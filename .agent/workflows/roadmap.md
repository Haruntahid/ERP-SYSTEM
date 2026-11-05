# /roadmap

Purpose: inspect the current roadmap state without modifying any files. Read-only.

Steps:
1. Read `docs/roadmap/README.md`, `docs/roadmap/OPEN-QUESTIONS.md`, and `docs/PROGRESS.md`.
2. Print a concise summary containing:
   - **Status Table**: all items (`F-01`..`F-10`, `M-01`..`M-06`) with ID, title, status, priority, effort, and revision.
   - **In Progress**: any item currently marked `IN_PROGRESS` and which implementation slice is active.
   - **Next Ready to Build**: items whose dependencies are `DONE` and whose status is `APPROVED` (or `PROPOSED` if awaiting approval).
   - **Blocked by Open Questions**: list each open question (`Q-xxx`) from `docs/roadmap/OPEN-QUESTIONS.md` that blocks the next upcoming items, along with its recommended default.
3. Do not modify any files or start implementation.
