-- M-02 staff and teacher assignment scaffolding

CREATE TABLE designations (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(40)  NOT NULL,
    title         VARCHAR(120) NOT NULL,
    category      VARCHAR(20)  NOT NULL DEFAULT 'TEACHING',
    display_order INT          NOT NULL DEFAULT 1,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at    TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_designations_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE staff (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NULL,
    campus_id          BIGINT       NOT NULL,
    department_id      BIGINT       NULL,
    designation_id     BIGINT       NOT NULL,
    employee_code      VARCHAR(50)  NOT NULL,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    full_name          VARCHAR(200) NOT NULL,
    email              VARCHAR(190) NULL,
    phone              VARCHAR(40)  NOT NULL,
    gender             VARCHAR(20)  NULL,
    date_of_birth      DATE         NULL,
    blood_group        VARCHAR(10)  NULL,
    national_id        VARCHAR(60)  NULL,
    qualification      VARCHAR(255) NULL,
    specialization     VARCHAR(150) NULL,
    staff_type         VARCHAR(20)  NOT NULL DEFAULT 'TEACHING',
    employment_status  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    join_date          DATE         NOT NULL,
    leave_date         DATE         NULL,
    photo_file_id      BIGINT       NULL,
    address            VARCHAR(500) NULL,
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at         TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_employee_code (employee_code),
    UNIQUE KEY uk_staff_user_id (user_id),
    KEY idx_staff_dept_status (department_id, employment_status),
    KEY idx_staff_type_status (staff_type, employment_status),
    KEY idx_staff_phone (phone),
    CONSTRAINT fk_staff_user        FOREIGN KEY (user_id)        REFERENCES users (id),
    CONSTRAINT fk_staff_campus      FOREIGN KEY (campus_id)      REFERENCES campuses (id),
    CONSTRAINT fk_staff_department  FOREIGN KEY (department_id)  REFERENCES departments (id),
    CONSTRAINT fk_staff_designation FOREIGN KEY (designation_id) REFERENCES designations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teacher_subject_assignments (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    academic_session_id BIGINT       NOT NULL,
    staff_id            BIGINT       NOT NULL,
    section_id          BIGINT       NOT NULL,
    subject_id          BIGINT       NULL,
    is_class_teacher    BOOLEAN      NOT NULL DEFAULT FALSE,
    role_type           VARCHAR(20)  NOT NULL DEFAULT 'PRIMARY',
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at          TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_tsa_session_section (academic_session_id, section_id),
    KEY idx_tsa_session_staff (academic_session_id, staff_id),
    KEY idx_tsa_subject (subject_id),
    CONSTRAINT fk_tsa_session FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT fk_tsa_staff   FOREIGN KEY (staff_id)            REFERENCES staff (id),
    CONSTRAINT fk_tsa_section FOREIGN KEY (section_id)          REFERENCES sections (id),
    CONSTRAINT fk_tsa_subject FOREIGN KEY (subject_id)          REFERENCES subjects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO designations (code, title, category, display_order) VALUES
    ('PRINCIPAL', 'Principal / Headmaster', 'TEACHING', 1),
    ('SR_TEACHER', 'Senior Teacher', 'TEACHING', 2),
    ('ASST_TEACHER', 'Assistant Teacher', 'TEACHING', 3),
    ('LECTURER', 'Lecturer', 'TEACHING', 4),
    ('REGISTRAR', 'Academic Registrar', 'NON_TEACHING', 5),
    ('ACCOUNTANT', 'Accounts Officer', 'NON_TEACHING', 6)
ON DUPLICATE KEY UPDATE title = VALUES(title), category = VALUES(category), display_order = VALUES(display_order);

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('hr.employee_code_template', 'EMP-{YEAR}-{SEQ:4}'),
    ('hr.auto_create_user', 'true')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO permissions (code, description) VALUES
    ('staff.read', 'View staff directory, profiles, and teacher assignments'),
    ('staff.write', 'Create and update staff records and designations'),
    ('staff.assign', 'Assign teachers to sections and subjects for an academic session'),
    ('staff.status.manage', 'Change staff employment status (leave, resignation, termination)')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('staff.read', 'staff.write', 'staff.assign', 'staff.status.manage')
WHERE r.code = 'SCHOOL_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('staff.read', 'staff.write', 'staff.assign')
WHERE r.code = 'REGISTRAR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'staff.read'
WHERE r.code IN ('ACCOUNTANT', 'TEACHER');

