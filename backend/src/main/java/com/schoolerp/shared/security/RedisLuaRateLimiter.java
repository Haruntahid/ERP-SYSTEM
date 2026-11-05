package com.schoolerp.shared.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Atomic Redis Lua fixed/sliding-window rate limiter executing INCR + EXPIRE + TTL in a single round-trip (F-08 BR-02).
 */
@Component
public class RedisLuaRateLimiter implements RateLimiterStrategy {

    private static final long WINDOW_SECONDS = 60L;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
                "local current = redis.call('INCR', KEYS[1])\n"
                        + "if current == 1 then\n"
                        + "    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n"
                        + "end\n"
                        + "local ttl = redis.call('TTL', KEYS[1])\n"
                        + "if ttl < 1 then ttl = tonumber(ARGV[1]) end\n"
                        + "return {current, ttl}");
        RATE_LIMIT_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public RedisLuaRateLimiter(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitDecision evaluate(String bucketKey, int maxRequestsPerMinute) {
        if (redisTemplate == null) {
            throw new IllegalStateException("StringRedisTemplate is not configured");
        }
        int limit = Math.max(1, maxRequestsPerMinute);
        List<?> result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT, List.of(bucketKey), String.valueOf(WINDOW_SECONDS));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Unexpected empty response from Redis rate limit script");
        }
        long count = ((Number) result.get(0)).longValue();
        long ttl = Math.max(1L, ((Number) result.get(1)).longValue());
        return new RateLimitDecision(count <= limit, count, ttl);
    }
}

