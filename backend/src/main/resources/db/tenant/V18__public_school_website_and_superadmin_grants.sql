-- V18: SuperAdmin full-privilege grants and Public School Website settings

INSERT INTO roles (code, name, system_role)
VALUES ('SUPER_ADMIN', 'Super Administrator', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO permissions (code, description)
VALUES ('*', 'Superadmin wildcard permission for all ERP modules and settings')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- Grant every permission in the system to both SCHOOL_ADMIN and SUPER_ADMIN
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('SCHOOL_ADMIN', 'SUPER_ADMIN');

-- Ensure every existing SCHOOL_ADMIN user also holds SUPER_ADMIN (ALL scope)
INSERT IGNORE INTO user_roles (user_id, role_id, scope_type, scope_id)
SELECT ur.user_id, sa.id, 'ALL', 0
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id AND r.code = 'SCHOOL_ADMIN'
CROSS JOIN roles sa
WHERE sa.code = 'SUPER_ADMIN';

-- Default Public Website & Branding Settings for each school
INSERT INTO app_settings (setting_key, setting_value) VALUES
  ('website.hero_title', 'Inspiring Excellence, Shaping Future Leaders'),
  ('website.hero_subtitle', 'A modern digital campus connecting students, teachers, and guardians with world-class academics.'),
  ('website.about_text', 'Our institution combines strong academic traditions with modern digital learning, character education, and transparent school governance.'),
  ('website.principal_name', 'Prof. Mohammad Rahman'),
  ('website.principal_message', 'Welcome to our official school portal. Our mission is to nurture curious minds, ethical citizens, and lifelong learners in a safe and inspiring environment.'),
  ('website.contact_phone', '+880 1700-000000'),
  ('website.contact_email', 'info@school.edu.bd'),
  ('website.contact_address', 'College Road, Main Campus, Bangladesh'),
  ('website.established_year', '1992'),
  ('website.eiin_number', '120789')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

