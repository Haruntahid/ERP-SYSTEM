# /new-module

Input: module name (for example `students`).

Steps:
1. Read AGENTS.md and all .agent/rules.
2. Write docs/plans/module-<name>.md describing: entities, relations, permissions (`<name>.read`, `<name>.write`, ...), feature flag, endpoints, frontend routes. STOP and wait for "approved".
3. After approval, scaffold:
   - Backend: `com.schoolerp.<name>` with api, application, domain, infrastructure packages.
   - Flyway migration in db/tenant.
   - Permission seed in a new tenant migration.
   - Tenant isolation test.
   - Frontend: `src/features/<name>` with api, hooks, components, pages, and an entry in `src/app/modules.ts` gated by the feature flag.
4. Add the feature flag to platform DefaultFeatures if it is optional by plan.
5. Update docs/PROGRESS.md.
