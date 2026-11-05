# Frontend

React 18, TypeScript, Vite, React Router, TanStack Query.

```bash
npm install
cp .env.example .env.local   # optional
npm run dev                  # http://localhost:5173, proxies /api to http://localhost:8080
```

- Operator console: http://localhost:5173/platform (enter the platform key, create a school).
- School app: in development choose the school slug on the first screen. In production the school comes from the subdomain (`VITE_BASE_DOMAIN`).
