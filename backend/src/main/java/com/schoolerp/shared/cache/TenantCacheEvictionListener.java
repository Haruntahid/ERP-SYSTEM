package com.schoolerp.shared.cache;

import com.schoolerp.shared.security.JwtRevocationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts tenant-scoped Redis cache keys after database transactions commit (F-08 BR-05).
 */
@Component
public class TenantCacheEvictionListener {

    private final TenantAwareRedisTemplate redisTemplate;
    private final JwtRevocationService jwtRevocationService;

    public TenantCacheEvictionListener(
            TenantAwareRedisTemplate redisTemplate,
            JwtRevocationService jwtRevocationService) {
        this.redisTemplate = redisTemplate;
        this.jwtRevocationService = jwtRevocationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSettingsUpdated(SettingsUpdatedInternalEvent event) {
        if (event.tenantSlug() != null && !event.tenantSlug().isBlank()) {
            String key = redisTemplate.tenantKeyForSlug(event.tenantSlug(), "settings", "all");
            redisTemplate.deleteRaw(key);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserPermissionsChanged(UserPermissionsChangedInternalEvent event) {
        if (event.tenantSlug() != null && !event.tenantSlug().isBlank()) {
            String key = redisTemplate.tenantKeyForSlug(
                    event.tenantSlug(), "userPermissions", event.userId());
            redisTemplate.deleteRaw(key);
            if (event.revokeSessions()) {
                jwtRevocationService.revokeAllForUserInTenant(event.tenantSlug(), event.userId());
            }
        }
    }
}

