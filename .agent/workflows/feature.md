# /feature

Input: a feature description from the user.

Steps:
1. Read AGENTS.md, all .agent/rules, docs/ARCHITECTURE.md, docs/DOMAIN.md and docs/PROGRESS.md.
2. Ask clarifying questions if business rules are ambiguous.
3. Write docs/plans/<feature-name>.md with:
   - Goal, scope and explicit non-goals.
   - Affected modules (backend and frontend).
   - Tenant migration outline (new Flyway file in db/tenant).
   - API endpoints: method, path, request, response, required permission and scope.
   - Domain rules, state transitions, edge cases.
   - Tenancy, security and audit considerations. Feature flag name if the module is optional.
   - Test plan, including a tenant isolation test.
   - Frontend pages and components.
   - Task checklist split into small slices.
4. STOP. Present the plan and wait for the user to reply "approved". Do not write code before that.
5. After approval, implement slice by slice. Build and run tests after each slice.
6. Update docs/PROGRESS.md and tick the plan checklist.
7. Summarize what changed, how to verify it, and what is next.
