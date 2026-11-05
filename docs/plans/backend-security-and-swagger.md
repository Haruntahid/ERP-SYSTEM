# Plan: Backend Bootstrap, Security (JWT + Scoped RBAC), and OpenAPI/Swagger

## 1. Goal, Scope, and Non-Goals

### Goal
Bootstrap the backend, implement complete production-grade multi-tenant security (JWT access tokens, rotating refresh tokens, tenant-claim verification, DB-driven RBAC with scope enforcement, feature-flag enforcement, login rate limiting, and audit logging), document all APIs with OpenAPI 3 / Swagger UI (including auth & tenant headers), and run the project with Swagger UI accessible and verifiable end-to-end.

### Scope
- **Bootstrap (`/bootstrap`)**: Verify environment prerequisites, start MySQL via Docker Compose, fix any starter compile/wiring issues, and verify tenant provisioning and startup migration.
- **OpenAPI / Swagger (`shared.config`)**: Configure SpringDoc OpenAPI with:
  - `BearerAuth` (JWT) security scheme,
  - `PlatformKeyAuth` (`X-Platform-Key`) security scheme,
  - Global `X-Tenant-ID` header parameter on tenant APIs,
  - Organized API groups (`platform`, `tenant`) and full `@Tag`, `@Operation`, `@ApiResponse` annotations across all controllers and DTOs.
