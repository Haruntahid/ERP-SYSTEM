# /bootstrap

Purpose: first run. Verify that the starter builds and works end to end, and fix what does not.
The backend was written without being compiled, so expect small compile or wiring errors.

Steps:
1. Read AGENTS.md, all .agent/rules, docs/ARCHITECTURE.md and docs/PROGRESS.md.
2. Check prerequisites: Java 21, Maven, Node 20+, Docker. Report what is missing.
3. Propose the commands to start MySQL (`docker compose up -d`), build the backend (`mvn -q verify` in /backend) and install and build the frontend (`npm ci` or `npm install`, `npm run build` in /frontend). Wait for approval before running them.
4. Run the build. Fix compile errors and failing tests with the smallest possible changes. Explain each fix.
5. Start the backend. Verify:
   - startup succeeds with no tenant bound (no connection attempt on the routing data source),
   - POST /api/v1/platform/tenants (header X-Platform-Key) provisions a school,
   - GET /api/v1/settings with header X-Tenant-ID returns the seeded settings,
   - an unknown tenant returns 404 and a suspended tenant returns 403.
6. Write the result and any deviations into docs/PROGRESS.md.
7. Stop and report. Do not start feature work.
