-- V14__students_guardians_admission_enrollment.sql
-- Tenant schema migration for M-03: Students, Guardians, Admission Workflow, and Session Enrollment

CREATE TABLE student_code_sequences (
    sequence_key   VARCHAR(60)  NOT NULL,
    `last_value`   BIGINT       NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (sequence_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE students (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    student_code              VARCHAR(40)   NOT NULL,
    user_id                   BIGINT        NULL,
    first_name                VARCHAR(100)  NOT NULL,
    last_name                 VARCHAR(100)  NOT NULL,
    full_name_bn              VARCHAR(200)  NULL,
    date_of_birth             DATE          NOT NULL,
    gender                    VARCHAR(20)   NOT NULL,
    blood_group               VARCHAR(10)   NULL,
    religion                  VARCHAR(40)   NULL,
    nationality               VARCHAR(60)   NOT NULL DEFAULT 'Bangladeshi',
    birth_reg_or_nid          VARCHAR(60)   NULL,
    email                     VARCHAR(190)  NULL,
    phone                     VARCHAR(30)   NULL,
    present_address_json      JSON          NULL,
    permanent_address_json    JSON          NULL,
    photo_file_id             BIGINT        NULL,
    medical_notes             TEXT          NULL,
    rfid_card_uid             VARCHAR(64)   NULL,
    admission_date            DATE          NOT NULL,
    status                    VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at                TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at                TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_students_code (student_code),
    UNIQUE KEY uk_students_user (user_id),
    UNIQUE KEY uk_students_rfid (rfid_card_uid),
    KEY idx_students_status (status, deleted_at),
    KEY idx_students_name (last_name, first_name),
    KEY idx_students_dob (date_of_birth),
    CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guardians (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    user_id          BIGINT        NULL,
    full_name        VARCHAR(200)  NOT NULL,
    phone            VARCHAR(30)   NOT NULL,
    email            VARCHAR(190)  NULL,
    occupation       VARCHAR(100)  NULL,
    organization     VARCHAR(150)  NULL,
    national_id_no   VARCHAR(60)   NULL,
    address_json     JSON          NULL,
    photo_file_id    BIGINT        NULL,
    created_at       TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at       TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_guardians_user (user_id),
    KEY idx_guardians_phone (phone),
    KEY idx_guardians_email (email),
    CONSTRAINT fk_guardians_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_guardians (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    student_id            BIGINT       NOT NULL,
    guardian_id           BIGINT       NOT NULL,
    relationship          VARCHAR(30)  NOT NULL,
    is_primary_contact    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_emergency_contact  BOOLEAN      NOT NULL DEFAULT TRUE,
    can_pickup            BOOLEAN      NOT NULL DEFAULT TRUE,
    is_fee_payer          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_guardian (student_id, guardian_id),
    KEY idx_sg_guardian (guardian_id),
    CONSTRAINT fk_sg_student  FOREIGN KEY (student_id)  REFERENCES students (id),
    CONSTRAINT fk_sg_guardian FOREIGN KEY (guardian_id) REFERENCES guardians (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE enrollments (
    id                           BIGINT        NOT NULL AUTO_INCREMENT,
    student_id                   BIGINT        NOT NULL,
    academic_session_id          BIGINT        NOT NULL,
    campus_id                    BIGINT        NOT NULL,
    department_id                BIGINT        NULL,
    class_id                     BIGINT        NOT NULL,
    section_id                   BIGINT        NOT NULL,
    roll_number                  INT           NOT NULL,
    enrollment_date              DATE          NOT NULL,
    status                       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    promoted_from_enrollment_id  BIGINT        NULL,
    remarks                      VARCHAR(500)  NULL,
    created_by_user_id           BIGINT        NULL,
    created_at                   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                   TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at                   TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_enrollments_student_session (student_id, academic_session_id, status),
    KEY idx_enrollments_session_class_section (academic_session_id, class_id, section_id, status),
    KEY idx_enrollments_section_roll (academic_session_id, section_id, roll_number),
    KEY idx_enrollments_campus_dept (campus_id, department_id),
    KEY idx_enrollments_promoted_from (promoted_from_enrollment_id),
    CONSTRAINT fk_enrollments_student       FOREIGN KEY (student_id)                  REFERENCES students (id),
    CONSTRAINT fk_enrollments_session       FOREIGN KEY (academic_session_id)         REFERENCES academic_sessions (id),
    CONSTRAINT fk_enrollments_campus        FOREIGN KEY (campus_id)                   REFERENCES campuses (id),
    CONSTRAINT fk_enrollments_department    FOREIGN KEY (department_id)               REFERENCES departments (id),
    CONSTRAINT fk_enrollments_class         FOREIGN KEY (class_id)                    REFERENCES academic_classes (id),
    CONSTRAINT fk_enrollments_section       FOREIGN KEY (section_id)                  REFERENCES sections (id),
    CONSTRAINT fk_enrollments_promoted_from FOREIGN KEY (promoted_from_enrollment_id) REFERENCES enrollments (id),
    CONSTRAINT fk_enrollments_created_by    FOREIGN KEY (created_by_user_id)          REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admission_applications (
    id                        BIGINT         NOT NULL AUTO_INCREMENT,
    application_no            VARCHAR(40)    NOT NULL,
    academic_session_id       BIGINT         NOT NULL,
    target_campus_id          BIGINT         NOT NULL,
    target_class_id           BIGINT         NOT NULL,
    target_section_id         BIGINT         NULL,
    applicant_first_name      VARCHAR(100)   NOT NULL,
    applicant_last_name       VARCHAR(100)   NOT NULL,
    full_name_bn              VARCHAR(200)   NULL,
    date_of_birth             DATE           NOT NULL,
    gender                    VARCHAR(20)    NOT NULL,
    blood_group               VARCHAR(10)    NULL,
    previous_school_name      VARCHAR(200)   NULL,
    previous_class_result     VARCHAR(100)   NULL,
    guardian_name             VARCHAR(200)   NOT NULL,
    guardian_phone            VARCHAR(30)    NOT NULL,
    guardian_email            VARCHAR(190)   NULL,
    guardian_relationship     VARCHAR(30)    NOT NULL DEFAULT 'FATHER',
    application_fee_amount    DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    application_fee_currency  CHAR(3)        NOT NULL DEFAULT 'BDT',
    payload_json              JSON           NULL,
    status                    VARCHAR(25)    NOT NULL DEFAULT 'DRAFT',
    reviewer_user_id          BIGINT         NULL,
    review_notes              VARCHAR(1000)  NULL,
    reviewed_at               TIMESTAMP(6)   NULL,
    enrolled_student_id       BIGINT         NULL,
    created_at                TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at                TIMESTAMP(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admission_app_no (application_no),
    KEY idx_admission_session_status (academic_session_id, status),
    KEY idx_admission_target_class (target_class_id, status),
    KEY idx_admission_guardian_phone (guardian_phone),
    CONSTRAINT fk_admission_session          FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT fk_admission_campus           FOREIGN KEY (target_campus_id)    REFERENCES campuses (id),
    CONSTRAINT fk_admission_class            FOREIGN KEY (target_class_id)     REFERENCES academic_classes (id),
    CONSTRAINT fk_admission_section          FOREIGN KEY (target_section_id)   REFERENCES sections (id),
    CONSTRAINT fk_admission_reviewer         FOREIGN KEY (reviewer_user_id)    REFERENCES users (id),
    CONSTRAINT fk_admission_enrolled_student FOREIGN KEY (enrolled_student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_documents (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    student_id           BIGINT        NOT NULL,
    document_type        VARCHAR(40)   NOT NULL,
    title                VARCHAR(200)  NOT NULL,
    stored_file_id       BIGINT        NOT NULL,
    verified_by_user_id  BIGINT        NULL,
    verified_at          TIMESTAMP(6)  NULL,
    created_at           TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at           TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_sdoc_student (student_id, deleted_at),
    CONSTRAINT fk_sdoc_student  FOREIGN KEY (student_id)          REFERENCES students (id),
    CONSTRAINT fk_sdoc_file     FOREIGN KEY (stored_file_id)      REFERENCES stored_files (id),
    CONSTRAINT fk_sdoc_verifier FOREIGN KEY (verified_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('student.id_template',              '{YEAR}{CLASS_CODE}{SEQ:4}'),
    ('student.allow_manual_code',        'false'),
    ('student.auto_create_portal_user',  'false'),
    ('guardian.auto_create_portal_user', 'true'),
    ('admission.online_enabled',         'true'),
    ('admission.application_prefix',     'ADM-{YEAR}-{SEQ:5}')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO permissions (code, description) VALUES
    ('admission.read',     'View admission applications'),
    ('admission.write',    'Create and edit admission applications'),
    ('admission.review',   'Review, approve, waitlist, or reject applications'),
    ('admission.enroll',   'Convert approved application into enrolled student'),
    ('student.read',       'View student profiles and documents'),
    ('student.write',      'Create and update student profiles and documents'),
    ('student.delete',     'Soft-delete/archive student records'),
    ('student.import',     'Bulk import students and enrollments'),
    ('enrollment.read',    'View session enrollments and class rosters'),
    ('enrollment.write',   'Enroll students, change sections, or update roll numbers'),
    ('enrollment.promote', 'Execute batch session promotion wizard'),
    ('guardian.read',      'View guardian contact information'),
    ('guardian.write',     'Create, update, and link guardians to students')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'admission.read', 'admission.write', 'admission.review', 'admission.enroll',
    'student.read', 'student.write', 'student.delete', 'student.import',
    'enrollment.read', 'enrollment.write', 'enrollment.promote',
    'guardian.read', 'guardian.write'
)
WHERE r.code = 'SCHOOL_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'admission.read', 'admission.write', 'admission.review', 'admission.enroll',
    'student.read', 'student.write', 'student.import',
    'enrollment.read', 'enrollment.write', 'enrollment.promote',
    'guardian.read', 'guardian.write'
)
WHERE r.code = 'REGISTRAR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('student.read', 'enrollment.read', 'guardian.read')
WHERE r.code IN ('ACCOUNTANT', 'TEACHER', 'STUDENT', 'GUARDIAN');
