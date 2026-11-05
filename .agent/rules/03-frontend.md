# Frontend Standards

## Stack
React 18, TypeScript (strict), Vite, React Router, TanStack Query, Axios.

## Structure
- `src/app`: providers, router, layout.
- `src/features/<feature>`: api, hooks, components, pages for one feature. A feature never imports another feature's internals.
- `src/shared`: api client, tenant helpers, ui primitives, utils, i18n.
- Navigation items come from `src/app/modules.ts` and are filtered by the tenant's enabled features.

## Rules
- Server state lives in TanStack Query. Local UI state in React state. No global store unless justified in an ADR.
- All HTTP goes through `shared/api/client.ts` (adds the tenant header, handles errors). No raw fetch or axios calls in components.
- Never trust the UI for authorization. Hiding a button is UX only. The backend enforces permissions.
- No hardcoded user facing strings. Use i18n keys (en, bn).
- Theming uses CSS variables (`--color-primary`). Tenant branding is loaded from /api/v1/settings and applied by ThemeProvider.
- Components are small, typed, and accessible (labels, keyboard, contrast).
- Forms: validate on the client for UX, but the server is the source of truth.
- No `any`. No suppressed TypeScript errors.

## Testing
- Vitest and React Testing Library for components and hooks (add when the first feature lands).
