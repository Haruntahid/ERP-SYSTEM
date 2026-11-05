# School ERP Starter (multi-tenant SaaS, database per school)

Stack: Spring Boot 3 (Java 21) + MySQL 8 + Flyway, React 18 + TypeScript + Vite.
Every school gets its own database. One deployment serves all schools.

```
.agent/rules        binding rules for the AI agent (Antigravity)
.agent/workflows    /bootstrap  /feature  /new-module  /review
AGENTS.md           agent entry point
docs/               PRD, ARCHITECTURE, DOMAIN, PROGRESS, adr/, plans/
backend/            Spring Boot modular monolith
frontend/           React app (school app + operator console)
docker-compose.yml  local MySQL
```

## Run locally
```bash
docker compose up -d                 # MySQL on :3306 (root/root, development only)

cd backend
mvn spring-boot:run                  # API on :8080, Swagger UI on /swagger-ui.html

cd ../frontend
npm install
npm run dev                          # http://localhost:5173
```

Create the first school:
```bash
curl -X POST http://localhost:8080/api/v1/platform/tenants \
  -H "Content-Type: application/json" -H "X-Platform-Key: dev-platform-key" \
  -d '{"slug":"greenfield","name":"Greenfield School","plan":"PRO"}'

curl http://localhost:8080/api/v1/settings -H "X-Tenant-ID: greenfield"
```
Or use the operator console at http://localhost:5173/platform.

## Using it with Antigravity
1. Open this folder as the workspace. Rules in `.agent/rules` and workflows in `.agent/workflows` are picked up automatically.
2. Use Planning mode.
3. First command: `/bootstrap` (the backend was not compiled when this template was generated, so expect small fixes).
4. Then one feature at a time: `/feature Authentication and JWT`, `/feature RBAC with scope`, `/new-module students` ...
5. The agent writes a plan to `docs/plans/`, stops, and waits for your "approved" before coding.
6. `docs/PROGRESS.md` is the memory between sessions. Keep it current.

## Production notes
- Run with `SPRING_PROFILES_ACTIVE=prod`. All secrets must come from environment variables (see application-prod.yml).
- Generate the crypto key with `openssl rand -base64 32`. Losing it makes stored tenant database passwords unreadable.
- Do not use the root MySQL account. Use a provisioning account that can create databases and users.
- Tenant endpoints are NOT authenticated in this starter. Do not deploy before the Authentication and RBAC features are done.
