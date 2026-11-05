package com.schoolerp.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.schoolerp.shared.config.AppProperties;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless HMAC-SHA256 JWT access tokens carrying the user ID, email, tenant slug,
 * unique token ID (jti), and issued-at timestamp (F-08 BR-04).
 */
@Service
public class JwtService {

    public record JwtClaims(
            long userId,
            String email,
            String tenantSlug,
            String jti,
            Instant issuedAt,
            Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long accessTokenTtlSeconds;

    public JwtService(AppProperties props) {
        byte[] raw = Base64.getDecoder().decode(props.jwt().secret());
        if (raw.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be a base64 encoded key of at least 32 bytes");
        }
        SecretKey secretKey = new SecretKeySpec(raw, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        this.decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.accessTokenTtlSeconds = props.jwt().accessTokenTtlSeconds();
    }

    public String issueAccessToken(long userId, String email, String tenantSlug) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", email)
                .claim("tenant", tenantSlug)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public JwtClaims parseAndValidate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            long userId = Long.parseLong(jwt.getSubject());
            String email = jwt.getClaimAsString("email");
            String tenant = jwt.getClaimAsString("tenant");
            if (email == null || tenant == null || tenant.isBlank()) {
                throw new JwtException("Missing required claims");
            }
            Instant issuedAt = jwt.getIssuedAt() != null ? jwt.getIssuedAt() : Instant.EPOCH;
            return new JwtClaims(userId, email, tenant, jwt.getId(), issuedAt, jwt.getExpiresAt());
        } catch (NumberFormatException e) {
            throw new JwtException("Invalid subject claim", e);
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}
