# Security Rules

- Secrets only from environment variables. Never commit secrets. The dev defaults in application.yml are for local use only.
- Passwords: Argon2id or bcrypt. Never log passwords, tokens or personal data.
- Authentication: JWT access token plus rotating refresh token. The token carries the tenant slug and user id. Permissions are loaded server side.
- Authorization: DB driven roles and permissions (`module.action`) with scope (ALL, DEPARTMENT, CLASS, SECTION, OWN). Enforce in the backend with method security plus a scope check. Never rely on the frontend.
- Validate all input. Use parameterized queries only. The only dynamic SQL allowed is tenant provisioning, and identifiers there are validated by a strict regex.
- Rate limit login and public endpoints.
- File uploads: validate type and size, store under a tenant prefixed path, never serve from the app directory.
- Tenant database passwords are encrypted (AES-GCM) in the master database. Rotate app.crypto.key with a migration plan.
- Dependency updates go through review. No unpinned or very new versions of critical libraries.
- Audit log is append only.
