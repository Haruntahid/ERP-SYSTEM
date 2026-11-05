package com.schoolerp.shared.storage;

import com.schoolerp.shared.error.ApiException;
import com.schoolerp.shared.storage.FileStorageService.PresignedDownloadUrlDto;
import com.schoolerp.shared.storage.FileStorageService.StorageQuotaDto;
import com.schoolerp.shared.storage.FileStorageService.StoredFileDto;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tenant-scoped REST controller for uploading, inspecting, streaming, and soft-deleting files.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileStorageController {

    private final FileStorageService fileStorageService;
    private final StorageProvider storageProvider;
    private final LocalFsStorageProvider localFsStorageProvider;

    public FileStorageController(
            FileStorageService fileStorageService,
            StorageProvider storageProvider,
            @Autowired(required = false) LocalFsStorageProvider localFsStorageProvider) {
        this.fileStorageService = fileStorageService;
        this.storageProvider = storageProvider;
        this.localFsStorageProvider = localFsStorageProvider;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.has(authentication, 'file.upload')")
    public StoredFileDto uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") FileCategory category,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) String entityId) {
        return fileStorageService.uploadFile(file, category, entityType, entityId);
    }

    @GetMapping("/quota")
    @PreAuthorize("@perm.has(authentication, 'file.admin')")
    public StorageQuotaDto getQuotaUsage() {
        return fileStorageService.getQuotaUsage();
    }

    @GetMapping("/{fileUuid}")
    @PreAuthorize("@perm.has(authentication, 'file.read')")
    public StoredFileDto getFileMetadata(@PathVariable String fileUuid) {
        return fileStorageService.getFileMetadata(fileUuid);
    }

    @GetMapping("/{fileUuid}/download-url")
    @PreAuthorize("@perm.has(authentication, 'file.read')")
    public PresignedDownloadUrlDto getDownloadUrl(
            @PathVariable String fileUuid,
            @RequestParam(value = "disposition", defaultValue = "attachment") String disposition) {
        return fileStorageService.generateDownloadUrl(fileUuid, disposition);
    }

    @GetMapping("/stream/{token}")
    public ResponseEntity<Resource> streamSignedFile(@PathVariable String token) {
        if (localFsStorageProvider == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Local stream endpoint is not active");
        }
        try {
            LocalFsStorageProvider.StreamTokenClaims claims =
                    localFsStorageProvider.verifyAndParseStreamToken(token);
            InputStream stream = storageProvider.openStream(claims.objectKey());

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore().mustRevalidate().cachePrivate())
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            claims.disposition() + "; filename=\"" + claims.filename() + "\"")
                    .contentType(MediaType.parseMediaType(claims.contentType()))
                    .body(new InputStreamResource(stream));
        } catch (SecurityException se) {
            throw new ApiException(HttpStatus.FORBIDDEN, se.getMessage());
        } catch (IllegalArgumentException iae) {
            throw new ApiException(HttpStatus.NOT_FOUND, iae.getMessage());
        }
    }

    @DeleteMapping("/{fileUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.has(authentication, 'file.delete')")
    public void deleteFile(@PathVariable String fileUuid) {
        fileStorageService.softDeleteFile(fileUuid);
    }
}

