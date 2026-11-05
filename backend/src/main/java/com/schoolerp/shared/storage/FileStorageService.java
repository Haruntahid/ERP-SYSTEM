package com.schoolerp.shared.storage;

import com.schoolerp.settings.SettingsService;
import com.schoolerp.shared.audit.AuditService;
import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.messaging.EventPriority;
import com.schoolerp.shared.messaging.OutboxEventWriter;
import com.schoolerp.shared.security.SchoolUserPrincipal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Application service for tenant-isolated file upload, magic-byte & ClamAV verification,
 * pre-signed download URL generation, quota enforcement, and soft-deletion.
 */
@Service
public class FileStorageService {

    private static final Set<String> INLINE_SAFE_MIMES = Set.of(
            FileMagicByteValidator.MIME_JPEG,
            FileMagicByteValidator.MIME_PNG,
            FileMagicByteValidator.MIME_WEBP,
            FileMagicByteValidator.MIME_PDF);

    private static final Set<FileCategory> AUDITED_UPLOAD_CATEGORIES = Set.of(
            FileCategory.SCHOOL_LOGO,
            FileCategory.STUDENT_DOC,
            FileCategory.STAFF_DOC,
            FileCategory.BULK_IMPORT);

    private final StoredFileRepository storedFileRepository;
    private final StorageProvider storageProvider;
    private final FileMagicByteValidator magicByteValidator;
    private final MalwareScanner malwareScanner;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final OutboxEventWriter outboxEventWriter;

