-- V16__notices_and_communication.sql
-- Tenant schema migration for M-06: Notice Board and Targeted School Communication

CREATE TABLE notices (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    title                 VARCHAR(250)   NOT NULL,
    body_markdown         TEXT           NOT NULL,
    sms_body              VARCHAR(500)   NULL,
    category              VARCHAR(30)    NOT NULL DEFAULT 'GENERAL',
    priority              VARCHAR(20)    NOT NULL DEFAULT 'NORMAL',
    is_pinned             BOOLEAN        NOT NULL DEFAULT FALSE,
    is_public             BOOLEAN        NOT NULL DEFAULT FALSE,
    send_in_app           BOOLEAN        NOT NULL DEFAULT TRUE,
    send_email            BOOLEAN        NOT NULL DEFAULT FALSE,
    send_sms              BOOLEAN        NOT NULL DEFAULT FALSE,
    status                VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    scheduled_publish_at  TIMESTAMP(6)   NULL,
    published_at          TIMESTAMP(6)   NULL,
    expires_at            TIMESTAMP(6)   NULL,
    total_targeted_count  INT            NOT NULL DEFAULT 0,
    author_user_id        BIGINT         NOT NULL,
    published_by_user_id  BIGINT         NULL,
    created_at            TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at            TIMESTAMP(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_notices_status_pub (status, is_pinned, published_at),
    KEY idx_notices_scheduled (status, scheduled_publish_at),
    KEY idx_notices_category (category, status),
    CONSTRAINT fk_notices_author       FOREIGN KEY (author_user_id)       REFERENCES users (id),
    CONSTRAINT fk_notices_published_by FOREIGN KEY (published_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notice_audiences (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    notice_id         BIGINT       NOT NULL,
    target_type       VARCHAR(30)  NOT NULL,
    target_role_code  VARCHAR(60)  NULL,
    target_entity_id  BIGINT       NULL,
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_notice_aud_notice (notice_id),
    KEY idx_notice_aud_lookup (target_type, target_entity_id, target_role_code),
    CONSTRAINT fk_notice_aud_notice FOREIGN KEY (notice_id) REFERENCES notices (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notice_attachments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    notice_id       BIGINT        NOT NULL,
    stored_file_id  BIGINT        NOT NULL,
    display_name    VARCHAR(200)  NOT NULL,
    created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_notice_att_notice (notice_id),
    CONSTRAINT fk_notice_att_notice FOREIGN KEY (notice_id)      REFERENCES notices (id),
    CONSTRAINT fk_notice_att_file   FOREIGN KEY (stored_file_id) REFERENCES stored_files (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notice_read_receipts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    notice_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    read_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notice_read_user (notice_id, user_id),
    KEY idx_notice_read_user_time (user_id, read_at),
    CONSTRAINT fk_nrr_notice FOREIGN KEY (notice_id) REFERENCES notices (id),
    CONSTRAINT fk_nrr_user   FOREIGN KEY (user_id)   REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('notices.allow_teacher_publish', 'true'),
    ('notices.sms_max_length_chars',  '320'),
    ('notices.default_channels',      '["IN_APP"]'),
    ('notices.public_feed_enabled',   'false')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO permissions (code, description) VALUES
    ('notice.feed.read',           'View published notices targeted to the user and mark as read'),
    ('notice.manage.read',         'View notice management list and delivery/read statistics'),
    ('notice.write',               'Create and edit draft notices'),
    ('notice.publish',             'Publish or schedule notices to assigned scope'),
    ('notice.publish_school_wide', 'Target notices to WHOLE_SCHOOL or arbitrary classes/roles'),
    ('notice.broadcast_sms',       'Enable SMS broadcast channel when publishing a notice')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'notice.feed.read', 'notice.manage.read', 'notice.write',
    'notice.publish', 'notice.publish_school_wide', 'notice.broadcast_sms'
)
WHERE r.code = 'SCHOOL_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'notice.feed.read', 'notice.manage.read', 'notice.write',
    'notice.publish', 'notice.publish_school_wide'
)
WHERE r.code = 'REGISTRAR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'notice.feed.read', 'notice.manage.read', 'notice.write', 'notice.publish'
)
WHERE r.code = 'TEACHER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'notice.feed.read'
WHERE r.code IN ('ACCOUNTANT', 'STUDENT', 'GUARDIAN');

