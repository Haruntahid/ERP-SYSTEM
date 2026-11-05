package com.schoolerp.shared.cache;

import com.schoolerp.shared.tenancy.TenantContext;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Lightweight tenant-scoped distributed mutual exclusion lock backed by Redis {@code SET NX EX}
 * with atomic Lua compare-and-delete release (used by F-05 OutboxPublisher and F-09 Tenant Scheduler).
 */
@Component
public class TenantDistributedLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final TenantAwareRedisTemplate redisTemplate;

    public TenantDistributedLock(TenantAwareRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryLock(String tenantSlug, String lockName, String ownerToken, Duration ttl) {
        String key = buildLockKey(tenantSlug, lockName);
        return redisTemplate.setIfAbsentRaw(key, ownerToken, ttl);
    }

    public void unlock(String tenantSlug, String lockName, String ownerToken) {
        String key = buildLockKey(tenantSlug, lockName);
        StringRedisTemplate raw = redisTemplate.rawRedisTemplate();
        if (raw != null) {
            try {
                raw.execute(UNLOCK_SCRIPT, List.of(key), ownerToken);
                redisTemplate.deleteRaw(key);
                return;
            } catch (Exception ignored) {
                // Fall through to local fallback check
            }
        }
        redisTemplate.getRaw(key).ifPresent(current -> {
            if (ownerToken.equals(current)) {
                redisTemplate.deleteRaw(key);
            }
        });
    }

    public boolean executeIfLocked(String lockName, Duration ttl, Runnable task) {
        String tenantSlug = TenantContext.require();
        return executeIfLocked(tenantSlug, lockName, ttl, task);
    }

    public boolean executeIfLocked(String tenantSlug, String lockName, Duration ttl, Runnable task) {
        String token = UUID.randomUUID().toString();
        if (!tryLock(tenantSlug, lockName, token, ttl)) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            unlock(tenantSlug, lockName, token);
        }
    }

    private String buildLockKey(String tenantSlug, String lockName) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalStateException("Tenant slug is required for TenantDistributedLock");
        }
        if (lockName == null || lockName.isBlank()) {
            throw new IllegalArgumentException("Lock name must not be blank");
        }
        return "lock:" + tenantSlug + ":" + lockName;
    }
}

