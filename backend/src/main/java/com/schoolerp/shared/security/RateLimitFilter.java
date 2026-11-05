package com.schoolerp.shared.security;

import com.schoolerp.settings.SettingsService;
import com.schoolerp.shared.config.AppProperties;
import com.schoolerp.shared.security.RateLimiterStrategy.RateLimitDecision;
import com.schoolerp.shared.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Distributed rate-limiting filter enforcing tiered policies across tenant endpoints using
 * {@code <tenantSlug>:ratelimit:<policy>:<clientKey>} in Redis with resilient in-memory fallback (F-08 BR-02).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterStrategy rateLimiter;
    private final AppProperties.RateLimit rateLimitProps;
    private final SettingsService settingsService;
    private final boolean authEndpointsOnly;

    public RateLimitFilter(int maxRequestsPerMinute) {
        this.rateLimiter = new InMemoryFallbackRateLimiter();
        this.rateLimitProps = new AppProperties.RateLimit(maxRequestsPerMinute, 30, 20, 300);
        this.settingsService = null;
        this.authEndpointsOnly = true;
    }

    public RateLimitFilter(
            RateLimiterStrategy rateLimiter,
            AppProperties.RateLimit rateLimitProps,
            SettingsService settingsService) {
        this.rateLimiter = rateLimiter != null ? rateLimiter : new InMemoryFallbackRateLimiter();
        this.rateLimitProps = rateLimitProps != null
                ? rateLimitProps
                : new AppProperties.RateLimit(10, 30, 20, 300);
        this.settingsService = settingsService;
        this.authEndpointsOnly = false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (authEndpointsOnly) {
            return !("/api/v1/auth/login".equals(path) || "/api/v1/auth/refresh".equals(path));
        }
        return !path.startsWith("/api/v1/")
                || path.startsWith("/api/v1/platform/")
                || "/api/v1/tenant/config".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitPolicy policy = resolvePolicy(request.getRequestURI());
        String tenant = TenantContext.get().orElse("platform");
        String clientKey = clientIp(request);
        String bucketKey = tenant + ":ratelimit:" + policy.code() + ":" + clientKey;
        int limit = resolveLimitPerMinute(policy);

        RateLimitDecision decision = rateLimiter.evaluate(bucketKey, limit);
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                            + "\"detail\":\"Rate limit exceeded. Please try again later.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private RateLimitPolicy resolvePolicy(String path) {
        if ("/api/v1/auth/login".equals(path) || "/api/v1/auth/refresh".equals(path)) {
            return RateLimitPolicy.AUTH_LOGIN;
        }
        if (path.startsWith("/api/v1/public/verify")) {
            return RateLimitPolicy.PUBLIC_VERIFY;
        }
        if ("/api/v1/auth/change-password".equals(path)
                || path.startsWith("/api/v1/auth/forgot-password")
                || path.startsWith("/api/v1/auth/reset-password")
                || path.startsWith("/api/v1/files")) {
            return RateLimitPolicy.SENSITIVE_MUTATION;
        }
        return RateLimitPolicy.TENANT_API_GENERAL;
    }

    private int resolveLimitPerMinute(RateLimitPolicy policy) {
        return switch (policy) {
            case AUTH_LOGIN -> settingsService != null
                    ? settingsService.getInt("ratelimit.login_per_minute", rateLimitProps.authRequestsPerMinute())
                    : rateLimitProps.authRequestsPerMinute();
            case PUBLIC_VERIFY -> rateLimitProps.publicVerifyRequestsPerMinute();
            case SENSITIVE_MUTATION -> rateLimitProps.sensitiveMutationRequestsPerMinute();
            case TENANT_API_GENERAL -> settingsService != null
                    ? settingsService.getInt("ratelimit.api_per_minute", rateLimitProps.generalApiRequestsPerMinute())
                    : rateLimitProps.generalApiRequestsPerMinute();
        };
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
