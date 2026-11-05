package com.schoolerp.shared.storage;

import java.util.Set;

/**
 *Logical file storage categories with per-category MIME and retention rules.
 */
public enum FileCategory {
    SCHOOL_LOGO(true, false, Set.of("image/jpeg", "image/png", "image/webp")),
    PROFILE_PHOTO(true, false, Set.of("image/jpeg", "image/png", "image/webp")),
    STUDENT_DOC(false, true, Set.of("image/jpeg", "image/png", "image/webp", "application/pdf")),
    STAFF_DOC(false, true, Set.of("image/jpeg", "image/png", "image/webp", "application/pdf")),
    NOTICE_ATTACHMENT(false, false, Set.of("image/jpeg", "image/png", "image/webp", "application/pdf")),
    GENERATED_PDF(false, true, Set.of("application/pdf")),
    BULK_IMPORT(false, false, Set.of(
            "text/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final boolean photoSizeCap;
    private final boolean permanentRetention;
    private final Set<String> allowedMimeTypes;

    FileCategory(boolean photoSizeCap, boolean permanentRetention, Set<String> allowedMimeTypes) {
        this.photoSizeCap = photoSizeCap;
        this.permanentRetention = permanentRetention;
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public boolean usesPhotoSizeCap() {
        return photoSizeCap;
    }

    public boolean isPermanentRetention() {
        return permanentRetention;
    }

    public Set<String> allowedMimeTypes() {
        return allowedMimeTypes;
    }
}