    public FileStorageService(
            StoredFileRepository storedFileRepository,
            StorageProvider storageProvider,
            FileMagicByteValidator magicByteValidator,
            MalwareScanner malwareScanner,
            SettingsService settingsService,
            AuditService auditService,
            OutboxEventWriter outboxEventWriter) {
        this.storedFileRepository = storedFileRepository;
        this.storageProvider = storageProvider;
        this.magicByteValidator = magicByteValidator;
        this.malwareScanner = malwareScanner;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public StoredFileDto uploadFile(
            MultipartFile multipartFile,
            FileCategory category,
            String entityType,
            String entityId) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Uploaded file must not be empty");
        }
        byte[] bytes;
        try {
            bytes = multipartFile.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to read uploaded file stream");
        }
        return storeValidatedBytes(
                bytes,
                multipartFile.getOriginalFilename(),
                category,
                entityType,
                entityId);
    }

    @Transactional
    public StoredFileDto storeValidatedBytes(
            byte[] bytes,
            String originalFilename,
            FileCategory category,
            String entityType,
            String entityId) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "File content must not be empty");
        }
        if (category == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File category is required");
        }

        long maxSizeBytes = resolveMaxFileSizeBytes(category);
        if (bytes.length > maxSizeBytes) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "File size (" + bytes.length + " bytes) exceeds maximum allowed limit (" + maxSizeBytes + " bytes)");
        }

        // Enforce school storage quota for non-system-generated files
        long usedBytes = storedFileRepository.sumActiveSizeBytes();
        long quotaBytes = resolveQuotaBytes();
        if (category != FileCategory.GENERATED_PDF && (usedBytes + bytes.length) > quotaBytes) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "STORAGE_QUOTA_EXCEEDED: School storage quota exceeded");
        }

        String sanitizedFilename;
        try {
            sanitizedFilename = ObjectKeyValidator.sanitizeFilename(originalFilename);
        } catch (SecurityException se) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, se.getMessage());
        }

        byte[] headerSample = Arrays.copyOf(bytes, Math.min(bytes.length, 16_384));
        Set<String> allowedMimes = resolveAllowedMimeTypes();
        String detectedMime = magicByteValidator.validateAndDetectMime(
                sanitizedFilename,
                headerSample,
                category,
                allowedMimes);

        // Synchronous malware scan before persisting bytes
        MalwareScanner.ScanResult scanResult = malwareScanner.scan(
                new ByteArrayInputStream(bytes),
                sanitizedFilename);
        if (scanResult.isInfected()) {
            Long actorId = currentUserId();
            auditService.record(
                    "UPLOAD_MALWARE_DETECTED",
                    "STORED_FILE",
                    sanitizedFilename,
                    null,
                    Map.of(
                            "filename", sanitizedFilename,
                            "category", category.name(),
                            "signature", String.valueOf(scanResult.signature()),
                            "actorUserId", actorId != null ? actorId : 0L));
            outboxEventWriter.write(
                    "STORED_FILE",
                    sanitizedFilename,
                    "files.malware_detected",
                    "notification.high.malware_detected",
                    EventPriority.HIGH,
                    Map.of(
                            "filename", sanitizedFilename,
                            "signature", String.valueOf(scanResult.signature())));
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "UPLOAD_MALWARE_DETECTED: File rejected by malware scanner (" + scanResult.signature() + ")");
        }

        String fileUuid = UUID.randomUUID().toString();
        String objectKey = ObjectKeyValidator.buildObjectKey(
                category,
                fileUuid,
                sanitizedFilename,
                LocalDate.now(ZoneOffset.UTC));
        String sha256 = sha256Hex(bytes);

        storageProvider.putObject(objectKey, new ByteArrayInputStream(bytes), bytes.length, detectedMime);

        Instant now = Instant.now();
        StoredFile entity = new StoredFile();
        entity.setFileUuid(fileUuid);
        entity.setCategory(category);
        entity.setEntityType(normalizeBlank(entityType));
        entity.setEntityId(normalizeBlank(entityId));
        entity.setOriginalFilename(originalFilename != null && !originalFilename.isBlank()
                ? originalFilename
                : sanitizedFilename);
        entity.setSanitizedFilename(sanitizedFilename);
        entity.setObjectKey(objectKey);
        entity.setStorageProvider(storageProvider.providerName());
        entity.setMimeType(detectedMime);
        entity.setSizeBytes(bytes.length);
        entity.setSha256Checksum(sha256);
        entity.setScanStatus(scanResult.status());
        entity.setStatus(entity.getEntityType() != null && entity.getEntityId() != null
                ? FileStatus.ATTACHED
                : FileStatus.TEMP);
        entity.setUploadedByUserId(currentUserId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        StoredFile saved = storedFileRepository.save(entity);

        if (AUDITED_UPLOAD_CATEGORIES.contains(category)) {
            auditService.record(
                    "FILE_UPLOADED",
                    "STORED_FILE",
                    saved.getFileUuid(),
                    null,
                    Map.of(
                            "category", category.name(),
                            "objectKey", saved.getObjectKey(),
                            "sizeBytes", saved.getSizeBytes(),
                            "sha256", saved.getSha256Checksum()));
        }

        checkAndEmitQuotaWarning(usedBytes, usedBytes + bytes.length, quotaBytes);

        return StoredFileDto.from(saved);
    }

    @Transactional
    public StoredFileDto attachFile(
            String fileUuid,
            FileCategory expectedCategory,
            String entityType,
            String entityId) {
        StoredFile file = requireActiveFile(fileUuid);
        ObjectKeyValidator.assertBelongsToCurrentTenant(file.getObjectKey());
        if (expectedCategory != null && file.getCategory() != expectedCategory) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "File category mismatch: expected " + expectedCategory + " but found " + file.getCategory());
        }
        file.setEntityType(normalizeBlank(entityType));
        file.setEntityId(normalizeBlank(entityId));
        file.setStatus(FileStatus.ATTACHED);
        file.setUpdatedAt(Instant.now());
        return StoredFileDto.from(storedFileRepository.save(file));
    }

    @Transactional(readOnly = true)
    public StoredFileDto getFileMetadata(String fileUuid) {
        StoredFile file = requireActiveFile(fileUuid);
        ObjectKeyValidator.assertBelongsToCurrentTenant(file.getObjectKey());
        return StoredFileDto.from(file);
    }

    @Transactional(readOnly = true)
    public PresignedDownloadUrlDto generateDownloadUrl(String fileUuid, String requestedDisposition) {
        StoredFile file = requireActiveFile(fileUuid);
        ObjectKeyValidator.assertBelongsToCurrentTenant(file.getObjectKey());

        int ttlSeconds = Math.max(30, settingsService.getInt("storage.presigned_url_ttl_seconds", 300));
        Duration ttl = Duration.ofSeconds(ttlSeconds);

        String safeDisposition = "attachment";
        if ("inline".equalsIgnoreCase(requestedDisposition) && INLINE_SAFE_MIMES.contains(file.getMimeType())) {
            safeDisposition = "inline";
        }

        String url = storageProvider.generatePresignedGetUrl(
                file.getObjectKey(),
                file.getSanitizedFilename(),
                file.getMimeType(),
                safeDisposition,
                ttl);
        return new PresignedDownloadUrlDto(file.getFileUuid(), url, Instant.now().plus(ttl));
    }

    @Transactional
    public void softDeleteFile(String fileUuid) {
        StoredFile file = requireActiveFile(fileUuid);
        ObjectKeyValidator.assertBelongsToCurrentTenant(file.getObjectKey());

        if (file.getCategory() == FileCategory.GENERATED_PDF) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "System-generated official PDFs cannot be deleted");
        }

        Instant now = Instant.now();
        file.setStatus(FileStatus.DELETED);
        file.setDeletedAt(now);
        file.setUpdatedAt(now);
        storedFileRepository.save(file);

        auditService.record(
                "FILE_DELETED",
                "STORED_FILE",
                file.getFileUuid(),
                Map.of("status", FileStatus.ATTACHED.name(), "objectKey", file.getObjectKey()),
                Map.of("status", FileStatus.DELETED.name(), "deletedAt", now.toString()));
    }

    @Transactional(readOnly = true)
    public StorageQuotaDto getQuotaUsage() {
        long usedBytes = storedFileRepository.sumActiveSizeBytes();
        long quotaBytes = resolveQuotaBytes();
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (FileCategory cat : FileCategory.values()) {
            byCategory.put(cat.name(), 0L);
        }
        for (StoredFileRepository.CategoryCountProjection row : storedFileRepository.countActiveFilesByCategory()) {
            if (row.getCategory() != null) {
                byCategory.put(row.getCategory().name(), row.getFileCount());
            }
        }
        return new StorageQuotaDto(usedBytes, quotaBytes, byCategory);
    }

    private StoredFile requireActiveFile(String fileUuid) {
        return storedFileRepository.findByFileUuidAndDeletedAtIsNull(fileUuid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Stored file not found"));
    }

    private long resolveMaxFileSizeBytes(FileCategory category) {
        int maxMb = category.usesPhotoSizeCap()
                ? Math.max(1, settingsService.getInt("storage.max_photo_size_mb", 2))
                : Math.max(1, settingsService.getInt("storage.max_file_size_mb", 10));
        return maxMb * 1024L * 1024L;
    }

    private long resolveQuotaBytes() {
        int quotaMb = Math.max(1, settingsService.getInt("storage.max_quota_mb", 10240));
        return quotaMb * 1024L * 1024L;
    }

    private Set<String> resolveAllowedMimeTypes() {
        String csv = settingsService.getString(
                "storage.allowed_mime_types",
                "image/jpeg,image/png,image/webp,application/pdf,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private void checkAndEmitQuotaWarning(long previousBytes, long newBytes, long quotaBytes) {
        if (quotaBytes <= 0) {
            return;
        }
        double prevRatio = (double) previousBytes / quotaBytes;
        double newRatio = (double) newBytes / quotaBytes;
        if ((prevRatio < 0.85 && newRatio >= 0.85) || (prevRatio < 0.95 && newRatio >= 0.95)) {
            int thresholdPct = newRatio >= 0.95 ? 95 : 85;
            outboxEventWriter.write(
                    "STORAGE_QUOTA",
                    String.valueOf(thresholdPct),
                    "files.quota_warning",
                    "notification.bulk.quota_warning",
                    EventPriority.NORMAL,
                    Map.of(
                            "usedBytes", newBytes,
                            "quotaBytes", quotaBytes,
                            "thresholdPercent", thresholdPct));
        }
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SchoolUserPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    public record StoredFileDto(
            String fileUuid,
            FileCategory category,
            String entityType,
            String entityId,
            String originalFilename,
            String sanitizedFilename,
            String objectKey,
            String mimeType,
            long sizeBytes,
            String sha256Checksum,
            ScanStatus scanStatus,
            FileStatus status,
            Long uploadedByUserId,
            Instant createdAt) {

        public static StoredFileDto from(StoredFile f) {
            return new StoredFileDto(
                    f.getFileUuid(),
                    f.getCategory(),
                    f.getEntityType(),
                    f.getEntityId(),
                    f.getOriginalFilename(),
                    f.getSanitizedFilename(),
                    f.getObjectKey(),
                    f.getMimeType(),
                    f.getSizeBytes(),
                    f.getSha256Checksum(),
                    f.getScanStatus(),
                    f.getStatus(),
                    f.getUploadedByUserId(),
                    f.getCreatedAt());
        }
    }

    public record PresignedDownloadUrlDto(
            String fileUuid,
            String url,
            Instant expiresAt) {
    }

    public record StorageQuotaDto(
            long usedBytes,
            long quotaBytes,
            Map<String, Long> fileCountByCategory) {
    }
}

