# Progress

Update this file after every slice. It is the memory between agent sessions.

- **Master Roadmap**: See [`docs/roadmap/README.md`](./roadmap/README.md) for the complete status table, dependency graph, and build order (`F-01`..`F-10`, `M-01`..`M-06`).
- **Open Business Questions**: See [`docs/roadmap/OPEN-QUESTIONS.md`](./roadmap/OPEN-QUESTIONS.md) (`Q-001`..`Q-025`).
- **Roadmap Changelog**: See [`docs/roadmap/CHANGELOG.md`](./roadmap/CHANGELOG.md).

## Current state
- Backend builds, passes all 26 unit and multi-tenant isolation tests (`mvn verify`), and exposes Swagger UI at `http://localhost:8080/swagger-ui.html`.
- MySQL 8.4 (`school-erp-mysql`, port `3307:3306`), RabbitMQ 3.13 (`school-erp-rabbitmq`, ports `5672`/`15672`), and Redis 7.4 (`school-erp-redis`, port `6379:6379`) are running via `docker compose`.
- Frontend builds cleanly with strict TypeScript (`npm --prefix frontend run build`).
- All 25 open questions (`Q-001`..`Q-025`) are `RESOLVED` with recommended defaults, all roadmap plans are `APPROVED`, and `F-08` and `F-05` are `DONE`.

## Done
- Database per school tenancy core: TenantFilter, TenantContext, TenantRoutingDataSource, TenantRegistry, provisioning, startup migration.
- Platform API: create, list, suspend/activate schools, update feature flags (static platform key), plus initial `SCHOOL_ADMIN` user seeding during provisioning.
- Tenant config endpoint (`/api/v1/tenant/config`) and settings API (`/api/v1/settings`) protected by RBAC (`settings.read`, `settings.write`) and audit logging.
- Tenant schema V1 (users, roles, permissions, user_roles, app_settings, audit_log), V2 (default roles and settings), V3 (`refresh_tokens` table, baseline permissions, and role grants), V4 (`V4__redis_cache_admin_permissions.sql`), and V5 (`V5__outbox_and_inbox_events.sql`).
- OpenAPI 3 / Swagger UI (`OpenApiConfig`): `BearerAuth` (JWT), `PlatformKeyAuth` (`X-Platform-Key`), and automatic `X-Tenant-ID` header on tenant endpoints.
- Authentication (`com.schoolerp.iam`): BCrypt password hashing, HMAC-SHA256 JWT access tokens with `tenant` and `jti` claims, rotating refresh tokens with reuse detection, login rate limiting (`RateLimitFilter`), and cross-tenant token rejection (`JwtAuthenticationFilter`).
- Scoped RBAC (`PermissionChecker` `@perm` with `ALL`, `DEPARTMENT`, `CLASS`, `SECTION`, `OWN`), feature flag enforcement (`@RequiresFeature` + `FeatureFlagInterceptor`), paginated `/api/v1/users` and `/api/v1/roles` endpoints, and append-only `AuditService`.
- Multi-tenant security isolation integration tests (`TenantSecurityIsolationTest`, `RedisTenantIsolationTest`, `MessagingTenantIsolationTest`).
- React shell: tenant resolution, theming from settings, feature-gated navigation, platform console page.
- Roadmap documentation system (`docs/roadmap/`) and workflows (`/roadmap`, `/next`, `/change-plan`).
- **`F-08` Redis Caching, Distributed Rate Limiting & Token Blacklist (`DONE`)**:
  - `TenantPrefixedCacheKeyGenerator` & `TenantAwareRedisTemplate` enforcing `<tenantSlug>:<namespace>:<key>` prefixes with non-blocking `SCAN`+`UNLINK` eviction and resilient in-memory fallback.
  - Cached school settings (`<tenantSlug>:settings:all`) and RBAC permissions (`<tenantSlug>:userPermissions:<userId>`) with `AFTER_COMMIT` eviction listeners and `POST /api/v1/cache/evict`.
  - `RedisLuaRateLimiter` & `ResilientRateLimiter` with tiered policies (`AUTH_LOGIN`, `PUBLIC_VERIFY`, `SENSITIVE_MUTATION`, `TENANT_API_GENERAL`) and `Retry-After` headers.
  - `AccountLockoutService` (`423 Locked` after 5 failed attempts in 15 min), `JwtRevocationService` (`jti` blacklist on logout + per-user revocation watermark), `TenantDistributedLock`, `/api/v1/security/lockouts/{userId}`, `/api/v1/security/users/{userId}/revoke-sessions`, `RedisTenantIsolationTest`, and frontend `SecurityAndCacheSettingsPanel` with `en`/`bn` i18n keys.
