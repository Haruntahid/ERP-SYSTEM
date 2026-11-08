# Progress

Update this file after every slice. It is the memory between agent sessions.

- **Master Roadmap**: See [`docs/roadmap/README.md`](./roadmap/README.md) for the complete status table, dependency graph, and build order (`F-01`..`F-10`, `M-01`..`M-06`).
- **Open Business Questions**: See [`docs/roadmap/OPEN-QUESTIONS.md`](./roadmap/OPEN-QUESTIONS.md) (`Q-001`..`Q-025`).
- **Roadmap Changelog**: See [`docs/roadmap/CHANGELOG.md`](./roadmap/CHANGELOG.md).

## Current state
- **Full Phase 1 (`M-01`..`M-06`) and Foundation (`F-01`, `F-04`..`F-10`) are `DONE`** with both backend APIs, Flyway tenant migrations (`V1`..`V17`), two-school tenant isolation tests, and full functional React frontend pages (`LoginPage`, `DashboardPage`, `AcademicPage`, `StaffPage`, `StudentsPage`, `AttendancePage`, `FeesPage`, `NoticesPage`, `OutboxDashboardPage`, `PlatformTenantsPage`).
- Backend builds and passes unit and multi-tenant isolation tests (`mvn test`), exposing Swagger UI at `http://localhost:8080/swagger-ui.html`.
- MySQL 8.4 (`school-erp-mysql`, port `3307:3306`), RabbitMQ 3.13 (`school-erp-rabbitmq`, ports `5672`/`15672`), and Redis 7.4 (`school-erp-redis`, port `6379:6379`) are running via `docker compose`.
- Frontend builds cleanly with strict TypeScript (`npm --prefix frontend run build`) with `en` and `bn` i18n keys.

## Done
- **Tenancy & Security Core**: `TenantFilter`, `TenantContext`, `TenantRoutingDataSource`, `TenantRegistry`, provisioning, startup migration (`V1`..`V17`), JWT access tokens (`tenant` + `jti` claims), rotating refresh tokens with reuse detection, scoped RBAC (`@perm.has` / `@perm.hasScoped`), `@RequiresFeature`, and `AuditService`.
- **Foundation Modules (`DONE`)**:
  - `F-08`: Redis caching, distributed Lua rate limiter, account lockout (`423 Locked`), JWT `jti` revocation blacklist, `TenantDistributedLock`, and `SecurityAndCacheSettingsPanel`.
  - `F-05`: RabbitMQ 3.13 topology, `OutboxEventWriter`, `OutboxPublisher`, `TenantAwareMessageListener`, `OutboxMessagingController`, and `OutboxDashboardPage`.
  - `F-06`: Tenant-isolated file storage (`S3StorageProvider` / `LocalFsStorageProvider`), magic-byte validator, ClamAV scanner, quota enforcement, and `StorageQuotaWidget`.
  - `F-04`: Multi-channel notification engine (`V7__notification_engine.sql`), `SmsSegmentCalculator`, `NotificationTemplateRenderer`, `NotificationService`, and `NotificationController`.
  - `F-01`: Account recovery & security hardening (`V8__account_recovery_and_sessions.sql`), password reset, forced password change, and session management.
  - `F-07`: Branded document & PDF generation engine (`V10__document_generation.sql`).
  - `F-09`: Multi-tenant distributed scheduler & job history (`V11__tenant_scheduler.sql`).
  - `F-10`: Bulk CSV/Excel import & export framework (`V12__import_export_jobs.sql`).
- **Phase 1 Domain Modules (`M-01`..`M-06` — `DONE` Backend + Full Functional Frontend)**:
  - **`M-01` Institution & Academic Structure (`com.schoolerp.academic`)**:
    - Migration `V9__academic_structure.sql`; backend CRUD for campuses, shifts, rooms, academic sessions (with atomic current-session activation), departments, classes, sections, subjects, class-subject mappings, grading scales, and calendar events; `AcademicStructureTenantIsolationTest`; frontend `AcademicPage.tsx`, `AcademicSessionsPage.tsx`, `AcademicStructurePage.tsx`.
  - **`M-02` Staff, Teachers & Subject-Section Assignments (`com.schoolerp.staff`)**:
    - Migration `V13__staff_and_teachers.sql`; backend CRUD for designations, staff profiles, status transitions (`ACTIVE`, `ON_LEAVE`, `SUSPENDED`, `RESIGNED`, `TERMINATED`), and teacher section/subject assignments; `StaffTenantIsolationTest`; frontend `StaffPage.tsx`.
  - **`M-03` Students, Guardians, Admission & Enrollment (`com.schoolerp.students`)**:
    - Migration `V14__students_guardians_admission_enrollment.sql`; atomic student code generator (`student_code_sequences`), student + guardian registration, admission application workflow (`SUBMITTED -> UNDER_REVIEW -> APPROVED -> ENROLLED`), per-session enrollment history, and batch session promotion wizard; `StudentTenantIsolationTest`; frontend `StudentsPage.tsx`.
  - **`M-04` Student & Staff Attendance with Leave & Alerts (`com.schoolerp.attendance`)**:
    - Migration `V15__attendance_and_leave.sql`; section daily roll-call sheet (`GET /api/v1/attendance/students/sheet`), one-tap batch submission, configurable edit window + mandatory amendment reason, admin session unlock, staff daily attendance sheet, leave application & approval workflow, and automatic `absence_alert_queue` + transactional outbox event emission; `AttendanceTenantIsolationTest`; frontend `AttendancePage.tsx`.
  - **`M-05` Fees, Invoices, Immutable Ledger & Payments (`com.schoolerp.fees`)**:
    - Migration `V17__fees_invoices_payments_ledger.sql`; fee heads, structures, student discount/waivers, single & batch class invoice generation, counter POS payment collection with invoice line allocation and advance wallet overflow, payment reversal (`CREDIT` -> `DEBIT`), and append-only `fee_ledger_entries` with running balance; `FeeTenantIsolationTest`; frontend `FeesPage.tsx`.
  - **`M-06` Notice Board & Targeted School Communication (`com.schoolerp.notices`)**:
    - Migration `V16__notices_and_communication.sql`; notice creation with audience targeting (`WHOLE_SCHOOL`, `CLASS`, `SECTION`, `ROLE`), publish workflow with transactional outbox broadcast (`notice.published`), archive, published notice feed, and per-user read receipts; `NoticeTenantIsolationTest`; frontend `NoticesPage.tsx`.

## Next (Remaining P1 Hardening Items)
1. `F-03` Secrets & Key Management with Envelope Rotation (`APPROVED`)
2. `F-02` Platform Operator Authentication & TOTP MFA (`APPROVED`)

## Decisions log
- ADR 0001: Database per school.
- ADR 0002: RabbitMQ with Transactional Outbox for Asynchronous Messaging.
- Mapped Docker Compose MySQL to `3307:3306` locally because host port `3306` is occupied.
- Added `@Primary JdbcTemplate tenantJdbcTemplate` alongside `masterJdbcTemplate` so tenant-scoped JDBC services (`AuditService`) never accidentally bind to the master catalog database.
