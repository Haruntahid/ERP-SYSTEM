-- V15__attendance_and_leave.sql
-- Tenant schema migration for M-04: Student & Staff Attendance, Leave Applications, and Absence Alerts

CREATE TABLE leave_applications (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    applicant_type          VARCHAR(20)   NOT NULL,
    student_enrollment_id   BIGINT        NULL,
    staff_id                BIGINT        NULL,
    leave_type              VARCHAR(30)   NOT NULL,
    start_date              DATE          NOT NULL,
    end_date                DATE          NOT NULL,
    total_days              INT           NOT NULL,
    reason                  VARCHAR(1000) NOT NULL,
    attachment_file_id      BIGINT        NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    requested_by_user_id    BIGINT        NOT NULL,
    reviewed_by_user_id     BIGINT        NULL,
    review_remarks          VARCHAR(500)  NULL,
    reviewed_at             TIMESTAMP(6)  NULL,
    created_at              TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at              TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_leave_student_dates (student_enrollment_id, status, start_date, end_date),
    KEY idx_leave_staff_dates (staff_id, status, start_date, end_date),
    KEY idx_leave_status (status, created_at),
    CONSTRAINT fk_leave_enrollment   FOREIGN KEY (student_enrollment_id) REFERENCES enrollments (id),
    CONSTRAINT fk_leave_staff        FOREIGN KEY (staff_id)              REFERENCES staff (id),
    CONSTRAINT fk_leave_attachment   FOREIGN KEY (attachment_file_id)    REFERENCES stored_files (id),
    CONSTRAINT fk_leave_requested_by FOREIGN KEY (requested_by_user_id)  REFERENCES users (id),
    CONSTRAINT fk_leave_reviewed_by  FOREIGN KEY (reviewed_by_user_id)   REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attendance_sessions (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    academic_session_id   BIGINT        NOT NULL,
    campus_id             BIGINT        NOT NULL,
    class_id              BIGINT        NOT NULL,
    section_id            BIGINT        NOT NULL,
    subject_id            BIGINT        NULL,
    period_number         INT           NULL,
    attendance_date       DATE          NOT NULL,
    mode                  VARCHAR(20)   NOT NULL DEFAULT 'DAILY_SECTION',
    status                VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED',
    submitted_by_user_id  BIGINT        NOT NULL,
    submitted_at          TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    locked_at             TIMESTAMP(6)  NULL,
    total_students        INT           NOT NULL DEFAULT 0,
    present_count         INT           NOT NULL DEFAULT 0,
    absent_count          INT           NOT NULL DEFAULT 0,
    late_count            INT           NOT NULL DEFAULT 0,
    excused_count         INT           NOT NULL DEFAULT 0,
    created_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at            TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_attsess_section_date (academic_session_id, section_id, attendance_date, subject_id, period_number),
    KEY idx_attsess_date_status (attendance_date, status),
    KEY idx_attsess_class_date (class_id, attendance_date),
    CONSTRAINT fk_attsess_session      FOREIGN KEY (academic_session_id)  REFERENCES academic_sessions (id),
    CONSTRAINT fk_attsess_campus       FOREIGN KEY (campus_id)            REFERENCES campuses (id),
    CONSTRAINT fk_attsess_class        FOREIGN KEY (class_id)             REFERENCES academic_classes (id),
    CONSTRAINT fk_attsess_section      FOREIGN KEY (section_id)           REFERENCES sections (id),
    CONSTRAINT fk_attsess_subject      FOREIGN KEY (subject_id)           REFERENCES subjects (id),
    CONSTRAINT fk_attsess_submitted_by FOREIGN KEY (submitted_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_attendance_records (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    attendance_session_id     BIGINT        NOT NULL,
    enrollment_id             BIGINT        NOT NULL,
    student_id                BIGINT        NOT NULL,
    status                    VARCHAR(20)   NOT NULL,
    source                    VARCHAR(20)   NOT NULL DEFAULT 'MANUAL_WEB',
    leave_application_id      BIGINT        NULL,
    remarks                   VARCHAR(300)  NULL,
    last_modified_by_user_id  BIGINT        NOT NULL,
    created_at                TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_std_att_session_enrollment (attendance_session_id, enrollment_id),
    KEY idx_std_att_student_status (student_id, status),
    KEY idx_std_att_enrollment (enrollment_id, status),
    CONSTRAINT fk_std_att_session     FOREIGN KEY (attendance_session_id)    REFERENCES attendance_sessions (id),
    CONSTRAINT fk_std_att_enrollment  FOREIGN KEY (enrollment_id)            REFERENCES enrollments (id),
    CONSTRAINT fk_std_att_student     FOREIGN KEY (student_id)               REFERENCES students (id),
    CONSTRAINT fk_std_att_leave       FOREIGN KEY (leave_application_id)     REFERENCES leave_applications (id),
    CONSTRAINT fk_std_att_modified_by FOREIGN KEY (last_modified_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE staff_attendance_records (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    staff_id              BIGINT        NOT NULL,
    attendance_date       DATE          NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    check_in_time         TIME          NULL,
    check_out_time        TIME          NULL,
    source                VARCHAR(20)   NOT NULL DEFAULT 'MANUAL_WEB',
    leave_application_id  BIGINT        NULL,
    remarks               VARCHAR(300)  NULL,
    recorded_by_user_id   BIGINT        NOT NULL,
    created_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_att_date (staff_id, attendance_date),
    KEY idx_staff_att_date_status (attendance_date, status),
    CONSTRAINT fk_staff_att_staff       FOREIGN KEY (staff_id)             REFERENCES staff (id),
    CONSTRAINT fk_staff_att_leave       FOREIGN KEY (leave_application_id) REFERENCES leave_applications (id),
    CONSTRAINT fk_staff_att_recorded_by FOREIGN KEY (recorded_by_user_id)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE absence_alert_queue (
    id                            BIGINT       NOT NULL AUTO_INCREMENT,
    student_attendance_record_id  BIGINT       NOT NULL,
    student_id                    BIGINT       NOT NULL,
    guardian_id                   BIGINT       NULL,
    attendance_date               DATE         NOT NULL,
    scheduled_send_at             TIMESTAMP(6) NOT NULL,
    status                        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    processed_at                  TIMESTAMP(6) NULL,
    created_at                    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_record (student_attendance_record_id),
    KEY idx_alert_due (status, scheduled_send_at),
    CONSTRAINT fk_alert_att_record FOREIGN KEY (student_attendance_record_id) REFERENCES student_attendance_records (id),
    CONSTRAINT fk_alert_student    FOREIGN KEY (student_id)                   REFERENCES students (id),
    CONSTRAINT fk_alert_guardian   FOREIGN KEY (guardian_id)                  REFERENCES guardians (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('attendance.mode',                 'DAILY_SECTION'),
    ('attendance.edit_window_hours',    '48'),
    ('attendance.notify_absent_sms',    'true'),
    ('attendance.notify_late_in_app',   'true'),
    ('attendance.notify_delay_minutes', '30'),
    ('attendance.minimum_percentage',   '75.00'),
    ('attendance.default_mark',         'PRESENT')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO permissions (code, description) VALUES
    ('attendance.read',          'View student attendance sheets and individual history'),
    ('attendance.write',         'Submit and amend student attendance within edit window'),
    ('attendance.override_lock', 'Unlock or amend student attendance after edit window expires'),
    ('attendance.report',        'View monthly attendance grids and chronic absentee reports'),
    ('attendance.staff.read',    'View staff attendance records'),
    ('attendance.staff.write',   'Record and amend daily staff attendance'),
    ('leave.request',            'Submit student or staff leave applications'),
    ('leave.read',               'View leave applications'),
    ('leave.approve',            'Approve or reject student/staff leave applications')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'attendance.read', 'attendance.write', 'attendance.override_lock', 'attendance.report',
    'attendance.staff.read', 'attendance.staff.write',
    'leave.request', 'leave.read', 'leave.approve'
)
WHERE r.code IN ('SCHOOL_ADMIN', 'REGISTRAR');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'attendance.read', 'attendance.write', 'attendance.report',
    'attendance.staff.read', 'leave.request', 'leave.read', 'leave.approve'
)
WHERE r.code = 'TEACHER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('attendance.read', 'leave.request', 'leave.read')
WHERE r.code IN ('STUDENT', 'GUARDIAN');

