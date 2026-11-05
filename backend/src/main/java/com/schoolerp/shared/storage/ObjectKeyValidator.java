package com.schoolerp.shared.storage;

import com.schoolerp.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

/**
 * Enforces strict tenant-prefixed object keys ({@code <tenantSlug>/<category>/<yyyy>/<mm>/<uuid>-<sanitizedFilename>})
 * and prevents path traversal or cross-tenant key access.
 */
public final class ObjectKeyValidator {

    private static final Pattern OBJECT_KEY_PATTERN = Pattern.compile(
            "^[a-z0-9_-]+/[A-Z_]+/\\d{4}/\\d{2}/[0-9a-f-]{36}-[a-zA-Z0-9._-]+$");

    private ObjectKeyValidator() {
    }

    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file.bin";
        }
        if (originalFilename.indexOf('\0') >= 0) {
            throw new SecurityException("Filename contains illegal null byte");
        }
        String stripped = originalFilename
                .replace('\\', '/')
                .substring(originalFilename.replace('\\', '/').lastIndexOf('/') + 1);
        if (stripped.contains("..")) {
            stripped = stripped.replace("..", ".");
        }
        String cleaned = stripped.replaceAll("[^a-zA-Z0-9._-]", "_");
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isBlank()) {
            cleaned = "file.bin";
        }
        if (cleaned.length() > 120) {
            int dotIdx = cleaned.lastIndexOf('.');
            if (dotIdx > 0 && cleaned.length() - dotIdx <= 10) {
                String ext = cleaned.substring(dotIdx);
                cleaned = cleaned.substring(0, 120 - ext.length()) + ext;
            } else {
                cleaned = cleaned.substring(0, 120);
            }
        }
        return cleaned;
    }

    public static String buildObjectKey(
            FileCategory category,
            String fileUuid,
            String sanitizedFilename,
            LocalDate dateUtc) {
        String tenantSlug = TenantContext.require();
        LocalDate d = dateUtc != null ? dateUtc : LocalDate.now(ZoneOffset.UTC);
        String key = "%s/%s/%04d/%02d/%s-%s".formatted(
                tenantSlug,
                category.name(),
                d.getYear(),
                d.getMonthValue(),
                fileUuid,
                sanitizedFilename);
        assertBelongsToCurrentTenant(key);
        return key;
    }

    public static void assertBelongsToCurrentTenant(String objectKey) {
        String tenantSlug = TenantContext.require();
        assertBelongsToTenant(objectKey, tenantSlug);
    }

    public static void assertBelongsToTenant(String objectKey, String expectedTenantSlug) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new SecurityException("Object key must not be blank");
        }
        if (expectedTenantSlug == null || expectedTenantSlug.isBlank()) {
            throw new SecurityException("Expected tenant slug must not be blank");
        }
        if (objectKey.indexOf('\0') >= 0 || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new SecurityException("Object key contains illegal path traversal sequence");
        }
        String requiredPrefix = expectedTenantSlug + "/";
        if (!objectKey.startsWith(requiredPrefix)) {
            throw new SecurityException(
                    "Cross-tenant object key access denied: key does not belong to tenant " + expectedTenantSlug);
        }
        if (!OBJECT_KEY_PATTERN.matcher(objectKey).matches()) {
            throw new SecurityException("Malformed object key format: " + objectKey);
        }
    }
}

