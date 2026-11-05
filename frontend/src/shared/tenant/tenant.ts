const DEV_TENANT_KEY = 'devTenant';

/**
 * Resolves the school slug. In production it comes from the subdomain. In development it comes from
 * VITE_TENANT_ID or from the value chosen on the tenant selection screen.
 */
export function resolveTenantSlug(): string | null {
  const baseDomain = import.meta.env.VITE_BASE_DOMAIN;
  const host = window.location.hostname;
  if (baseDomain && host.endsWith(`.${baseDomain}`)) {
    return host.slice(0, host.length - baseDomain.length - 1);
  }
  if (import.meta.env.VITE_TENANT_ID) {
    return import.meta.env.VITE_TENANT_ID;
  }
  if (import.meta.env.DEV) {
    return window.localStorage.getItem(DEV_TENANT_KEY);
  }
  return null;
}

export function setDevTenant(slug: string): void {
  window.localStorage.setItem(DEV_TENANT_KEY, slug);
}

export function clearDevTenant(): void {
  window.localStorage.removeItem(DEV_TENANT_KEY);
}
