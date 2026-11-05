package com.schoolerp.shared.security;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Composite rate limiter that delegates to {@link RedisLuaRateLimiter} and degrades gracefully to
 * {@link InMemoryFallbackRateLimiter} if Redis is unreachable (F-08 BR-06).
 */
@Component
public class ResilientRateLimiter implements RateLimiterStrategy {

    private static final Logger log = LoggerFactory.getLogger(ResilientRateLimiter.class);

    private final RateLimiterStrategy primary;
    private final RateLimiterStrategy fallback;
    private final AtomicLong fallbackCounter = new AtomicLong();

    @org.springframework.beans.factory.annotation.Autowired
    public ResilientRateLimiter(RedisLuaRateLimiter primary, InMemoryFallbackRateLimiter fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    public ResilientRateLimiter(RateLimiterStrategy primary, RateLimiterStrategy fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public RateLimitDecision evaluate(String bucketKey, int maxRequestsPerMinute) {
        if (primary != null) {
            try {
                return primary.evaluate(bucketKey, maxRequestsPerMinute);
            } catch (Exception e) {
                long count = fallbackCounter.incrementAndGet();
                if (count == 1 || count % 50 == 0) {
                    log.warn("redis.fallback.active: Rate limiter falling back to local memory (count={}): {}",
                            count, e.getMessage());
                }
            }
        }
        return fallback.evaluate(bucketKey, maxRequestsPerMinute);
    }

    public long fallbackCount() {
        return fallbackCounter.get();
    }
}

