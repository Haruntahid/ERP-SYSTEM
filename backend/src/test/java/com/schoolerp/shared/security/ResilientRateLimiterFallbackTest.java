package com.schoolerp.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schoolerp.shared.security.RateLimiterStrategy.RateLimitDecision;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

class ResilientRateLimiterFallbackTest {

    @Test
    void fallsBackToInMemoryRateLimiterWhenRedisThrowsConnectionFailure() {
        RateLimiterStrategy failingPrimary = (key, max) -> {
            throw new RedisConnectionFailureException("Simulated Redis outage");
        };
        InMemoryFallbackRateLimiter fallback = new InMemoryFallbackRateLimiter();
        ResilientRateLimiter resilient = new ResilientRateLimiter(failingPrimary, fallback);

        RateLimitDecision first = resilient.evaluate("alpha:ratelimit:auth_login:10.0.0.1", 2);
        assertTrue(first.allowed());
        assertEquals(1L, first.currentCount());

        RateLimitDecision second = resilient.evaluate("alpha:ratelimit:auth_login:10.0.0.1", 2);
        assertTrue(second.allowed());
        assertEquals(2L, second.currentCount());

        RateLimitDecision third = resilient.evaluate("alpha:ratelimit:auth_login:10.0.0.1", 2);
        assertFalse(third.allowed());
        assertTrue(third.retryAfterSeconds() >= 1L);
        assertEquals(3L, resilient.fallbackCount());
    }
}

