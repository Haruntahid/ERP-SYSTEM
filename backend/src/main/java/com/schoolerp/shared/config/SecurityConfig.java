package com.schoolerp.shared.config;

import com.schoolerp.settings.SettingsService;
import com.schoolerp.shared.security.JwtAuthenticationFilter;
import com.schoolerp.shared.security.JwtRevocationService;
import com.schoolerp.shared.security.JwtService;
import com.schoolerp.shared.security.PrincipalLoader;
import com.schoolerp.shared.security.RateLimitFilter;
import com.schoolerp.shared.security.ResilientRateLimiter;
import com.schoolerp.shared.security.SecurityProblemSupport;
import com.schoolerp.shared.tenancy.PlatformKeyFilter;
import com.schoolerp.shared.tenancy.TenantFilter;
import com.schoolerp.shared.tenancy.TenantRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Multi-tenant stateless security configuration:
 * 1. PlatformKeyFilter protects /api/v1/platform/** with X-Platform-Key.
 * 2. TenantFilter resolves the school from X-Tenant-ID or subdomain and binds TenantContext.
 * 3. RateLimitFilter rate-limits tenant endpoints using Redis Lua with resilient fallback.
 * 4. JwtAuthenticationFilter verifies the Bearer JWT, checks jwt.tenant == TenantContext,
 *    verifies JWT revocation blacklist, and loads cached permissions.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TenantRegistry registry,
            AppProperties props,
            JwtService jwtService,
            JwtRevocationService jwtRevocationService,
            PrincipalLoader principalLoader,
            ResilientRateLimiter rateLimiter,
            SettingsService settingsService,
            SecurityProblemSupport problemSupport) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemSupport)
                        .accessDeniedHandler(problemSupport))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/api/v1/platform/**",
                                "/api/v1/tenant/config",
                                "/api/v1/public/**",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/files/stream/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(
                        new PlatformKeyFilter(props.platform().apiKey()),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        new TenantFilter(registry, props.tenancy().baseDomain()),
                        PlatformKeyFilter.class)
                .addFilterAfter(
                        new RateLimitFilter(rateLimiter, props.rateLimit(), settingsService),
                        TenantFilter.class)
                .addFilterAfter(
                        new JwtAuthenticationFilter(jwtService, principalLoader, jwtRevocationService),
                        RateLimitFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
