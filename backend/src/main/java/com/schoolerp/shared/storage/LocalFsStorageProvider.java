package com.schoolerp.shared.storage;

import com.schoolerp.shared.config.AppProperties;
import com.schoolerp.shared.tenancy.TenantContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * External local-filesystem storage provider for development and tests.
 * Never writes inside the application working directory or classpath; stores files under
 * {@code app.storage.local.root-dir} (default {@code /tmp/school-erp-storage}) and issues
 * HMAC-SHA256 signed, tenant-bound streaming URLs ({@code /api/v1/files/stream/{token}}).
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFsStorageProvider implements StorageProvider {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final Path rootDir;
    private final byte[] hmacSecret;

    public LocalFsStorageProvider(
            @Value("${app.storage.local.root-dir:/tmp/school-erp-storage}") String rootDirPath,
            AppProperties appProperties) {
        this.rootDir = Path.of(rootDirPath).toAbsolutePath().normalize();
        String secretStr = appProperties != null && appProperties.jwt() != null && appProperties.jwt().secret() != null
                ? appProperties.jwt().secret()
                : "default-local-storage-hmac-secret-key-32-bytes";
        this.hmacSecret = secretStr.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String providerName() {
        return "LOCAL";
    }

    @Override
    public void putObject(String objectKey, InputStream stream, long sizeBytes, String contentType) {
        ObjectKeyValidator.assertBelongsToCurrentTenant(objectKey);
        Path target = resolveSafePath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write object to local storage: " + objectKey, e);
        }
    }

    @Override
    public String generatePresignedGetUrl(
            String objectKey,
            String filename,
            String contentType,
            String disposition,
            Duration ttl) {
        ObjectKeyValidator.assertBelongsToCurrentTenant(objectKey);
        String tenantSlug = TenantContext.require();
        String safeDisposition = "inline".equalsIgnoreCase(disposition) ? "inline" : "attachment";
        long expiresAtEpochSec = Instant.now().plus(ttl != null ? ttl : Duration.ofSeconds(300)).getEpochSecond();

        String payload = String.join(
                "|",
                tenantSlug,
                objectKey,
                filename != null ? filename.replace("|", "_") : "download",
                contentType != null ? contentType.replace("|", "_") : "application/octet-stream",
                safeDisposition,
                String.valueOf(expiresAtEpochSec));

        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signatureB64 = sign(payloadB64);
        String token = payloadB64 + "." + signatureB64;
        return "/api/v1/files/stream/" + token;
    }

    @Override
    public InputStream openStream(String objectKey) {
        ObjectKeyValidator.assertBelongsToCurrentTenant(objectKey);
        Path target = resolveSafePath(objectKey);
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("Stored object does not exist: " + objectKey);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open stored object: " + objectKey, e);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        ObjectKeyValidator.assertBelongsToCurrentTenant(objectKey);
        Path target = resolveSafePath(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete stored object: " + objectKey, e);
        }
    }

    public StreamTokenClaims verifyAndParseStreamToken(String token) {
        if (token == null || !token.contains(".")) {
            throw new SecurityException("Malformed download stream token");
        }
        int dot = token.indexOf('.');
        String payloadB64 = token.substring(0, dot);
        String providedSig = token.substring(dot + 1);
        String expectedSig = sign(payloadB64);

        if (!MessageDigest.isEqual(
                providedSig.getBytes(StandardCharsets.UTF_8),
                expectedSig.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid HMAC signature on download stream token");
        }

        String decoded = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|", 6);
        if (parts.length != 6) {
            throw new SecurityException("Invalid download stream token payload");
        }

        String tokenTenantSlug = parts[0];
        String objectKey = parts[1];
        String filename = parts[2];
        String contentType = parts[3];
        String disposition = parts[4];
        long expiresAtEpochSec = Long.parseLong(parts[5]);

        if (Instant.now().getEpochSecond() > expiresAtEpochSec) {
            throw new SecurityException("Download URL has expired");
        }

        String activeTenant = TenantContext.require();
        if (!activeTenant.equals(tokenTenantSlug)) {
            throw new SecurityException(
                    "Cross-tenant download token replay rejected: token issued for "
                            + tokenTenantSlug + " but active tenant is " + activeTenant);
        }
        ObjectKeyValidator.assertBelongsToCurrentTenant(objectKey);

        return new StreamTokenClaims(tokenTenantSlug, objectKey, filename, contentType, disposition, expiresAtEpochSec);
    }

    private Path resolveSafePath(String objectKey) {
        Path resolved = rootDir.resolve(objectKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new SecurityException("Path traversal attempt outside storage root rejected");
        }
        return resolved;
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(hmacSecret, HMAC_ALGO));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC for stream token", e);
        }
    }

    public record StreamTokenClaims(
            String tenantSlug,
            String objectKey,
            String filename,
            String contentType,
            String disposition,
            long expiresAtEpochSec) {
    }
}

