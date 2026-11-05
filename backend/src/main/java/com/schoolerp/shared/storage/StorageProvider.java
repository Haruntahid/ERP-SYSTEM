package com.schoolerp.shared.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Pluggable object storage provider strategy (Local external filesystem or S3/MinIO/R2).
 * All implementations MUST enforce {@link ObjectKeyValidator#assertBelongsToCurrentTenant(String)}
 * on every operation.
 */
public interface StorageProvider {

    String providerName();

    void putObject(String objectKey, InputStream stream, long sizeBytes, String contentType);

    String generatePresignedGetUrl(
            String objectKey,
            String filename,
            String contentType,
            String disposition,
            Duration ttl);

    InputStream openStream(String objectKey);

    void deleteObject(String objectKey);
}

