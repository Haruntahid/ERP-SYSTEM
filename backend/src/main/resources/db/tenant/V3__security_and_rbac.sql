-- Rotating refresh tokens and baseline RBAC permissions per school database.

CREATE TABLE refresh_tokens (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    token_hash        VARCHAR(64)  NOT NULL,
    expires_at        TIMESTAMP(6) NOT NULL,
    revoked_at        TIMESTAMP(6) NULL,
    replaced_by_hash  VARCHAR(64)  NULL,
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    KEY idx_refresh_tokens_expires (expires_at),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO permissions (code, description) VALUES
    ('settings.read',  'View school settings and branding'),
    ('settings.write', 'Update school settings and branding'),
    ('user.read',      'View users and staff/student accounts'),
    ('user.write',     'Create and update user accounts'),
    ('role.read',      'View roles and permission grants'),
    ('role.write',     'Assign or revoke user roles and scopes'),
    ('audit.read',     'View school audit logs');

-- SCHOOL_ADMIN gets all baseline permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'SCHOOL_ADMIN';

-- Staff roles can read school settings
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'settings.read'
WHERE r.code IN ('REGISTRAR', 'ACCOUNTANT', 'TEACHER');

