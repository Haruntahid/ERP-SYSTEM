# Tenancy Rules (database per school)

## Model
- `school_master` database: platform catalog (`tenants` table). Accessed only through MasterTenantRepository and the masterJdbcTemplate.
- One database per school (`school<slug>`), with its own database user. All business data lives there.
- The primary DataSource is TenantRoutingDataSource. It resolves the school database from TenantContext.

## Rules
1. Business tables live only in the tenant schema: backend/src/main/resources/db/tenant. Platform tables live only in db/master.
2. Business code never reads or writes the master database. Only the platform module and shared.tenancy do.
3. Never add a tenant_id column to tenant tables. Isolation is physical, not logical.
4. Never switch or bypass TenantContext manually inside business code.
5. Async work, schedulers and event listeners must bind the tenant explicitly (TenantContext.set in try, clear in finally) and carry the tenant slug in the message.
6. Cache keys, file storage paths, queue names and search indexes must be prefixed with the tenant slug.
7. The tenant for a request is resolved by TenantFilter. Once JWT auth exists, the token's tenant claim must match the resolved tenant, otherwise reject with 403.
8. Every new module needs an isolation test: data written under tenant A must not be visible under tenant B.
9. Schema changes: add a new Flyway file in db/tenant (V<n>__description.sql). Never edit an applied migration. Migrations must be backward compatible with the previous app version (expand, then contract).
10. Per school customization goes in `app_settings` (key/value) or a dedicated settings table, never in code branches like `if (school == "x")`.
11. Per plan or per school module switches go through feature flags (`tenants.features_json`, exposed by /api/v1/tenant/config). Backend endpoints of a disabled feature must return 403.

## Scalability notes
- Pools are lazy and small (minIdle 0). Do not raise pool sizes without measuring.
- `jdbc_url` is stored per tenant so schools can be spread over several MySQL servers later (sharding by host).
