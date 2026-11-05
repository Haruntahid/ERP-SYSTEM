INSERT INTO roles (code, name, system_role) VALUES
    ('SCHOOL_ADMIN', 'School Administrator', TRUE),
    ('REGISTRAR',    'Registrar',             TRUE),
    ('ACCOUNTANT',   'Accountant',            TRUE),
    ('TEACHER',      'Teacher',               TRUE),
    ('STUDENT',      'Student',               TRUE),
    ('GUARDIAN',     'Guardian',              TRUE);

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('school.display_name',   'My School'),
    ('school.timezone',       'Asia/Dhaka'),
    ('school.locale',         'en'),
    ('academic.session_type', 'YEARLY'),
    ('grading.scale',         'GPA_5'),
    ('brand.primary_color',   '#1d4ed8');
