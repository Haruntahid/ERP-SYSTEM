# Open Business & Product Questions

Every ambiguity across the Foundation (`F-01`..`F-10`) and Phase 1 (`M-01`..`M-06`) plans is cataloged here rather than guessed in code. Each plan marks dependent rules with `[ASSUMPTION -> Q-xxx]` using the **Recommended Default** below until you confirm or override the decision.

To answer or override any question, tell the agent (for example: *"Accept recommended defaults for Q-001 to Q-010, except Q-004 use option B"* or run `/change-plan`).

---

## Summary Table (Top 10 Blocking Questions Highlighted First)

| ID | Topic | Affects | Status | Recommended Default |
|---|---|---|---|---|
| **Q-001** | Account lockout threshold & duration | `F-01`, `F-08` | `RESOLVED` | 5 failed attempts within 15 min -> 15 min lock (configurable per school) |
| **Q-002** | Password policy & expiry rules | `F-01` | `RESOLVED` | Min 8 chars (upper, lower, digit), no forced periodic expiry |
| **Q-003** | SMS gateway providers at launch (Bangladesh / Global) | `F-04`, `M-04`, `M-05`, `M-06` | `RESOLVED` | Log/Mock adapter in dev + SSL Wireless & BulkSMSBD HTTP adapters |
| **Q-004** | Monthly SMS quota & overage handling per school plan | `F-04`, `M-06` | `RESOLVED` | Hard cap per plan (`BASIC`: 500, `PRO`: 5000, `ENTERPRISE`: 25000/mo) with top-up counter |
| **Q-005** | File upload max size & allowed MIME types | `F-06`, `M-02`, `M-03`, `M-06` | `RESOLVED` | 10 MB per file; `image/jpeg`, `image/png`, `image/webp`, `application/pdf` |
| **Q-006** | Academic hierarchy levels (School vs University terminology) | `M-01` | `RESOLVED` | Unified 5-level hierarchy (`Campus -> Department/Program -> Class/Batch -> Section -> Subject`) with optional levels |
| **Q-007** | Student ID auto-generation format & uniqueness | `M-03` | `RESOLVED` | Pattern `{YEAR}{CLASS_CODE}{SEQ:4}` (e.g., `2026090042`), permanent across sessions |
| **Q-008** | Attendance mode (Daily Section vs Per-Period Subject) & edit window | `M-04` | `RESOLVED` | Per-school setting (`DAILY` default for K-12, `SUBJECT_PERIOD` for college/university); 48-hour teacher edit window |
| **Q-009** | Supported currencies & online payment gateways at launch | `M-05` | `RESOLVED` | Default `BDT` (`DECIMAL(12,2)`), offline cash/bank/cheque + SSLCommerz & bKash tokenized checkout |
| **Q-010** | Partial fee payments, allocation order & late fine calculation | `M-05` | `RESOLVED` | Allow partial payments (configurable per school); allocate FIFO (Fines -> Arrears -> Tuition); flat or daily % fine |
| **Q-011** | Concurrent active device sessions per user | `F-01` | `RESOLVED` | Max 5 active refresh token sessions per user (oldest evicted on 6th login) |
| **Q-012** | Platform operator roles & MFA mechanism | `F-02` | `RESOLVED` | `SUPER_ADMIN`, `SUPPORT_OPERATOR`, `BILLING_OPERATOR` with mandatory TOTP (RFC 6238) MFA |
| **Q-013** | Secret manager backend for production | `F-03` | `RESOLVED` | Environment variable keyring in v1 (`v1:<base64>,v2:<base64>`) + Vault/AWS KMS interface |
| **Q-014** | Virus/malware scanning on file uploads | `F-06` | `RESOLVED` | ClamAV TCP daemon (`clamd`) in prod, no-op pass-through scanner in dev |
| **Q-015** | PDF verification QR code retention & public verification URL | `F-07`, `M-05` | `RESOLVED` | Public signed verification URL `/verify/doc/{tenantSlug}/{verificationCode}` valid indefinitely |
| **Q-016** | Tenant scheduler timezone handling | `F-09`, `M-04`, `M-05` | `RESOLVED` | Evaluate cron/daily triggers in each school's `school.timezone` (`Asia/Dhaka` default) |
| **Q-017** | Bulk import row limit & partial-commit vs all-or-nothing | `F-10`, `M-02`, `M-03` | `RESOLVED` | Max 5,000 rows per file; two-step dry-run then commit valid rows + downloadable error CSV |
| **Q-018** | Promotion / progression rules between academic sessions | `M-01`, `M-03` | `RESOLVED` | Explicit batch promotion wizard creating new `Enrollment` rows in target session; manual override for repeaters |
| **Q-019** | Staff user account creation on onboarding | `M-02` | `RESOLVED` | Auto-create `users` row + send welcome password-set link via `F-04` when staff record is created |
| **Q-020** | Guardian portal login identity (Email vs Mobile Number) | `M-03`, `F-01` | `RESOLVED` | Support both email and phone number (`users.phone`) for guardian/student login |
| **Q-021** | Admission workflow stages & application fee | `M-03`, `M-05` | `RESOLVED` | `DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED/REJECTED -> ENROLLED`; optional admission fee invoice |
| **Q-022** | Guardian absence SMS trigger timing | `M-04`, `F-04`, `F-09` | `RESOLVED` | Configurable delay (default 30 min after attendance submission or scheduled cutoff time) so teachers can fix typos first |
| **Q-023** | Fee waiver & scholarship approval workflow | `M-05` | `RESOLVED` | `ACCOUNTANT` can propose waiver; requires `SCHOOL_ADMIN` approval if waiver > configurable threshold |
| **Q-024** | Fee refund policy (Credit Balance vs Cash Refund) | `M-05` | `RESOLVED` | Support both `WALLET_CREDIT` (applied to next invoice) and `EXTERNAL_REFUND` (with reversal ledger entry) |
| **Q-025** | Notice approval & emergency SMS broadcast permissions | `M-06` | `RESOLVED` | Teachers can publish `IN_APP` notices to their assigned sections; SMS broadcasts require `SCHOOL_ADMIN` |

