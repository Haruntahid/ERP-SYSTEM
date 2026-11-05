package com.schoolerp.shared.security;

import com.schoolerp.shared.cache.TenantAwareRedisTemplate;
import com.schoolerp.shared.config.AppProperties;
import com.schoolerp.shared.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Manages distributed JWT revocation via single-token {@code jti} blacklisting and
 * per-user revocation watermarks {@code <tenantSlug>:jwt:revoke-user:<userId>} (F-08 BR-04).
 */
@Service
public class JwtRevocationService {

    private final TenantAwareRedisTemplate redisTemplate;
    private final Duration accessTokenTtl;

    public JwtRevocationService(TenantAwareRedisTemplate redisTemplate, AppProperties props) {
        this.redisTemplate = redisTemplate;
        long ttlSeconds = props.jwt() != null && props.jwt().accessTokenTtlSeconds() > 0
                ? props.jwt().accessTokenTtlSeconds()
                : 900L;
        this.accessTokenTtl = Duration.ofSeconds(ttlSeconds);
    }

    public void blacklistJti(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        Duration remaining = expiresAt != null
                ? Duration.between(Instant.now(), expiresAt)
                : accessTokenTtl;
        if (remaining.isNegative() || remaining.isZero()) {
            remaining = Duration.ofSeconds(60);
        }
        String key = redisTemplate.tenantKey("jwt", "blacklist", "jti", jti);
        redisTemplate.setRaw(key, "1", remaining);
    }

    public void revokeAllForUser(long userId) {
        String tenantSlug = TenantContext.require();
        revokeAllForUserInTenant(tenantSlug, userId);
    }

    public void revokeAllForUserInTenant(String tenantSlug, long userId) {
        String key = redisTemplate.tenantKeyForSlug(tenantSlug, "jwt", "revoke-user", userId);
        redisTemplate.setRaw(key, String.valueOf(Instant.now().toEpochMilli()), accessTokenTtl);
    }

    public boolean isRevoked(JwtService.JwtClaims claims) {
        String tenantSlug = claims.tenantSlug();
        if (claims.jti() != null && !claims.jti().isBlank()) {
            String jtiKey = redisTemplate.tenantKeyForSlug(
                    tenantSlug, "jwt", "blacklist", "jti", claims.jti());
            if (redisTemplate.getRaw(jtiKey).isPresent()) {
                return true;
            }
        }

        String userWatermarkKey = redisTemplate.tenantKeyForSlug(
                tenantSlug, "jwt", "revoke-user", claims.userId());
        Optional<String> watermarkOpt = redisTemplate.getRaw(userWatermarkKey);
        if (watermarkOpt.isPresent()) {
            try {
                long revokedAtEpochMillis = Long.parseLong(watermarkOpt.get());
                long issuedAtMillis = claims.issuedAt() != null
                        ? claims.issuedAt().toEpochMilli()
                        : 0L;
                if (issuedAtMillis <= revokedAtEpochMillis) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        return false;
    }
}

