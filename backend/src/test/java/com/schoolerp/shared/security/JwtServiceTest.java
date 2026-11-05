package com.schoolerp.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.schoolerp.shared.config.AppProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private static JwtService createService(long ttlSeconds) {
        AppProperties props = new AppProperties(
                null, null, null, null,
                new AppProperties.Jwt(SECRET, ttlSeconds, 604800),
                null, null, null, null);
        return new JwtService(props);
    }

    @Test
    void issuesAndValidatesTokenWithTenantClaim() {
        JwtService jwtService = createService(900);
        String token = jwtService.issueAccessToken(42L, "admin@greenwood.school", "greenwood");

        JwtService.JwtClaims claims = jwtService.parseAndValidate(token);
        assertEquals(42L, claims.userId());
        assertEquals("admin@greenwood.school", claims.email());
        assertEquals("greenwood", claims.tenantSlug());
        assertNotNull(claims.expiresAt());
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwtService = createService(900);
        String token = jwtService.issueAccessToken(42L, "admin@greenwood.school", "greenwood");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThrows(JwtException.class, () -> jwtService.parseAndValidate(tampered));
    }

    @Test
    void rejectsShortSecretKey() {
        String shortSecret = Base64.getEncoder().encodeToString("short-key".getBytes());
        AppProperties props = new AppProperties(
                null, null, null, null,
                new AppProperties.Jwt(shortSecret, 900, 604800),
                null, null, null, null);
        assertThrows(IllegalStateException.class, () -> new JwtService(props));
    }
}

