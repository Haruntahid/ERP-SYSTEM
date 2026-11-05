package com.schoolerp.shared.cache;

import com.schoolerp.shared.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tenant-aware Redis helper enforcing {@code <tenantSlug>:<namespace>:<key>} prefixes on all
 * tenant operations and providing resilient fallback when Redis is temporarily unreachable (F-08 BR-01, BR-06).
 */
@Component
public class TenantAwareRedisTemplate {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareRedisTemplate.class);

    private record FallbackEntry(String value, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return expiresAt != null && now.isAfter(expiresAt);
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final TenantPrefixedCacheKeyGenerator keyGenerator;
    private final ConcurrentMap<String, FallbackEntry> fallbackStore = new ConcurrentHashMap<>();
    private final AtomicLong fallbackEventCounter = new AtomicLong();

    @Autowired
    public TenantAwareRedisTemplate(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            TenantPrefixedCacheKeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
    }

    public String tenantKey(String namespace, Object... keyParts) {
        return keyGenerator.buildTenantKey(namespace, keyParts);
    }

    public String tenantKeyForSlug(String tenantSlug, String namespace, Object... keyParts) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalStateException("Tenant slug must not be blank");
        }
        String previous = TenantContext.get().orElse(null);
        try {
            TenantContext.set(tenantSlug);
            return keyGenerator.buildTenantKey(namespace, keyParts);
        } finally {
            if (previous != null) {
                TenantContext.set(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    public Optional<String> getTenantValue(String namespace, Object... keyParts) {
        String fullKey = tenantKey(namespace, keyParts);
        return getRaw(fullKey);
    }

    public void setTenantValue(String namespace, Duration ttl, String value, Object... keyParts) {
        String fullKey = tenantKey(namespace, keyParts);
        setRaw(fullKey, value, ttl);
    }

    public void deleteTenantKey(String namespace, Object... keyParts) {
        String fullKey = tenantKey(namespace, keyParts);
        deleteRaw(fullKey);
    }

    public Optional<String> getRaw(String fullKey) {
        validateKeyPrefix(fullKey);
        if (redisTemplate != null) {
            try {
                String val = redisTemplate.opsForValue().get(fullKey);
                if (val != null) {
                    return Optional.of(val);
                }
            } catch (Exception e) {
                recordFallback(e);
            }
        }
        FallbackEntry entry = fallbackStore.get(fullKey);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(Instant.now())) {
            fallbackStore.remove(fullKey, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void setRaw(String fullKey, String value, Duration ttl) {
        validateKeyPrefix(fullKey);
        Instant expiresAt = ttl != null && !ttl.isZero() && !ttl.isNegative()
                ? Instant.now().plus(ttl)
                : null;
        fallbackStore.put(fullKey, new FallbackEntry(value, expiresAt));
        if (redisTemplate != null) {
            try {
                if (expiresAt != null) {
                    redisTemplate.opsForValue().set(fullKey, value, ttl);
                } else {
                    redisTemplate.opsForValue().set(fullKey, value);
                }
            } catch (Exception e) {
                recordFallback(e);
            }
        }
    }

    public boolean setIfAbsentRaw(String fullKey, String value, Duration ttl) {
        validateKeyPrefix(fullKey);
        if (redisTemplate != null) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(fullKey, value, ttl);
                if (Boolean.TRUE.equals(acquired)) {
                    fallbackStore.put(fullKey, new FallbackEntry(value, Instant.now().plus(ttl)));
                    return true;
                }
                if (acquired != null) {
                    return false;
                }
            } catch (Exception e) {
                recordFallback(e);
            }
        }
        Instant now = Instant.now();
        FallbackEntry computed = fallbackStore.compute(fullKey, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new FallbackEntry(value, now.plus(ttl));
            }
            return existing;
        });
        return computed.value().equals(value);
    }

    public long incrementRaw(String fullKey, Duration ttl) {
        validateKeyPrefix(fullKey);
        if (redisTemplate != null) {
            try {
                Long val = redisTemplate.opsForValue().increment(fullKey);
                if (val != null) {
                    if (val == 1L && ttl != null) {
                        redisTemplate.expire(fullKey, ttl);
                    }
                    fallbackStore.put(
                            fullKey,
                            new FallbackEntry(
                                    String.valueOf(val),
                                    ttl != null ? Instant.now().plus(ttl) : null));
                    return val;
                }
            } catch (Exception e) {
                recordFallback(e);
            }
        }
        Instant now = Instant.now();
        FallbackEntry updated = fallbackStore.compute(fullKey, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new FallbackEntry("1", ttl != null ? now.plus(ttl) : null);
            }
            long next = Long.parseLong(existing.value()) + 1L;
            return new FallbackEntry(String.valueOf(next), existing.expiresAt());
        });
        return Long.parseLong(updated.value());
    }

    public void deleteRaw(String fullKey) {
        validateKeyPrefix(fullKey);
        fallbackStore.remove(fullKey);
        if (redisTemplate != null) {
            try {
                redisTemplate.unlink(fullKey);
            } catch (Exception e) {
                recordFallback(e);
            }
        }
    }

    /**
     * Evicts all keys matching {@code <tenantSlug>:<namespacePrefix>*} using non-blocking
     * {@code SCAN} and {@code UNLINK} (never {@code KEYS} or {@code FLUSHDB}).
     */
    public long evictTenantNamespace(String namespacePrefix) {
        String tenantSlug = TenantContext.require();
        return evictTenantNamespaceForSlug(tenantSlug, namespacePrefix);
    }

    public long evictTenantNamespaceForSlug(String tenantSlug, String namespacePrefix) {
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalStateException("Tenant slug must not be blank");
        }
        String prefix = namespacePrefix == null || namespacePrefix.isBlank() || "all".equalsIgnoreCase(namespacePrefix)
                ? tenantSlug + ":"
                : tenantSlug + ":" + namespacePrefix + ":";

        long removed = 0L;
        for (String key : List.copyOf(fallbackStore.keySet())) {
            if (key.startsWith(prefix)) {
                if (fallbackStore.remove(key) != null) {
                    removed++;
                }
            }
        }

        if (redisTemplate != null) {
            try {
                List<String> matchedKeys = new ArrayList<>();
                ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(200).build();
                redisTemplate.execute((RedisConnection connection) -> {
                    try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                        while (cursor.hasNext()) {
                            matchedKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                        }
                    }
                    return null;
                });
                if (!matchedKeys.isEmpty()) {
                    Long unlinked = redisTemplate.unlink(matchedKeys);
                    if (unlinked != null && unlinked > removed) {
                        removed = unlinked;
                    }
                }
            } catch (Exception e) {
                recordFallback(e);
            }
        }
        return removed;
    }

    public StringRedisTemplate rawRedisTemplate() {
        return redisTemplate;
    }

    public long fallbackCount() {
        return fallbackEventCounter.get();
    }

    private void validateKeyPrefix(String fullKey) {
        if (fullKey == null || fullKey.isBlank() || !fullKey.contains(":")) {
            throw new IllegalStateException("Redis key must carry an explicit tenant or platform prefix: " + fullKey);
        }
    }

    private void recordFallback(Exception e) {
        long count = fallbackEventCounter.incrementAndGet();
        if (count == 1 || count % 50 == 0) {
            log.warn("Redis unavailable or timed out (fallback count={}); using local resilient store: {}",
                    count, e.getMessage());
        }
    }
}

