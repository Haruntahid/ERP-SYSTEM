# /review

Purpose: review the current changes before merge. Read only. Do not modify code.

Checklist:
1. Tenancy: no master DB access from business code, no manual TenantContext misuse, async work binds the tenant, cache and storage keys are tenant prefixed, isolation test exists.
2. Security: authorization enforced in the backend with scope, input validated, no SQL concatenation, no secrets or personal data in logs.
3. Data: Flyway migration is new (not edited), backward compatible, indexes present, soft delete for academic and financial data, money uses DECIMAL.
4. API: versioned, paginated lists, ProblemDetail errors, DTOs only.
5. Frontend: no direct HTTP outside the api client, no hardcoded strings, no `any`, feature flags respected.
6. Tests and docs updated.

Output: a list of findings grouped by severity (blocker, important, minor) with file and line references. Wait for the user before fixing anything.
