# Architecture

## Overview
```
React SPA (one build, many schools)
        |  HTTPS, subdomain per school (school1.example.com) or X-Tenant-ID header in development
        v
Spring Boot modular monolith (stateless)
   TenantFilter -> TenantContext -> TenantRoutingDataSource
        |                                   |
        v                                   v
  school_master (catalog)        school<slug> databases (one per school)
```

## Tenancy: database per school
- `school_master.tenants` stores slug, status, plan, feature flags, JDBC URL and the encrypted database credentials.
- Provisioning (`POST /api/v1/platform/tenants`) creates the database and a dedicated database user, runs the tenant Flyway migrations, and inserts the catalog row. Failures clean up what was created.
- At startup every active school is migrated to the latest schema (TenantMigrationRunner).
- Hibernate does not touch the database at boot (`allow_jdbc_metadata_access=false`) because no tenant is bound then.
- See ADR 0001 for the reasoning and trade-offs.

## Customization layers
1. Plan and feature flags: which modules a school can use (`features_json`, served by `/api/v1/tenant/config`, used by the frontend navigation and enforced by the backend).
2. Settings: per school key/value configuration in `app_settings` (locale, timezone, grading scale, branding).
3. Configurable domain data: grading scales, fee heads, academic calendar and similar are data in the school database, never code branches.
4. Branding: colors and logo applied by the frontend ThemeProvider from settings.

## Module layout (backend)
```
com.schoolerp
  shared      tenancy, crypto, error, config (cross cutting only)
  platform    school provisioning, plans, feature flags (operator facing)
  settings    per school settings
  <module>    api, application, domain, infrastructure (students, fees, exams ...)
```
Modules interact through application services or domain events. Consider Spring Modulith to verify boundaries in tests.

## Scaling path
- Stateless API: scale horizontally behind a load balancer.
- Connection pools are lazy and small, one per active school. If hundreds of schools are active per instance, add an LRU eviction of idle pools and consider ProxySQL.
- Move a school to another MySQL server by changing its `jdbc_url` (after copying the database).
- Heavy work (SMS, reports, PDF) goes through a queue (RabbitMQ or Kafka) with the tenant slug in every message.
- Reports run against read replicas.
- Cache (Redis) keys are prefixed with the tenant slug.

## Environments
Local (docker compose MySQL), staging, production. Configuration via environment variables. The `prod` Spring profile requires all secrets.

## Known gaps in the starter (planned work, see PROGRESS.md and docs/roadmap/README.md)
- Tenant JWT authentication, scoped RBAC, append-only `AuditService`, Redis-backed caching/rate-limiting/lockouts/token-revocation (`F-08`), RabbitMQ transactional outbox + idempotent consumers (`F-05`), and tenant-isolated file storage + malware scanning (`F-06`) are implemented.
- Platform API uses a static key (`F-02` replaces it with operator authentication + TOTP MFA).
- Multi-channel notification engine (`F-04`) and account recovery/security hardening (`F-01`) are next in the roadmap build queue.
