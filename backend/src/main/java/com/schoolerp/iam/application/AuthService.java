package com.schoolerp.iam.application;

import com.schoolerp.iam.domain.RefreshToken;
import com.schoolerp.iam.domain.User;
import com.schoolerp.iam.infrastructure.RefreshTokenRepository;
import com.schoolerp.iam.infrastructure.UserRepository;
import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.cache.TenantAwareRedisTemplate;
import com.schoolerp.shared.cache.UserPermissionsChangedInternalEvent;
import com.schoolerp.shared.config.AppProperties;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.security.AccountLockoutService;
import com.schoolerp.shared.security.JwtRevocationService;
import com.schoolerp.shared.security.JwtService;
import com.schoolerp.shared.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtRevocationService jwtRevocationService;
    private final AccountLockoutService accountLockoutService;
    private final TenantAwareRedisTemplate redisTemplate;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final long refreshTokenTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtRevocationService jwtRevocationService,
            AccountLockoutService accountLockoutService,
            TenantAwareRedisTemplate redisTemplate,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            AppProperties props) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtRevocationService = jwtRevocationService;
        this.accountLockoutService = accountLockoutService;
        this.redisTemplate = redisTemplate;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.refreshTokenTtlSeconds = props.jwt().refreshTokenTtlSeconds();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair login(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();

        Optional<Instant> lockedUntilOpt = accountLockoutService.checkLockedUntil(normalizedEmail);
        if (lockedUntilOpt.isPresent()) {
            throw new ApiException(
                    HttpStatus.LOCKED,
                    "Account is temporarily locked due to repeated failed login attempts until "
                            + lockedUntilOpt.get());
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).orElse(null);
        if (user == null || !user.isActive()
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            Long userId = user != null ? user.getId() : null;
            auditService.recordAs(
                    userId,
                    "AUTH_LOGIN_FAILED",
                    "User",
                    normalizedEmail,
                    null,
                    Map.of("email", normalizedEmail));
            boolean justLocked = accountLockoutService.recordFailedAttempt(userId, normalizedEmail);
            if (justLocked) {
                throw new ApiException(
                        HttpStatus.LOCKED,
                        "Account has been temporarily locked due to too many failed login attempts");
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        accountLockoutService.clearOnSuccess(normalizedEmail);

        String tenantSlug = TenantContext.require();
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), tenantSlug);
        String rawRefresh = generateRawRefreshToken();
        String refreshHash = sha256Hex(rawRefresh);
        refreshTokenRepository.save(new RefreshToken(
                user.getId(), refreshHash, Instant.now().plusSeconds(refreshTokenTtlSeconds)));

        auditService.recordAs(
                user.getId(),
                "AUTH_LOGIN_SUCCESS",
                "User",
                String.valueOf(user.getId()),
                null,
                Map.of("email", user.getEmail()));

        return new TokenPair(accessToken, rawRefresh, jwtService.accessTokenTtlSeconds());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair refresh(String rawRefreshToken) {
        String presentedHash = sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        Instant now = Instant.now();
        if (existing.isRevoked()) {
            // Token reuse detected: revoke all active tokens for this user
            refreshTokenRepository.revokeAllActiveByUserId(existing.getUserId(), now);
            jwtRevocationService.revokeAllForUser(existing.getUserId());
            auditService.recordAs(
                    existing.getUserId(),
                    "AUTH_REFRESH_REUSE_DETECTED",
                    "User",
                    String.valueOf(existing.getUserId()),
                    null,
                    null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has already been used or revoked");
        }

        if (existing.isExpired(now)) {
            existing.revoke(null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(existing.getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            existing.revoke(null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User account is inactive");
        }

        String newRawRefresh = generateRawRefreshToken();
        String newRefreshHash = sha256Hex(newRawRefresh);
        existing.revoke(newRefreshHash);
        refreshTokenRepository.save(new RefreshToken(
                user.getId(), newRefreshHash, now.plusSeconds(refreshTokenTtlSeconds)));

        String tenantSlug = TenantContext.require();
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), tenantSlug);
        return new TokenPair(accessToken, newRawRefresh, jwtService.accessTokenTtlSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken, String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String rawJwt = authorizationHeader.substring(7).trim();
            try {
                JwtService.JwtClaims claims = jwtService.parseAndValidate(rawJwt);
                jwtRevocationService.blacklistJti(claims.jti(), claims.expiresAt());
            } catch (JwtException ignored) {
                // Ignore already invalid access token during logout
            }
        }
        String hash = sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            if (!rt.isRevoked()) {
                rt.revoke(null);
                auditService.recordAs(
                        rt.getUserId(),
                        "AUTH_LOGOUT",
                        "User",
                        String.valueOf(rt.getUserId()),
                        null,
                        null);
            }
        });
    }

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
        jwtRevocationService.revokeAllForUser(userId);
        auditService.recordAs(
                userId,
                "AUTH_PASSWORD_CHANGED",
                "User",
                String.valueOf(userId),
                null,
                null);
    }

    @Transactional
    public void revokeAllUserSessions(long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
        String tenantSlug = TenantContext.require();
        jwtRevocationService.revokeAllForUserInTenant(tenantSlug, user.getId());
        redisTemplate.deleteTenantKey("userPermissions", user.getId());
        eventPublisher.publishEvent(new UserPermissionsChangedInternalEvent(tenantSlug, user.getId(), true));
        auditService.record(
                "USER_SESSIONS_REVOKED",
                "User",
                String.valueOf(user.getId()),
                null,
                Map.of("userId", user.getId(), "email", user.getEmail()));
    }

    private String generateRawRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
