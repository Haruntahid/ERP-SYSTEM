-- F-04: notification engine schema

CREATE TABLE notification_templates (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    code              VARCHAR(80)  NOT NULL,
    channel           VARCHAR(16)  NOT NULL,
    category          VARCHAR(30)  NOT NULL,
    locale            VARCHAR(10)  NOT NULL DEFAULT 'en',
    subject_template  VARCHAR(255) NULL,
    body_template     TEXT         NOT NULL,
    allowed_vars_json JSON         NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    system_template   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at        TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_nt_code_channel_locale (code, channel, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_deliveries (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    idempotency_key      VARCHAR(140) NOT NULL,
    template_code        VARCHAR(80)  NOT NULL,
    channel              VARCHAR(16)  NOT NULL,
    category             VARCHAR(30)  NOT NULL,
    recipient_user_id    BIGINT       NULL,
    recipient_address    VARCHAR(190) NOT NULL,
    rendered_subject     VARCHAR(255) NULL,
    rendered_body        TEXT         NOT NULL,
    status               VARCHAR(24)  NOT NULL DEFAULT 'QUEUED',
    provider_name        VARCHAR(40)  NULL,
    provider_message_id  VARCHAR(120) NULL,
    sms_segments         INT          NOT NULL DEFAULT 0,
    attempt_count        INT          NOT NULL DEFAULT 0,
    last_error           VARCHAR(500) NULL,
    next_retry_at        TIMESTAMP(6) NULL,
    sent_at              TIMESTAMP(6) NULL,
    created_at           TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_nd_idempotency (idempotency_key),
    KEY idx_nd_status_retry (status, next_retry_at),
    KEY idx_nd_recipient_created (recipient_user_id, created_at),
    KEY idx_nd_channel_created (channel, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE in_app_notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    category    VARCHAR(30)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    action_url  VARCHAR(255) NULL,
    read_at     TIMESTAMP(6) NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ian_user_unread (user_id, read_at, created_at),
    CONSTRAINT fk_ian_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_notification_preferences (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    category        VARCHAR(30)  NOT NULL,
    email_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    sms_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    in_app_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    push_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_unp_user_category (user_id, category),
    CONSTRAINT fk_unp_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sms_quota_usage (
    id                      BIGINT      NOT NULL AUTO_INCREMENT,
    `year_month`            CHAR(7)     NOT NULL,
    segments_used           INT         NOT NULL DEFAULT 0,
    security_segments_used  INT         NOT NULL DEFAULT 0,
    updated_at              TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_squ_year_month (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO app_settings (setting_key, setting_value)
VALUES
    ('notification.email.enabled', 'true'),
    ('notification.email.from_name', 'My School'),
    ('notification.email.reply_to', 'noreply@school.edu'),
    ('notification.sms.enabled', 'true'),
    ('notification.sms.mode', 'PLATFORM_CREDITS'),
    ('notification.sms.provider', 'MOCK'),
    ('notification.sms.sender_id', 'SCHOOLERP'),
    ('notification.sms.monthly_quota', '500'),
    ('notification.sms.extra_credits', '0')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO permissions (code, description)
VALUES
    ('notification.template.read', 'View notification templates'),
    ('notification.template.write', 'Update notification templates'),
    ('notification.delivery.read', 'View notification delivery logs and quota status'),
    ('notification.test.send', 'Send a test notification to a user or address')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'notification.template.read',
    'notification.template.write',
    'notification.delivery.read',
    'notification.test.send'
)
WHERE r.code = 'SCHOOL_ADMIN';