- **`F-05` Asynchronous Messaging, RabbitMQ Broker, and Transactional Outbox (`DONE`)**:
  - `V5__outbox_and_inbox_events.sql` per-tenant `outbox_events` and `processed_events` tables, outbox retention/retry settings, and `messaging.read`/`messaging.replay` permissions.
  - Declarative RabbitMQ 3.13 topology (`RabbitMqTopologyConfig`: `erp.events.topic`, `erp.events.dlx`, priority/domain queues `erp.notifications.high`, `erp.notifications.bulk`, `erp.documents.generate`, `erp.imports.process`, retry queues `5s`/`30s`/`5m`, and terminal `erp.dlq`).
  - `OutboxEventWriter` (atomic event persistence inside caller's `@Transactional` boundary) and `OutboxPublisher` (scheduled tenant relay using `TenantDistributedLock` and `FOR UPDATE SKIP LOCKED`, plus daily retention purge).
  - `TenantAwareMessageListener` & `DomainEventConsumer<T>` enforcing `X-Tenant-Slug` header/envelope validation, `try/finally` `TenantContext` binding, idempotent `processed_events` deduplication, and retry/DLQ routing.
  - Admin Outbox Inspector & Replay API (`OutboxMessagingController` `/api/v1/messaging/outbox`, `/replay`, `/replay-batch`, `/stats`) with `JsonPayloadMasker`, `OUTBOX_EVENT_REPLAYED` audit trail, `MessagingTenantIsolationTest`, and React `OutboxDashboardPage` + `OutboxEventDrawer` (`/messaging`).

## Next (Recommended Build Order — see [`docs/roadmap/README.md`](./roadmap/README.md))
1. `F-06` Tenant-Isolated File Storage (S3/MinIO) & Malware Scan (`APPROVED` — next ready item)
3. `F-04` Multi-Channel Notification Engine (Email, SMS, In-App) (`APPROVED`)
4. `F-01` Account Recovery & Security Hardening (`APPROVED`)
5. `M-01` Institution & Academic Structure (`APPROVED`)
6. `F-07` Branded Document & PDF Generation Engine (`APPROVED`)
7. `F-09` Multi-Tenant Distributed Scheduler & Job History (`APPROVED`)
8. `F-10` Bulk CSV/Excel Import & Export Framework (`APPROVED`)
9. `M-02` Staff, Teachers & Subject-Section Assignments (`IN_PROGRESS`)**:
  - Added `V13__staff_and_teachers.sql` with `designations`, `staff`, and `teacher_subject_assignments` schema plus permission seeds.
  - Added `com.schoolerp.staff` domain, repository, service, and controller layer for designations, staff records, and teacher assignment CRUD.
  - Enabled the `staff` feature flag in `DefaultFeatures` and registered the frontend module route and i18n keys.
  - Backend compile succeeds with `docker run --rm -v "$PWD":/workspace -w /workspace maven:3.9-eclipse-temurin-21 mvn -q -DskipTests compile`.
  - Frontend build succeeds with `npm --prefix frontend run build`.
10. `M-03` Students, Guardians, Admission & Enrollment (`APPROVED`)
11. `M-04` Student & Staff Attendance with Leave & Alerts (`APPROVED`)
12. `M-06` Notice Board & Targeted School Communication (`APPROVED`)
13. `M-05` Fees, Invoices, Immutable Ledger & Payment Gateways (`APPROVED`)
14. `F-03` Secrets & Key Management with Envelope Rotation (`APPROVED`)
15. `F-02` Platform Operator Authentication & TOTP MFA (`APPROVED`)

## Recent fixes
- Fixed the `V7__notification_engine.sql` tenant migration issue by quoting the reserved MySQL identifier `year_month` as `` `year_month` `` so tenant provisioning and the M-02 staff isolation test can proceed without SQL syntax failures.
- Validated the exact two-school `StaffTenantIsolationTest` in the project-supported Dockerized Maven environment; it passes with 0 failures and 0 errors.

## Decisions log
- ADR 0001: Database per school.
- ADR 0002: RabbitMQ with Transactional Outbox for Asynchronous Messaging.
- Mapped Docker Compose MySQL to `3307:3306` locally because host port `3306` is occupied.
- Added `@Primary JdbcTemplate tenantJdbcTemplate` alongside `masterJdbcTemplate` so tenant-scoped JDBC services (`AuditService`) never accidentally bind to the master catalog database.

## Known issues
- Host machine has JRE 25 installed without `javac`/`mvn`; backend Maven builds run cleanly via Docker (`maven:3.9-eclipse-temurin-21`).

- **`F-04` Multi-Channel Notification Engine (`IN_PROGRESS`)**:
  - Added `V7__notification_engine.sql` with notification schema, base app settings, and permission seeds.
  - Added notification-domain enums and the first slice utilities: `SmsSegmentCalculator` and `NotificationTemplateRenderer`.
  - Added focused test coverage for the SMS segment calculator; Dockerized Maven validation is working via the repo’s supported toolchain.
