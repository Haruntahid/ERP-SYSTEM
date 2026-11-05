# Agent Entry Point

This repository is a multi-tenant School/University ERP sold as SaaS. Every school gets its own database.

Before doing anything in any session, read in this order:
1. `.agent/rules/` (all files). These are binding.
2. `docs/roadmap/README.md` (master roadmap, status table, dependency graph, and build order)
3. `docs/PROGRESS.md` (session memory and current state)
4. `docs/ARCHITECTURE.md`
5. `docs/DOMAIN.md`

Working agreement:
- Plan first, implement only after the user replies "approved" for a specific roadmap item.
- Use the workflows in `.agent/workflows/` (`/roadmap`, `/next`, `/change-plan`, `/bootstrap`, `/feature`, `/new-module`, `/review`).
- Keep `docs/roadmap/README.md`, `docs/roadmap/CHANGELOG.md`, and `docs/PROGRESS.md` current after every slice and item.
- Everything you write (code, comments, commits, docs) is in English.