---

## Detailed Questions, Options & Recommendations

### Q-001: Account Lockout Threshold & Duration (`F-01`, `F-08`)
- **Question**: How many consecutive failed login attempts should lock a user account, and how is it unlocked?
- **Options**:
  - **A (Recommended)**: Lock after 5 failed attempts within 15 minutes for a 15-minute cooldown (configurable per school via `security.lockout.max_attempts` and `security.lockout.duration_minutes`), with immediate manual unlock available to `SCHOOL_ADMIN`.
  - **B**: Lock permanently after 5 failed attempts until a `SCHOOL_ADMIN` unlocks or the user completes email password reset.
  - **C**: Rely solely on IP rate limiting without account-level lockout.
- **Recommended Default**: **Option A** — prevents credential stuffing across distributed IPs while avoiding support bottlenecks from forgotten passwords.

### Q-002: Password Policy & Expiry Rules (`F-01`)
- **Question**: What password complexity and expiration policy should schools enforce?
- **Options**:
  - **A (Recommended)**: Minimum 8 characters, at least 1 uppercase, 1 lowercase, and 1 digit; check against last 3 password hashes; no forced periodic expiration (NIST SP 800-63B recommendation). Configurable per school.
  - **B**: Strict enterprise policy (12 chars, special char required, forced rotation every 90 days).
- **Recommended Default**: **Option A**.

### Q-003: SMS Gateway Providers at Launch (`F-04`, `M-04`, `M-05`, `M-06`)
- **Question**: Which SMS gateways must be supported out-of-the-box, and do schools use a shared platform sender ID or their own gateway credentials?
- **Options**:
  - **A (Recommended)**: Support both platform-managed credits (shared non-masking/masking sender ID billed per plan quota) and school-owned credentials (encrypted in `app_settings`), with built-in Strategy adapters for **SSL Wireless** (Bangladesh), **Twilio** (Global), and **Console/Database Mock** (dev/test).
  - **B**: Only platform-managed single gateway.
  - **C**: Only school-provided gateway credentials.
