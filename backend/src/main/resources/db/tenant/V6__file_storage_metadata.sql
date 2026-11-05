-- F-06: Tenant-isolated file storage metadata per school database.

CREATE TABLE stored_files (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    file_uuid            VARCHAR(36)  NOT NULL,
    category             VARCHAR(40)  NOT NULL,
    entity_type          VARCHAR(60)  NULL,
    entity_id            VARCHAR(100) NULL,
    original_filename    VARCHAR(255) NOT NULL,
    sanitized_filename   VARCHAR(200) NOT NULL,
    object_key           VARCHAR(500) NOT NULL,
    storage_provider     VARCHAR(20)  NOT NULL,
    mime_type            VARCHAR(120) NOT NULL,
    size_bytes           BIGINT       NOT NULL,
    sha256_checksum      CHAR(64)     NOT NULL,
    scan_status          VARCHAR(20)  NOT NULL DEFAULT 'CLEAN',
    status               VARCHAR(20)  NOT NULL DEFAULT 'TEMP',
    uploaded_by_user_id  BIGINT       NULL,
    created_at           TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at           TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stored_files_uuid (file_uuid),
    UNIQUE KEY uk_stored_files_object_key (object_key),
    KEY idx_stored_files_entity (entity_type, entity_id, status),
    KEY idx_stored_files_category (category, status),
    KEY idx_stored_files_uploader (uploaded_by_user_id),
    KEY idx_stored_files_cleanup (status, created_at, deleted_at),
    CONSTRAINT fk_sf_uploaded_by FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('storage.max_file_size_mb',          '10'),
    ('storage.max_photo_size_mb',         '2'),
    ('storage.allowed_mime_types',        'image/jpeg,image/png,image/webp,application/pdf,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'),
    ('storage.presigned_url_ttl_seconds', '300'),
    ('storage.max_quota_mb',              '10240'),
    ('storage.trash_retention_days',      '30')
AS new_settings
ON DUPLICATE KEY UPDATE
    setting_value = new_settings.setting_value;

INSERT INTO permissions (code, description) VALUES
    ('file.upload', 'Upload files and attachments within permitted categories'),
    ('file.read',   'View file metadata and request short-lived download URLs'),
    ('file.delete', 'Soft-delete uploaded files'),
    ('file.admin',  'View school storage quota usage and manage all stored files')
AS new_perms
ON DUPLICATE KEY UPDATE
    description = new_perms.description;

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('file.upload', 'file.read', 'file.delete', 'file.admin')
WHERE r.code = 'SCHOOL_ADMIN';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('file.upload', 'file.read', 'file.delete')
WHERE r.code IN ('REGISTRAR', 'ACCOUNTANT', 'TEACHER');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'file.read'
WHERE r.code IN ('STUDENT', 'GUARDIAN');

