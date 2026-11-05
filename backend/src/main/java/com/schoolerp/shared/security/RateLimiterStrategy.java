package com.schoolerp.shared.security;

public interface RateLimiterStrategy {

    record RateLimitDecision(boolean allowed, long currentCount, long retryAfterSeconds) {
    }

    RateLimitDecision evaluate(String bucketKey, int maxRequestsPerMinute);
}

