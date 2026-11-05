-- M-01 academic structure

CREATE TABLE academic_sessions (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    code         VARCHAR(40)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    session_type VARCHAR(20)  NOT NULL DEFAULT 'YEARLY',
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'UPCOMING',
    is_current   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at   TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_academic_sessions_code (code),
    KEY idx_academic_sessions_status (status, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE campuses (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(500) NULL,
    phone       VARCHAR(40)  NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at  TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_campuses_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    campus_id   BIGINT       NOT NULL,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at  TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_campus_code (campus_id, code),
    KEY idx_departments_campus (campus_id),
    CONSTRAINT fk_dept_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shifts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    start_time  TIME         NOT NULL,
    end_time    TIME         NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at  TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shifts_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rooms (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    campus_id    BIGINT       NOT NULL,
    room_number  VARCHAR(40)  NOT NULL,
    name         VARCHAR(120) NULL,
    room_type    VARCHAR(30)  NOT NULL DEFAULT 'CLASSROOM',
    capacity     INT          NOT NULL DEFAULT 40,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at   TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rooms_campus_number (campus_id, room_number),
    KEY idx_rooms_campus (campus_id),
    CONSTRAINT fk_rooms_campus FOREIGN KEY (campus_id) REFERENCES campuses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE academic_classes (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    department_id BIGINT       NOT NULL,
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    numeric_level INT          NOT NULL DEFAULT 1,
    next_class_id BIGINT       NULL,
    display_order INT          NOT NULL DEFAULT 1,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at    TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_classes_dept_code (department_id, code),
    KEY idx_classes_level (numeric_level, display_order),
    CONSTRAINT fk_class_dept FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_class_next FOREIGN KEY (next_class_id) REFERENCES academic_classes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sections (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    class_id   BIGINT       NOT NULL,
    shift_id   BIGINT       NULL,
    room_id    BIGINT       NULL,
    code       VARCHAR(40)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    capacity   INT          NOT NULL DEFAULT 40,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sections_class_code (class_id, code),
    KEY idx_sections_class (class_id),
    KEY idx_sections_shift (shift_id),
    KEY idx_sections_room (room_id),
    CONSTRAINT fk_sec_class FOREIGN KEY (class_id) REFERENCES academic_classes (id),
    CONSTRAINT fk_sec_shift FOREIGN KEY (shift_id) REFERENCES shifts (id),
    CONSTRAINT fk_sec_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE subjects (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    code         VARCHAR(40)   NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    subject_type VARCHAR(20)   NOT NULL DEFAULT 'THEORY',
    credit_hours DECIMAL(4,2)  NOT NULL DEFAULT 1.00,
    created_at   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at   TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subjects_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE class_subjects (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    class_id      BIGINT        NOT NULL,
    subject_id    BIGINT        NOT NULL,
    is_mandatory  BOOLEAN       NOT NULL DEFAULT TRUE,
    max_marks     DECIMAL(6,2)  NOT NULL DEFAULT 100.00,
    pass_marks    DECIMAL(6,2)  NOT NULL DEFAULT 33.00,
    display_order INT           NOT NULL DEFAULT 1,
    created_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at    TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_subjects (class_id, subject_id),
    KEY idx_cs_subject (subject_id),
    CONSTRAINT fk_cs_class FOREIGN KEY (class_id) REFERENCES academic_classes (id),
    CONSTRAINT fk_cs_subject FOREIGN KEY (subject_id) REFERENCES subjects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO campuses (code, name, is_default) VALUES ('MAIN', 'Main Campus', TRUE);
INSERT INTO departments (campus_id, code, name, is_default)
SELECT id, 'GEN', 'General', TRUE FROM campuses WHERE code = 'MAIN';

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('academic.session_type', 'YEARLY'),
    ('academic.multi_campus', 'false'),
    ('academic.use_departments', 'false'),
    ('academic.shift_enabled', 'false'),
    ('academic.terminology_preset', 'K12_SCHOOL')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO permissions (code, description) VALUES
    ('academic.read', 'View academic sessions, classes, sections, and subjects'),
    ('academic.write', 'Create and update campuses, departments, classes, sections, and subjects'),
    ('academic.session.manage', 'Create, activate, and archive academic sessions')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('academic.read', 'academic.write', 'academic.session.manage')
WHERE r.code = 'SCHOOL_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('academic.read', 'academic.write')
WHERE r.code = 'REGISTRAR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'academic.read'
WHERE r.code IN ('ACCOUNTANT', 'TEACHER', 'STUDENT', 'GUARDIAN');

