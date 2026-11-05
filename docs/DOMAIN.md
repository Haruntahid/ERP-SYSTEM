# Domain Model (initial)

Everything below lives inside each school database unless stated otherwise.

## Platform (master database)
- Tenant: slug, name, status, plan, jdbc_url, credentials, feature flags.

## Identity and access (in tenant schema, already migrated V1–V6)
- User, Role, Permission, RolePermission, UserRole (with scope_type and scope_id), RefreshToken.
- Permission code format: `module.action` (for example `student.read`, `marks.write`, `result.publish`, `security.unlock`, `cache.evict`, `messaging.read`, `messaging.replay`, `file.upload`, `file.read`, `file.delete`, `file.admin`).
- Scope: ALL, DEPARTMENT, CLASS, SECTION, OWN.
- AppSetting, AuditLog.
- OutboxEvent (`outbox_events`), ProcessedEvent (`processed_events` idempotent consumer inbox).
- StoredFile (`stored_files` tenant-isolated file metadata with `<tenantSlug>/<category>/<yyyy>/<mm>/<uuid>-<sanitizedFilename>` keys, SHA-256 checksums, magic-byte + ClamAV scan status, and soft delete).
- Redis tenant-prefixed ephemeral state (`<tenantSlug>:settings:all`, `<tenantSlug>:userPermissions:<userId>`, `<tenantSlug>:ratelimit:*`, `<tenantSlug>:lockout:*`, `<tenantSlug>:jwt:blacklist:*`, `lock:<tenantSlug>:*`).

## Academic structure (to build)
Campus -> Program/Department -> Batch/Class -> Section -> Subject/Course.
AcademicSession (year or semester) is a first class entity. Fees, attendance and results always belong to a session.

## People (to build)
- Student is created once. Enrollment is per session (student, session, class, section, roll). Never merge them.
- Guardian linked to students.
- Staff/Teacher linked to subjects and sections.

## Operations (to build)
- Attendance: per session, per section/subject, per day, with audit on edits.
- Fees: FeeHead, FeeStructure, Invoice, InvoiceLine, Payment, Waiver, Fine, Refund. Never a single `paid` flag. Payments are idempotent.
- Exams: Exam, ExamSchedule, Mark, GradeScale (configurable), Result with state machine (draft, moderated, published, locked).
- Notice and Notification with channel (SMS, email, push) and delivery log.

## Rules to remember
- No hard delete of academic or financial records.
- Published results are immutable. Changes need a re-evaluation record and an audit entry.
- Grading scale and session type are per school settings.
