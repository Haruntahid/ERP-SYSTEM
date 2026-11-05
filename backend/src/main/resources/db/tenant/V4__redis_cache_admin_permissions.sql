-- Seed per-school account lockout and rate-limit settings plus cache/lockout admin permissions.

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('security.lockout.max_attempts',     '5'),
    ('security.lockout.duration_minutes', '15'),
    ('ratelimit.login_per_minute',        '10'),
    ('ratelimit.api_per_minute',          '300')
AS new_settings
ON DUPLICATE KEY UPDATE setting_value = new_settings.setting_value;

INSERT INTO permissions (code, description) VALUES
    ('security.unlock', 'View and clear temporary account lockouts for school users'),
    ('cache.evict',     'Inspect and flush school application caches');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('security.unlock', 'cache.evict')
WHERE r.code = 'SCHOOL_ADMIN';
