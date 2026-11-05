package com.schoolerp.shared.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the tenant to the request. The tenant comes from the subdomain (school.example.com) or, for
 * development, from the X-Tenant-ID header. Once JWT authentication exists, the tenant claim of the
 * token must be the source of truth and must match this value.
 */
public class TenantFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";
    private static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9]{2,23}$");

    private final TenantRegistry registry;
    private final String baseDomain;

    public TenantFilter(TenantRegistry registry, String baseDomain) {
        this.registry = registry;
        this.baseDomain = baseDomain;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || path.startsWith("/api/v1/platform/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String slug = resolveSlug(request);
        if (slug == null) {
            reject(response, 400, "Tenant not specified");
            return;
        }
        Optional<TenantInfo> tenant = registry.find(slug);
        if (tenant.isEmpty()) {
            reject(response, 404, "Unknown tenant");
            return;
        }
        if (!tenant.get().active()) {
            reject(response, 403, "Tenant is not active");
            return;
        }
        TenantContext.set(slug);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveSlug(HttpServletRequest request) {
        String header = request.getHeader(TENANT_HEADER);
        if (header != null && SLUG.matcher(header).matches()) {
            return header;
        }
        if (baseDomain != null && !baseDomain.isBlank()) {
            String host = request.getServerName();
            String suffix = "." + baseDomain;
            if (host != null && host.endsWith(suffix)) {
                String sub = host.substring(0, host.length() - suffix.length());
                if (SLUG.matcher(sub).matches()) {
                    return sub;
                }
            }
        }
        return null;
    }

    private void reject(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"Tenant error\",\"status\":" + status
                        + ",\"detail\":\"" + detail + "\"}");
    }
}
