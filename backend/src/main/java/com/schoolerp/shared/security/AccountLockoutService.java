package com.schoolerp.shared.security;

import com.schoolerp.settings.SettingsService;
import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.cache.TenantAwareRedisTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Tracks consecutive failed login attempts and temporary account lockouts per school tenant
 * in Redis with resilient fallback (F-08 BR-03, Q-001).
 */
@Service
public class AccountLockoutService {

    public static final String SETTING_MAX_ATTEMPTS = "security.lockout.max_attempts";
    public static final String SETTING_DURATION_MINUTES = "security.lockout.duration_minutes";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_DURATION_MINUTES = 15;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    public record AccountLockoutStatusDto(
            long userId,
            String identity,
            boolean locked,
            long failedAttempts,
            Instant lockedUntil) {
    }

    private final TenantAwareRedisTemplate redisTemplate;
    private final SettingsService settingsService;
    private final AuditService auditService;

    public AccountLockoutService(
            TenantAwareRedisTemplate redisTemplate,
            SettingsService settingsService,
            AuditService auditService) {
        this.redisTemplate = redisTemplate;
        this.settingsService = settingsService;
        this.auditService = auditService;
    }

    public Optional<Instant> checkLockedUntil(String rawIdentity) {
        String identity = normalize(rawIdentity);
        String lockKey = redisTemplate.tenantKey("lockout", "locked", identity);
        Optional<String> val = redisTemplate.getRaw(lockKey);
        if (val.isEmpty()) {
            return Optional.empty();
        }
        try {
            Instant until = Instant.parse(val.get());
            if (Instant.now().isBefore(until)) {
                return Optional.of(until);
            }
            redisTemplate.deleteRaw(lockKey);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean recordFailedAttempt(Long userId, String rawIdentity) {
        String identity = normalize(rawIdentity);
        int maxAttempts = Math.max(1, settingsService.getInt(SETTING_MAX_ATTEMPTS, DEFAULT_MAX_ATTEMPTS));
        int lockDurationMinutes = Math.max(
                1, settingsService.getInt(SETTING_DURATION_MINUTES, DEFAULT_DURATION_MINUTES));

        String attemptsKey = redisTemplate.tenantKey("lockout", "attempts", identity);
        long attempts = redisTemplate.incrementRaw(attemptsKey, ATTEMPT_WINDOW);

        if (attempts >= maxAttempts) {
            Duration lockDuration = Duration.ofMinutes(lockDurationMinutes);
            Instant lockedUntil = Instant.now().plus(lockDuration);
            String lockKey = redisTemplate.tenantKey("lockout", "locked", identity);
            redisTemplate.setRaw(lockKey, lockedUntil.toString(), lockDuration);

            auditService.recordAs(
                    userId,
                    "USER_ACCOUNT_LOCKED",
                    "User",
                    userId != null ? String.valueOf(userId) : identity,
                    null,
                    Map.of(
                            "identity", identity,
                            "failedAttempts", attempts,
                            "lockedUntil", lockedUntil.toString()));
            return true;
        }
        return false;
    }

    public void clearOnSuccess(String rawIdentity) {
        String identity = normalize(rawIdentity);
        redisTemplate.deleteTenantKey("lockout", "attempts", identity);
        redisTemplate.deleteTenantKey("lockout", "locked", identity);
    }

    public AccountLockoutStatusDto getStatus(long userId, String rawIdentity) {
        String identity = normalize(rawIdentity);
        Optional<Instant> lockedUntil = checkLockedUntil(identity);
        String attemptsKey = redisTemplate.tenantKey("lockout", "attempts", identity);
        long attempts = redisTemplate.getRaw(attemptsKey)
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .orElse(0L);
        return new AccountLockoutStatusDto(
                userId,
                identity,
                lockedUntil.isPresent(),
                attempts,
                lockedUntil.orElse(null));
    }

    public void adminUnlock(long userId, String rawIdentity) {
        String identity = normalize(rawIdentity);
        clearOnSuccess(identity);
        auditService.record(
                "USER_ACCOUNT_UNLOCKED",
                "User",
                String.valueOf(userId),
                null,
                Map.of("userId", userId, "identity", identity));
    }

    private String normalize(String rawIdentity) {
        return rawIdentity == null ? "" : rawIdentity.trim().toLowerCase();
    }
}