- **Authentication (`iam` module)**:
  - Password hashing with `BCryptPasswordEncoder`.
  - Stateless JWT access tokens (`spring-security-oauth2-jose` / `NimbusJwtEncoder` & `NimbusJwtDecoder` managed by Spring Boot BOM) carrying `sub` (user ID), `tenant` (school slug), and `email`.
  - Rotating refresh tokens persisted (SHA-256 hashed) in tenant table `refresh_tokens` with revocation on logout/reuse.
  - Provisioning enhancement: create the initial `SCHOOL_ADMIN` user when a school is provisioned via `POST /api/v1/platform/tenants`.
  - Auth endpoints: `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `POST /api/v1/auth/change-password`.
- **Authorization & Tenancy Guards (`shared.security` & `iam`)**:
  - `JwtAuthenticationFilter` running after `TenantFilter`: validates JWT, verifies `jwt.tenant == TenantContext.get()` (rejects with `403 Forbidden` RFC 7807 `ProblemDetail` on mismatch), and loads user permissions and scopes server-side.
  - Method security (`@EnableMethodSecurity`) with custom `@perm` evaluator supporting permission codes (`module.action`) and hierarchical scopes (`ALL`, `DEPARTMENT`, `CLASS`, `SECTION`, `OWN`).
  - Feature flag enforcement (`@RequiresFeature`) returning `403 Forbidden` when a module is disabled for the school's plan/features.
  - Rate limiting filter (`RateLimitFilter`) protecting `/api/v1/auth/**` and public endpoints (`429 Too Many Requests`).
- **IAM & Audit (`iam`, `shared.audit`)**:
  - Paginated User & Role management endpoints (`/api/v1/users`, `/api/v1/roles`) to create users, assign scoped roles, and inspect permissions via Swagger.
  - Append-only `AuditService` recording sensitive security and configuration events into `audit_log`.

### Non-Goals
- Frontend login/RBAC screens (will be implemented in a separate frontend slice after backend verification).
- Academic, Student, Attendance, or Fee domain modules (scheduled next in `docs/PROGRESS.md`).
- Replacing the static `X-Platform-Key` with full OAuth2 operator identity (platform key remains constant-time checked via `PlatformKeyFilter`).

---

## 2. Affected Modules

- `com.schoolerp.shared.config`: `SecurityConfig`, `OpenApiConfig`, `AppProperties` (add `jwt` and `rateLimit` properties).
- `com.schoolerp.shared.security`: `JwtService`, `JwtAuthenticationFilter`, `SchoolUserPrincipal`, `PermissionChecker` (`@perm`), `RequiresFeature`, `FeatureFlagInterceptor`, `RateLimitFilter`, `SecurityProblemSupport`.
- `com.schoolerp.shared.audit`: `AuditService`, `AuditAction`.
- `com.schoolerp.iam`:
  - `api`: `AuthController`, `UserController`, `RoleController`, request/response DTOs.
  - `application`: `AuthService`, `UserService`, `RoleService`.
  - `domain`: `User`, `Role`, `Permission`, `UserRole`, `RefreshToken`, `ScopeType`, repository interfaces.
  - `infrastructure`: JPA repositories and query adapters.
- `com.schoolerp.platform`: `PlatformTenantController`, `TenantProvisioningService` (seed initial `SCHOOL_ADMIN` user during school provisioning; add OpenAPI annotations).
- `com.schoolerp.settings`: `SettingsController` (add `@PreAuthorize` permission checks, audit logging, and OpenAPI annotations).

---

## 3. Tenant Migration Outline

New Flyway migration: `backend/src/main/resources/db/tenant/V3__security_and_rbac.sql`
1. Create `refresh_tokens` table:
   - `id BIGINT AUTO_INCREMENT PRIMARY KEY`
   - `user_id BIGINT NOT NULL` (FK -> `users.id`, indexed)
   - `token_hash VARCHAR(64) NOT NULL UNIQUE`
   - `expires_at TIMESTAMP(6) NOT NULL`
   - `revoked_at TIMESTAMP(6) NULL`
   - `replaced_by_hash VARCHAR(64) NULL`
   - `created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`
2. Seed baseline permissions (`permissions` table):
   - `settings.read`, `settings.write`
   - `user.read`, `user.write`
   - `role.read`, `role.write`
   - `audit.read`
3. Grant all seeded permissions to `SCHOOL_ADMIN` in `role_permissions`, and grant `settings.read` to `REGISTRAR`, `ACCOUNTANT`, `TEACHER`.

---

## 4. API Endpoints

| Method | Path | Request | Response | Auth / Permission | Scope |
|---|---|---|---|---|---|
| `POST` | `/api/v1/platform/tenants` | `CreateTenantRequest` (`slug`, `name`, `plan`, `adminEmail`, `adminName`, `adminPassword`) | `TenantResponse` (`201`) | `X-Platform-Key` | Platform |
| `GET` | `/api/v1/platform/tenants` | — | `List<TenantResponse>` | `X-Platform-Key` | Platform |
| `PATCH` | `/api/v1/platform/tenants/{slug}/status` | `UpdateStatusRequest` | `TenantResponse` | `X-Platform-Key` | Platform |
| `PUT` | `/api/v1/platform/tenants/{slug}/features` | `Map<String, Boolean>` | `TenantResponse` | `X-Platform-Key` | Platform |
| `GET` | `/api/v1/tenant/config` | Header `X-Tenant-ID` | `TenantConfigResponse` | Public (Tenant bound) | — |
| `POST` | `/api/v1/auth/login` | `LoginRequest` (`email`, `password`) | `TokenResponse` (`accessToken`, `refreshToken`, `expiresIn`) | Public (Tenant bound, rate-limited) | — |
| `POST` | `/api/v1/auth/refresh` | `RefreshRequest` (`refreshToken`) | `TokenResponse` | Public (Tenant bound, rate-limited) | — |
| `POST` | `/api/v1/auth/logout` | `LogoutRequest` (`refreshToken`) | `204 No Content` | Authenticated | — |
| `GET` | `/api/v1/auth/me` | — | `CurrentUserResponse` (id, email, fullName, tenant, roles, permissions) | Authenticated | `OWN` |
| `POST` | `/api/v1/auth/change-password` | `ChangePasswordRequest` (`currentPassword`, `newPassword`) | `204 No Content` | Authenticated | `OWN` |
| `GET` | `/api/v1/settings` | — | `List<SettingResponse>` | `settings.read` | `ALL` |
| `PUT` | `/api/v1/settings/{key}` | `UpdateSettingRequest` | `SettingResponse` | `settings.write` | `ALL` |
| `GET` | `/api/v1/users` | `?page=0&size=20&sort=id,desc` | `Page<UserResponse>` | `user.read` | `ALL` |
| `POST` | `/api/v1/users` | `CreateUserRequest` | `UserResponse` (`201`) | `user.write` | `ALL` |
| `POST` | `/api/v1/users/{id}/roles` | `AssignRoleRequest` (`roleCode`, `scopeType`, `scopeId`) | `UserResponse` | `role.write` | `ALL` |
| `DELETE` | `/api/v1/users/{id}/roles/{userRoleId}` | — | `204 No Content` | `role.write` | `ALL` |
| `GET` | `/api/v1/roles` | — | `List<RoleResponse>` | `role.read` | `ALL` |

---

## 5. Domain Rules, Tenancy, Security & Audit Considerations

1. **Physical Tenant Isolation**:
   - `TenantFilter` resolves the tenant from `X-Tenant-ID` or subdomain and binds `TenantContext`.
   - `JwtAuthenticationFilter` extracts the `tenant` claim from the JWT access token and compares it with `TenantContext.require()`. If they differ, it immediately rejects with `403 Forbidden` (`ProblemDetail`).
2. **Server-Side Permission Resolution**:
   - JWT contains only identity (`sub`, `email`, `tenant`). Roles, permissions (`module.action`), and scopes (`ALL`, `DEPARTMENT`, `CLASS`, `SECTION`, `OWN` + `scopeId`) are loaded from the current tenant database on request authentication.
   - A grant with `scope_type = ALL` satisfies any scope check for that permission. A scoped grant (`DEPARTMENT`, `CLASS`, `SECTION`, `OWN`) requires matching `scopeType` and `scopeId` (or `userId == targetUserId` for `OWN`).
3. **Refresh Token Rotation & Reuse Detection**:
   - Refresh tokens are 32-byte cryptographically random strings; only their SHA-256 hash is stored in `refresh_tokens`.
   - On `/api/v1/auth/refresh`, the used token is marked `revoked_at = NOW()` and linked via `replaced_by_hash`. Presenting an already-revoked token revokes all active tokens for that user and returns `401 Unauthorized`.
4. **First School Admin Provisioning**:
   - `POST /api/v1/platform/tenants` accepts optional `adminEmail` (default `admin@<slug>.school`), `adminName` (default `School Admin`), and `adminPassword` (default `Admin@12345` in dev if omitted, or caller-provided), hashes the password with BCrypt, inserts the user into the newly migrated tenant DB, and assigns the `SCHOOL_ADMIN` role with `ALL` scope.
5. **Rate Limiting**:
   - `RateLimitFilter` enforces per-IP + tenant bucket limits on `/api/v1/auth/login` and `/api/v1/auth/refresh` (configurable via `app.rate-limit.auth-requests-per-minute`, default `20`).
6. **Audit Logging**:
   - Login success/failure, password changes, user creation, role assignment, and setting updates append an entry to `audit_log` without ever logging raw passwords or tokens.

---

## 6. Test Plan

- **Unit Tests**:
  - `CryptoServiceTest` (existing).
  - `JwtServiceTest`: token generation, parsing, expiration, signature tampering rejection.
  - `PermissionCheckerTest`: `ALL` scope vs specific `SECTION`/`OWN` scope evaluation.
  - `RateLimitFilterTest`: verifies `429 Too Many Requests` after threshold.
- **Integration / Tenant Isolation Tests**:
  - `TenantSecurityIsolationTest`:
    - Provision Tenant A (`alpha`) and Tenant B (`beta`).
    - Authenticate as Tenant A admin (`POST /api/v1/auth/login` with `X-Tenant-ID: alpha`).
    - Verify using Tenant A's JWT against Tenant B (`X-Tenant-ID: beta`) is rejected with `403 Forbidden`.
    - Verify data created in Tenant A (`/api/v1/users`, `/api/v1/settings`) is invisible in Tenant B.
    - Verify unauthenticated calls to `/api/v1/settings` return `401` and calls without required permission return `403`.

---

## 7. Task Checklist (Slices)

- [x] **Slice 0 — Toolchain & Bootstrap (`/bootstrap`)**: Ensure Java 21 + Maven availability (or container build), start MySQL (`docker compose up -d`), compile and verify existing starter.
- [x] **Slice 1 — OpenAPI / Swagger Configuration**: Add `OpenApiConfig` (Bearer JWT, `X-Platform-Key`, `X-Tenant-ID` header) and annotate existing controllers.
- [x] **Slice 2 — Tenant Migration V3 (`V3__security_and_rbac.sql`)**: Add `refresh_tokens` table and seed baseline permissions and role mappings.
- [x] **Slice 3 — Authentication & Provisioning Admin Seed**: Implement `JwtService`, `AuthService`, `AuthController` (`login`, `refresh`, `logout`, `me`, `change-password`), and seed the initial `SCHOOL_ADMIN` user during tenant provisioning.
- [x] **Slice 4 — Security Filter Chain, Tenant-JWT Guard, Feature Flag Guard & Rate Limiting**: Wire `JwtAuthenticationFilter`, `RateLimitFilter`, `FeatureFlagInterceptor`, and `SecurityProblemSupport` (`401`/`403` RFC 7807 responses).
- [x] **Slice 5 — Scoped RBAC, User/Role APIs & Audit Writer**: Implement `@perm` method security checker, `/api/v1/users` and `/api/v1/roles` controllers, `AuditService`, unit/isolation tests, and run the backend with Swagger UI live at `http://localhost:8080/swagger-ui.html`.

