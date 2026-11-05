-- F-05: Transactional Outbox and Idempotent Consumer Inbox

CREATE TABLE outbox_events (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id          VARCHAR(36) NOT NULL,
    aggregate_type    VARCHAR(80) NOT NULL,
    aggregate_id      VARCHAR(80) NOT NULL,
    event_type        VARCHAR(120) NOT NULL,
    routing_key       VARCHAR(160) NOT NULL,
    priority          VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    payload_json      JSON NOT NULL,
    headers_json      JSON NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts          INT NOT NULL DEFAULT 0,
    max_attempts      INT NOT NULL DEFAULT 3,
    next_retry_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error        TEXT NULL,
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at      TIMESTAMP(6) NULL,
    updated_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_outbox_events_event_id UNIQUE (event_id),
    INDEX idx_outbox_events_poll (status, next_retry_at, priority, id),
    INDEX idx_outbox_events_aggregate (aggregate_type, aggregate_id),
    INDEX idx_outbox_events_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE processed_events (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id          VARCHAR(36) NOT NULL,
    consumer_group    VARCHAR(100) NOT NULL,
    event_type        VARCHAR(120) NOT NULL,
    processed_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_processed_events_event_consumer UNIQUE (event_id, consumer_group),
    INDEX idx_processed_events_processed_at (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('outbox.retention_days', '30'),
    ('outbox.max_attempts',   '3'),
    ('outbox.batch_size',     '50')
AS new_settings
ON DUPLICATE KEY UPDATE
    setting_value = new_settings.setting_value;

INSERT INTO permissions (code, description) VALUES
    ('messaging.read',   'Inspect transactional outbox events, delivery logs, and dead-letter queue entries'),
    ('messaging.replay', 'Replay failed or dead-lettered transactional outbox events')
AS new_perms
ON DUPLICATE KEY UPDATE
    description = new_perms.description;

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('messaging.read', 'messaging.replay')
WHERE r.code = 'SCHOOL_ADMIN';
