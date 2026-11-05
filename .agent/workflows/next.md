# /next

Purpose: execute the next ready item from the roadmap following the Phase 2 Execution Protocol.

Steps:
1. Read `AGENTS.md`, all `.agent/rules/*`, `docs/roadmap/README.md`, `docs/roadmap/OPEN-QUESTIONS.md`, and `docs/PROGRESS.md`.
2. Identify the next item in the recommended build order whose `Depends On` items are all `DONE` and whose status is `APPROVED`.
   - If the next candidate item is still `PROPOSED` or has unresolved `OPEN` questions in `docs/roadmap/OPEN-QUESTIONS.md`, list its blocking `Q-xxx` questions (with recommended defaults) and ask the user to reply `"approved"` before writing any code.
3. Once the item is `APPROVED`:
   - Re-read its full plan file (`docs/roadmap/foundation/<ID>-*.md` or `docs/roadmap/phase-1/<ID>-*.md`), `docs/ARCHITECTURE.md`, and `docs/DOMAIN.md`.
   - Set its status to `IN_PROGRESS` in both its plan file and `docs/roadmap/README.md`, and log the transition in `docs/roadmap/CHANGELOG.md`.
4. Implement slice by slice following Section 14 (`Implementation Slices`) of the plan:
   - After each slice: compile, run all tests (`mvn verify` / `npm run build`), and tick the slice checkbox (`- [x]`) in the plan file.
   - Ensure the implementation includes: Flyway migration, permissions seed, feature flag enforcement (`@RequiresFeature` + `modules.ts`), i18n keys (`en` and `bn`), OpenAPI annotations, frontend pages/components, `AuditService` entries, and a two-school tenant isolation test.
   - If implementation reveals that the plan is wrong or incomplete, **STOP**, run `/change-plan`, and wait for user approval before continuing.
5. When all slices are complete:
   - Run `/review` checklist and resolve any issues.
   - Set status to `DONE` in the plan file and `docs/roadmap/README.md`.
   - Update `docs/PROGRESS.md`, `docs/roadmap/CHANGELOG.md`, `docs/DOMAIN.md`, and `docs/ARCHITECTURE.md` (and add an ADR in `docs/adr/` if a significant technical decision was made).
6. Print a final summary (what changed, how to verify manually, and what item is next) and **STOP**. Never start the next item without the user's command.