- **Recommended Default**: **Option A**.

### Q-004: Monthly SMS Quota & Overage Handling (`F-04`, `M-06`)
- **Question**: When a school reaches its monthly SMS quota, what happens to outbound SMS messages?
- **Options**:
  - **A (Recommended)**: Block **bulk/promotional** SMS (`M-06` notices) with `422 Quota Exceeded`, while allowing **critical transactional** SMS (`F-01` password reset/OTP) up to a 10% grace buffer; platform operator can add top-up credits.
  - **B**: Hard block all SMS immediately.
  - **C**: Allow unlimited overage and bill post-paid.
- **Recommended Default**: **Option A**.

### Q-005: File Upload Size Limits & Allowed MIME Types (`F-06`, `M-02`, `M-03`, `M-06`)
- **Question**: What file types and sizes should be permitted for student/staff documents and notice attachments?
- **Options**:
  - **A (Recommended)**: Max 10 MB per file (2 MB for profile photos); allowed MIME types validated by both extension and magic bytes: `image/jpeg`, `image/png`, `image/webp`, `application/pdf`, and `text/csv` / `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` for `F-10` imports.
  - **B**: Allow arbitrary office documents (`.docx`, `.pptx`, `.zip`) up to 50 MB.
- **Recommended Default**: **Option A**.

### Q-006: Academic Hierarchy Levels (`M-01`)
- **Question**: Since the ERP serves both K-12 schools and universities/colleges, how should the academic hierarchy be structured?
- **Options**:
  - **A (Recommended)**: Unified hierarchy `Campus -> Department/Program -> Class/Batch -> Section -> Subject/Course` where `Campus` and `Department/Program` have a seeded `"Default"` entry for simple K-12 schools (hidden in UI when `academic.multi_campus=false` / `academic.use_departments=false`), while universities enable all 5 levels and `SEMESTER` sessions.
  - **B**: Separate tables and APIs for K-12 vs University.
- **Recommended Default**: **Option A**.

### Q-007: Student ID Format & Lifecycle (`M-03`)
- **Question**: How is a student's permanent ID (`student_code`) formatted, and does it change across academic sessions?
- **Options**:
  - **A (Recommended)**: `student_code` is generated once upon admission using a per-school template setting `student.id_template` (default `{YEAR}{CLASS_CODE}{SEQ:4}`, e.g. `2026090042`) and never changes; session-specific `roll_number` lives on the `enrollments` table and can change each year/semester.
  - **B**: Manual entry only.
- **Recommended Default**: **Option A**.

### Q-008: Attendance Mode & Edit Lock Window (`M-04`)
- **Question**: Is student attendance recorded once per day per section, or per subject/period, and how long can teachers edit submitted attendance?
- **Options**:
  - **A (Recommended)**: Controlled by school setting `attendance.mode` (`DAILY_SECTION` default for schools, `SUBJECT_PERIOD` for colleges/universities). Teachers can edit attendance within `attendance.edit_window_hours` (default `48` hours) with every change written to `audit_log`; after 48 hours, only `SCHOOL_ADMIN` or `REGISTRAR` can unlock/amend with a mandatory reason.
  - **B**: Daily section attendance only, locked immediately upon submission.
- **Recommended Default**: **Option A**.

### Q-009: Supported Currencies & Payment Gateways at Launch (`M-05`)
- **Question**: Which currencies and online payment gateways are required for Phase 1 launch?
- **Options**:
  - **A (Recommended)**: Currency stored per school in `fees.currency` (default `BDT`, ISO-4217 3-letter code) and on every invoice/payment row (`DECIMAL(12,2)`). Launch with **Offline Cashier Recording** (Cash, Bank Deposit, Cheque/DD) plus online Strategy adapters for **SSLCommerz**, **bKash Tokenized Checkout**, and a **Sandbox/Mock Gateway** for testing.
  - **B**: Offline payments only in Phase 1; online gateways in Phase 2.
- **Recommended Default**: **Option A**.

