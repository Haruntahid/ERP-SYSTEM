-- Seed university credit hours setting (false by default for K-12 schools; true for universities/colleges)
INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES
    ('academic.credit_hours_enabled', 'false');

