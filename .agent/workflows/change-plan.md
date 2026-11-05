# /change-plan

Input: `"<ID>: <requested change>"` (for example: `"M-05: add Nagad webhook IP allowlist and change late fine default to flat 50 BDT"`).

Steps:
1. Read `docs/roadmap/00-conventions.md`, `docs/roadmap/README.md`, `docs/roadmap/OPEN-QUESTIONS.md`, and the target plan file (`docs/roadmap/foundation/<ID>-*.md` or `docs/roadmap/phase-1/<ID>-*.md`).
2. Update the target plan file to reflect the requested change across all relevant sections (Business Rules, Configurability, Domain Model, Database, API, Frontend, Test Plan, Acceptance Criteria, Slices).
3. Increment the `Revision` number in the plan metadata header and append a row to Section 17 (`Revision History`).
4. Check the `Blocks` field of `<ID>` (and the dependency graph in `docs/roadmap/README.md`). Read and update every dependent plan affected by the change, incrementing their `Revision` numbers and `Revision History` tables as well.
5. Update `docs/roadmap/OPEN-QUESTIONS.md` (mark answered questions as `RESOLVED` or add new `Q-xxx` items if introduced), update `Rev` numbers in `docs/roadmap/README.md`, and append a row to `docs/roadmap/CHANGELOG.md`.
6. **STOP** and present a concise diff summary (files changed, revision numbers bumped, downstream plans updated) to the user for approval. Do not write or modify application code.
