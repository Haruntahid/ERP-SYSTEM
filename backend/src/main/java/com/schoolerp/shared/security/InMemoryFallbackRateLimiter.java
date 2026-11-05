package com.schoolerp.shared.security;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Local JVM sliding-window rate limiter used when Redis is unreachable or in standalone unit tests (F-08 BR-06).
 */
@Component
public class InMemoryFallbackRateLimiter implements RateLimiterStrategy {

    private static final long WINDOW_SECONDS = 60L;

    private record Window(long windowStartEpochSecond, AtomicInteger count) {
    }

    private final ConcurrentMap<String, Window> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitDecision evaluate(String bucketKey, int maxRequestsPerMinute) {
        int limit = Math.max(1, maxRequestsPerMinute);
        long nowEpochSecond = Instant.now().getEpochSecond();
        long nowWindow = nowEpochSecond / WINDOW_SECONDS;

        Window window = buckets.compute(bucketKey, (k, existing) -> {
            if (existing == null || existing.windowStartEpochSecond() != nowWindow) {
                return new Window(nowWindow, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        int count = window.count().get();
        long elapsedInWindow = nowEpochSecond % WINDOW_SECONDS;
        long retryAfter = Math.max(1L, WINDOW_SECONDS - elapsedInWindow);
        return new RateLimitDecision(count <= limit, count, retryAfter);
    }
}

