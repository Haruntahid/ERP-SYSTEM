package com.schoolerp.shared.storage;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * S3-compatible object storage provider (AWS S3, MinIO, Cloudflare R2) using AWS Signature V4.
 * Enforces {@link ObjectKeyValidator#assertBelongsToCurrentTenant(String)} on every call.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3StorageProvider implements StorageProvider {

    private static final DateTimeFormatter AMZ_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final String endpoint;
    private final String bucket;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final HttpClient httpClient;

    public S3StorageProvider(
            @Value("${app.storage.s3.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.storage.s3.bucket:school-erp-files}") String bucket,
            @Value("${app.storage.s3.region:ap-southeast-1}") String region,
            @Value("${app.storage.s3.access-key:minioadmin}") String accessKey,
            @Value("${app.storage.s3.secret-key:minioadmin}") String secretKey) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.bucket = bucket;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String providerName() {
        return "S3";
    }

    @Override
    public void putObject(String objectKey, InputStream stream, long sizeBytes, String contentType) {
        ObjectKeyValidator.assertBelongsToCurrentTenant(objectKey);
        try {
            byte[] bytes = stream.readAllBytes();
            URI uri = URI.create(endpoint + "/" + bucket + "/" + objectKey);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", contentType != null ? contentType : "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("S3 PUT failed with status " + response.statusCode());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload object to S3: " + objectKey, e);
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
        long expiresSeconds = Math.min(604800L, Math.max(1L, ttl != null ? ttl.toSeconds() : 300L));
        Instant now = Instant.now();
        String amzDate = AMZ_DATE_FORMAT.format(now);
        String dateStamp = DATE_STAMP_FORMAT.format(now);
        String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
        String credential = urlEncode(accessKey + "/" + credentialScope);
        String safeDisposition = ("inline".equalsIgnoreCase(disposition) ? "inline" : "attachment")
                + "; filename=\"" + (filename != null ? filename.replace("\"", "") : "file") + "\"";

        String query = "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=" + credential
                + "&X-Amz-Date=" + amzDate
                + "&X-Amz-Expires=" + expiresSeconds
                + "&X-Amz-SignedHeaders=host"
                + "&response-content-disposition=" + urlEncode(safeDisposition);

        URI baseUri = URI.create(endpoint);
        String hostHeader = baseUri.getPort() > 0
                ? baseUri.getHost() + ":" + baseUri.getPort()
                : baseUri.getHost();
        String canonicalUri = "/" + bucket + "/" + objectKey;
        String canonicalRequest = String.join(
                "\n",
                "GET",
                canonicalUri,
                query,
                "host:" + hostHeader + "\n",
                "host",
                "UNSIGNED-PAYLOAD");

        String stringToSign = String.join(
                "\n",
                "AWS4-HMAC-SHA256",
                amzDate,
                credentialScope,
                sha256Hex(canonicalRequest));

        byte[] signingKey = deriveSigningKey(secretKey, dateStamp, region, "s3");
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));

        return endpoint + canonicalUri + "?" + query + "&X-Amz-Signature=" + signature;
    }

    @Override
    public InputStream openStream(String objectKey) {
        ObjectKeyValidator.assertBelongsToCurrentTenant(objectKey);
        try {
            URI uri = URI.create(endpoint + "/" + bucket + "/" + objectKey);
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("S3 GET failed with status " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open S3 stream for " + objectKey, e);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        ObjectKeyValidator.assertBelongsToCurrentTenant(objectKey);
        try {
            URI uri = URI.create(endpoint + "/" + bucket + "/" + objectKey);
            HttpRequest request = HttpRequest.newBuilder(uri).DELETE().build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete S3 object " + objectKey, e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] deriveSigningKey(String secret, String dateStamp, String region, String service) {
        byte[] kSecret = ("AWS4" + secret).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }
}