### Q-010: Partial Payments, Allocation Order & Late Fines (`M-05`)
- **Question**: Can guardians pay an invoice in partial installments, and how are partial payments allocated across invoice lines?
- **Options**:
  - **A (Recommended)**: Controlled by `fees.allow_partial_payment` (default `true`). Partial payments are allocated in deterministic order: `FINE -> ARREARS -> TUITION -> OTHER` (configurable). Late fines are calculated by a Strategy (`NONE`, `FLAT_ONCE`, `DAILY_FIXED`, `PERCENTAGE_CAP`) configured per `FeeStructure`.
  - **B**: Full invoice payment only; no partial payments.
- **Recommended Default**: **Option A**.

### Q-011 .. Q-025 Summary Defaults
- **Q-011 (`F-01`)**: Max 5 concurrent refresh token sessions per user; user and admin can view active sessions (IP, User-Agent, last used) and revoke individual or all sessions.
- **Q-012 (`F-02`)**: Separate `platform_operators` table in `school_master` with roles `SUPER_ADMIN`, `SUPPORT_OPERATOR`, `BILLING_OPERATOR` and TOTP MFA. Static `X-Platform-Key` disabled in `prod` once `F-02` lands.
- **Q-013 (`F-03`)**: Keyring format (`v1:<base64>,v2:<base64>`) with active key version in `app.crypto.active-key-id`; background re-encryption command for `tenants.db_password_enc`.
- **Q-014 (`F-06`)**: Pluggable `MalwareScanner` interface (`NoOpMalwareScanner` in local/dev, `ClamAvMalwareScanner` in prod).
- **Q-015 (`F-07`, `M-05`)**: Every generated official PDF (receipt, ID card, certificate) embeds a tamper-evident HMAC/UUID verification token verifiable at `/api/v1/public/verify/{tenantSlug}/{token}`.
- **Q-016 (`F-09`)**: Scheduler ticks every minute in UTC, evaluates each active tenant's schedule against that school's `school.timezone` setting, and uses a Redis/DB distributed lock so only one app instance runs a tenant's job.
- **Q-017 (`F-10`)**: Two-phase import: `POST /imports/dry-run` validates and produces a row-by-row preview; `POST /imports/{id}/commit` imports all valid rows asynchronously and generates an error CSV for rejected rows.
- **Q-018 (`M-01`, `M-03`)**: Session promotion wizard copies eligible students from source `AcademicSession` + `Class` into target `AcademicSession` + next `Class` as new `Enrollment` records, preserving historical enrollments intact.
- **Q-019 (`M-02`)**: Creating a staff/teacher record optionally creates a linked `users` account with the chosen role (`TEACHER`, `ACCOUNTANT`, `REGISTRAR`) and dispatches a password-setup invite via `F-04`.
- **Q-020 (`M-03`, `F-01`)**: Add optional `phone` column (`uk_users_phone`) to `users` so guardians and students can log in with either email or mobile phone number.
- **Q-021 (`M-03`, `M-05`)**: Admission application state machine `DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED / WAITLISTED / REJECTED -> ENROLLED`; converting to `ENROLLED` atomically creates `Student`, `Guardian`, and initial `Enrollment`.
- **Q-022 (`M-04`, `F-04`, `F-09`)**: Absence notifications are queued with a configurable grace delay (`attendance.notify_delay_minutes`, default `30`) so accidental "Absent" marks corrected by the teacher within 30 minutes do not send false-alarm SMS to parents.
- **Q-023 (`M-05`)**: Waivers/scholarships up to `fees.waiver_auto_approve_limit` (`0.00` default) require `SCHOOL_ADMIN` approval before reducing an invoice.
- **Q-024 (`M-05`)**: Overpayments or refunds create an immutable `Refund` + credit ledger entry, either credited to the student's `StudentWallet` for future invoices or paid out externally.
- **Q-025 (`M-06`)**: Role-scoped notice publishing: `SCHOOL_ADMIN` can target whole school + SMS/Email; `TEACHER` can target only their assigned `CLASS`/`SECTION` (`IN_APP` only unless approved).
