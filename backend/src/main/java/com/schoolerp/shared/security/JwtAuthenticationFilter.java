package com.schoolerp.shared.security;

import com.schoolerp.shared.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates tenant requests using a Bearer JWT token, enforces that the token's tenant claim
 * matches the tenant resolved by TenantFilter (rule 01-tenancy.md #7), and verifies that the token
 * has not been revoked in the distributed blacklist (F-08 BR-04).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PrincipalLoader principalLoader;
    private final JwtRevocationService jwtRevocationService;

    public JwtAuthenticationFilter(JwtService jwtService, PrincipalLoader principalLoader) {
        this(jwtService, principalLoader, null);
    }

    public JwtAuthenticationFilter(
            JwtService jwtService,
            PrincipalLoader principalLoader,
            JwtRevocationService jwtRevocationService) {
        this.jwtService = jwtService;
        this.principalLoader = principalLoader;
        this.jwtRevocationService = jwtRevocationService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/")
                || path.startsWith("/api/v1/platform/")
                || path.startsWith("/api/v1/public/")
                || path.equals("/api/v1/tenant/config")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        JwtService.JwtClaims claims;
        try {
            claims = jwtService.parseAndValidate(token);
        } catch (JwtException e) {
            writeProblem(response, 401, "Unauthorized", "Invalid or expired access token");
            return;
        }

        String boundTenant = TenantContext.get().orElse(null);
        if (boundTenant == null || !boundTenant.equals(claims.tenantSlug())) {
            writeProblem(response, 403, "Forbidden", "Token tenant claim does not match request tenant");
            return;
        }

        if (jwtRevocationService != null && jwtRevocationService.isRevoked(claims)) {
            writeProblem(response, 401, "Unauthorized", "Access token has been revoked");
            return;
        }

        Optional<SchoolUserPrincipal> principalOpt =
                principalLoader.loadPrincipal(claims.userId(), boundTenant);
        if (principalOpt.isEmpty()) {
            writeProblem(response, 401, "Unauthorized", "User account is inactive or does not exist");
            return;
        }

        SchoolUserPrincipal principal = principalOpt.get();
        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeProblem(HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                        + ",\"detail\":\"" + detail + "\"}");
    }
}
