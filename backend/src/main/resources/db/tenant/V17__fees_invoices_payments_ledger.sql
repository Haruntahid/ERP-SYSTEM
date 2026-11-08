-- V17__fees_invoices_payments_ledger.sql
-- Tenant schema migration for M-05: Fees, Invoices, Immutable Ledger, Payment Gateways, and Reconciliation

CREATE TABLE fee_number_sequences (
    sequence_key   VARCHAR(60)  NOT NULL,
    `last_value`   BIGINT       NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (sequence_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fee_heads (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    code                 VARCHAR(40)   NOT NULL,
    name                 VARCHAR(150)  NOT NULL,
    category             VARCHAR(30)   NOT NULL DEFAULT 'TUITION',
    allocation_priority  INT           NOT NULL DEFAULT 100,
    is_refundable        BOOLEAN       NOT NULL DEFAULT FALSE,
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at           TIMESTAMP(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fee_heads_code (code),
    KEY idx_fee_heads_priority (allocation_priority, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fee_structures (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    academic_session_id    BIGINT         NOT NULL,
    campus_id              BIGINT         NOT NULL,
    class_id               BIGINT         NOT NULL,
    name                   VARCHAR(150)   NOT NULL,
    currency               CHAR(3)        NOT NULL DEFAULT 'BDT',
    late_fine_policy_type  VARCHAR(30)    NOT NULL DEFAULT 'NONE',
    late_fine_value        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    late_fine_cap_amount   DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    late_fine_grace_days   INT            NOT NULL DEFAULT 0,
    status                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at             TIMESTAMP(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_feestruct_session_class (academic_session_id, class_id, status),
    CONSTRAINT fk_feestruct_session FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT fk_feestruct_campus  FOREIGN KEY (campus_id)           REFERENCES campuses (id),
    CONSTRAINT fk_feestruct_class   FOREIGN KEY (class_id)            REFERENCES academic_classes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fee_structure_lines (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    fee_structure_id   BIGINT         NOT NULL,
    fee_head_id        BIGINT         NOT NULL,
    frequency          VARCHAR(20)    NOT NULL DEFAULT 'MONTHLY',
    amount             DECIMAL(12,2)  NOT NULL,
    due_day_of_month   INT            NOT NULL DEFAULT 10,
    is_mandatory       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_feestruct_line_head (fee_structure_id, fee_head_id),
    CONSTRAINT fk_fsl_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structures (id),
    CONSTRAINT fk_fsl_head      FOREIGN KEY (fee_head_id)      REFERENCES fee_heads (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_discount_waivers (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    enrollment_id         BIGINT         NOT NULL,
    student_id            BIGINT         NOT NULL,
    fee_head_id           BIGINT         NOT NULL,
    title                 VARCHAR(150)   NOT NULL,
    discount_type         VARCHAR(20)    NOT NULL,
    discount_value        DECIMAL(12,2)  NOT NULL,
    currency              CHAR(3)        NOT NULL DEFAULT 'BDT',
    status                VARCHAR(25)    NOT NULL DEFAULT 'PENDING_APPROVAL',
    reason                VARCHAR(500)   NOT NULL,
    requested_by_user_id  BIGINT         NOT NULL,
    approved_by_user_id   BIGINT         NULL,
    approved_at           TIMESTAMP(6)   NULL,
    created_at            TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at            TIMESTAMP(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_waiver_enrollment (enrollment_id, status),
    KEY idx_waiver_student (student_id, status),
    CONSTRAINT fk_waiver_enrollment   FOREIGN KEY (enrollment_id)        REFERENCES enrollments (id),
    CONSTRAINT fk_waiver_student      FOREIGN KEY (student_id)           REFERENCES students (id),
    CONSTRAINT fk_waiver_head         FOREIGN KEY (fee_head_id)          REFERENCES fee_heads (id),
    CONSTRAINT fk_waiver_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_waiver_approved_by  FOREIGN KEY (approved_by_user_id)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invoices (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_no             VARCHAR(40)    NOT NULL,
    enrollment_id          BIGINT         NOT NULL,
    student_id             BIGINT         NOT NULL,
    academic_session_id    BIGINT         NOT NULL,
    billing_month          CHAR(7)        NULL,
    issue_date             DATE           NOT NULL,
    due_date               DATE           NOT NULL,
    currency               CHAR(3)        NOT NULL DEFAULT 'BDT',
    gross_amount           DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    discount_amount        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    fine_amount            DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    net_payable_amount     DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    allocated_paid_amount  DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    balance_due_amount     DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    status                 VARCHAR(25)    NOT NULL DEFAULT 'DRAFT',
    generation_batch_key   VARCHAR(80)    NULL,
    cancelled_reason       VARCHAR(500)   NULL,
    created_by_user_id     BIGINT         NULL,
    created_at             TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at             TIMESTAMP(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoices_no (invoice_no),
    KEY idx_invoices_student_status (student_id, status),
    KEY idx_invoices_enrollment_month (enrollment_id, billing_month),
    KEY idx_invoices_session_status_due (academic_session_id, status, due_date),
    KEY idx_invoices_batch_key (generation_batch_key),
    CONSTRAINT fk_invoices_enrollment FOREIGN KEY (enrollment_id)       REFERENCES enrollments (id),
    CONSTRAINT fk_invoices_student    FOREIGN KEY (student_id)          REFERENCES students (id),
    CONSTRAINT fk_invoices_session    FOREIGN KEY (academic_session_id) REFERENCES academic_sessions (id),
    CONSTRAINT fk_invoices_created_by FOREIGN KEY (created_by_user_id)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invoice_lines (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_id             BIGINT         NOT NULL,
    fee_head_id            BIGINT         NOT NULL,
    description            VARCHAR(200)   NOT NULL,
    gross_amount           DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    discount_amount        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    fine_amount            DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    net_payable_amount     DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    allocated_paid_amount  DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    balance_due_amount     DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    created_at             TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_invlines_invoice (invoice_id),
    KEY idx_invlines_head (fee_head_id),
    CONSTRAINT fk_invlines_invoice FOREIGN KEY (invoice_id)  REFERENCES invoices (id),
    CONSTRAINT fk_invlines_head    FOREIGN KEY (fee_head_id) REFERENCES fee_heads (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    id                        BIGINT         NOT NULL AUTO_INCREMENT,
    receipt_no                VARCHAR(40)    NOT NULL,
    idempotency_key           VARCHAR(100)   NOT NULL,
    student_id                BIGINT         NOT NULL,
    enrollment_id             BIGINT         NOT NULL,
    gateway_code              VARCHAR(30)    NOT NULL,
    gateway_transaction_id    VARCHAR(120)   NULL,
    bank_or_cheque_ref        VARCHAR(120)   NULL,
    currency                  CHAR(3)        NOT NULL DEFAULT 'BDT',
    amount                    DECIMAL(12,2)  NOT NULL,
    status                    VARCHAR(30)    NOT NULL DEFAULT 'INITIATED',
    paid_at                   TIMESTAMP(6)   NULL,
    collected_by_user_id      BIGINT         NULL,
    verification_token        VARCHAR(80)    NOT NULL,
    reversal_reason           VARCHAR(500)   NULL,
    reversed_by_user_id       BIGINT         NULL,
    reversed_at               TIMESTAMP(6)   NULL,
    raw_gateway_payload_json  JSON           NULL,
    created_at                TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_receipt_no (receipt_no),
    UNIQUE KEY uk_payments_idempotency (idempotency_key),
    UNIQUE KEY uk_payments_verification_token (verification_token),
    KEY idx_payments_student_status (student_id, status),
    KEY idx_payments_gateway_tx (gateway_code, gateway_transaction_id),
    KEY idx_payments_status_created (status, created_at),
    CONSTRAINT fk_payments_student      FOREIGN KEY (student_id)           REFERENCES students (id),
    CONSTRAINT fk_payments_enrollment   FOREIGN KEY (enrollment_id)        REFERENCES enrollments (id),
    CONSTRAINT fk_payments_collected_by FOREIGN KEY (collected_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_payments_reversed_by  FOREIGN KEY (reversed_by_user_id)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_allocations (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    payment_id        BIGINT         NOT NULL,
    invoice_id        BIGINT         NOT NULL,
    invoice_line_id   BIGINT         NOT NULL,
    allocated_amount  DECIMAL(12,2)  NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_payalloc_payment (payment_id),
    KEY idx_payalloc_invoice (invoice_id, status),
    KEY idx_payalloc_line (invoice_line_id, status),
    CONSTRAINT fk_payalloc_payment FOREIGN KEY (payment_id)      REFERENCES payments (id),
    CONSTRAINT fk_payalloc_invoice FOREIGN KEY (invoice_id)      REFERENCES invoices (id),
    CONSTRAINT fk_payalloc_line    FOREIGN KEY (invoice_line_id) REFERENCES invoice_lines (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_wallets (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    student_id      BIGINT         NOT NULL,
    currency        CHAR(3)        NOT NULL DEFAULT 'BDT',
    balance_amount  DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    updated_at      TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_wallet (student_id, currency),
    CONSTRAINT fk_wallet_student  FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refunds (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    refund_no            VARCHAR(40)    NOT NULL,
    idempotency_key      VARCHAR(100)   NOT NULL,
    student_id           BIGINT         NOT NULL,
    payment_id           BIGINT         NULL,
    refund_method        VARCHAR(30)    NOT NULL,
    currency             CHAR(3)        NOT NULL DEFAULT 'BDT',
    amount               DECIMAL(12,2)  NOT NULL,
    reason               VARCHAR(500)   NOT NULL,
    approved_by_user_id  BIGINT         NOT NULL,
    created_at           TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refunds_no (refund_no),
    UNIQUE KEY uk_refunds_idempotency (idempotency_key),
    KEY idx_refunds_student (student_id),
    CONSTRAINT fk_refunds_student     FOREIGN KEY (student_id)          REFERENCES students (id),
    CONSTRAINT fk_refunds_payment     FOREIGN KEY (payment_id)          REFERENCES payments (id),
    CONSTRAINT fk_refunds_approved_by FOREIGN KEY (approved_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fee_ledger_entries (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    student_id             BIGINT         NOT NULL,
    enrollment_id          BIGINT         NOT NULL,
    entry_type             VARCHAR(10)    NOT NULL,
    event_category         VARCHAR(40)    NOT NULL,
    reference_type         VARCHAR(40)    NOT NULL,
    reference_id           BIGINT         NOT NULL,
    currency               CHAR(3)        NOT NULL DEFAULT 'BDT',
    amount                 DECIMAL(12,2)  NOT NULL,
    running_balance_after  DECIMAL(12,2)  NOT NULL,
    narration              VARCHAR(500)   NOT NULL,
    actor_user_id          BIGINT         NULL,
    created_at             TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ledger_student_created (student_id, id),
    KEY idx_ledger_enrollment (enrollment_id, id),
    KEY idx_ledger_ref (reference_type, reference_id),
    CONSTRAINT fk_ledger_student    FOREIGN KEY (student_id)    REFERENCES students (id),
    CONSTRAINT fk_ledger_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id),
    CONSTRAINT fk_ledger_actor      FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO fee_heads (code, name, category, allocation_priority, is_refundable) VALUES
    ('FINE',      'Late Payment Fine', 'FINE',    10, FALSE),
    ('ARREARS',   'Previous Arrears',  'ARREARS', 20, FALSE),
    ('TUITION',   'Monthly Tuition',   'TUITION', 30, FALSE),
    ('ADMISSION', 'Admission Fee',     'OTHER',   40, FALSE),
    ('EXAM',      'Examination Fee',   'OTHER',   50, FALSE)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('fees.currency',                    'BDT'),
    ('fees.allow_partial_payment',       'true'),
    ('fees.invoice_number_prefix',       'INV-{YEAR}-{SEQ:6}'),
    ('fees.receipt_number_prefix',       'RCP-{YEAR}-{SEQ:6}'),
    ('fees.waiver_auto_approve_limit',   '0.00'),
    ('fees.due_reminder_days_before',    '3'),
    ('fees.overdue_reminder_days_after', '1,7,15'),
    ('fees.online_gateways_enabled',     '["SSLCOMMERZ","BKASH","SANDBOX"]')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO permissions (code, description) VALUES
    ('fee.read',            'View fee heads and fee structures'),
    ('fee.config.write',    'Create and update fee heads and structures'),
    ('fee.waiver.request',  'Propose student fee discount or scholarship'),
    ('fee.waiver.approve',  'Approve student fee waivers above threshold'),
    ('fee.invoice.read',    'View student invoices and line breakdowns'),
    ('fee.invoice.write',   'Generate single or batch invoices'),
    ('fee.invoice.cancel',  'Cancel an unpaid invoice with reason'),
    ('fee.payment.read',    'View payments and download official receipt PDFs'),
    ('fee.payment.collect', 'Record offline cash/bank/cheque payments at counter'),
    ('fee.payment.online',  'Initiate online payment checkout for student invoices'),
    ('fee.payment.reverse', 'Reverse a posted payment with mandatory audit reason'),
    ('fee.refund.approve',  'Issue wallet or external refund'),
    ('fee.ledger.read',     'View immutable student fee ledger statement'),
    ('fee.report.read',     'View daily collection, reconciliation, and defaulters reports')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'fee.read', 'fee.config.write', 'fee.waiver.request', 'fee.waiver.approve',
    'fee.invoice.read', 'fee.invoice.write', 'fee.invoice.cancel',
    'fee.payment.read', 'fee.payment.collect', 'fee.payment.online',
    'fee.payment.reverse', 'fee.refund.approve', 'fee.ledger.read', 'fee.report.read'
)
WHERE r.code = 'SCHOOL_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'fee.read', 'fee.config.write', 'fee.waiver.request',
    'fee.invoice.read', 'fee.invoice.write', 'fee.invoice.cancel',
    'fee.payment.read', 'fee.payment.collect', 'fee.payment.online',
    'fee.ledger.read', 'fee.report.read'
)
WHERE r.code = 'ACCOUNTANT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('fee.read', 'fee.invoice.read', 'fee.payment.read')
WHERE r.code = 'REGISTRAR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'fee.read', 'fee.invoice.read', 'fee.payment.read', 'fee.payment.online', 'fee.ledger.read'
)
WHERE r.code IN ('STUDENT', 'GUARDIAN');
