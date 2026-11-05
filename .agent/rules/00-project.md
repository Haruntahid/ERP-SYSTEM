# Project Rules

## Product
Multi-tenant School/University ERP sold as SaaS to many schools.
- Backend: Spring Boot 3, Java 21, MySQL 8, Flyway, modular monolith.
- Frontend: React 18, TypeScript, Vite, React Router, TanStack Query.
- Tenancy: database per school. See 01-tenancy.md. This is the most important rule set.

## Working agreement
1. At the start of every session, read `AGENTS.md`, `docs/roadmap/README.md`, `docs/PROGRESS.md`, `docs/ARCHITECTURE.md`, and `docs/DOMAIN.md` before planning or coding.
2. Never write code before a plan exists in `docs/roadmap/` (or `docs/plans/`) and its status is `APPROVED`.
3. After writing or changing a plan (`/change-plan`), STOP and wait for the user to reply "approved".
4. Implement one roadmap item at a time in small vertical slices (migration, backend, tests, frontend) using `/next`. Each slice must build and pass tests.
5. After each slice, tick the plan checklist (`docs/roadmap/...`) and update `docs/PROGRESS.md` and `docs/roadmap/README.md`.
6. Ask before destructive commands (drop, delete, force push, reset).
7. If a requirement is ambiguous, record it in `docs/roadmap/OPEN-QUESTIONS.md` and ask. Do not guess business rules (fees, grading, attendance).
8. Record significant technical decisions as an ADR in `docs/adr/`.

## Language
All code, identifiers, comments, commit messages, docs and UI strings keys are in English.
User facing text goes through i18n (en and bn) and is never hardcoded in components.

## Definition of done
- Builds with no warnings introduced.
- Unit and integration tests added and passing.
- Tenant isolation considered and tested with two schools (see 01-tenancy.md).
- Migration included if the schema changed.
- Permissions seeded and feature flag enforced (backend and frontend).
- i18n keys (`en` and `bn`) added for all user-facing text.
- OpenAPI annotations or DTO docs updated.
- Audit log entries recorded for sensitive mutations.
- `docs/roadmap/README.md`, `docs/roadmap/CHANGELOG.md`, and `docs/PROGRESS.md` updated.
