import { api } from '../../shared/api/client';

export interface PlatformTenant {
  slug: string;
  name: string;
  status: 'ACTIVE' | 'SUSPENDED';
  plan: string;
  features: Record<string, boolean>;
}

const headers = (key: string) => ({ 'X-Platform-Key': key });

export async function listTenants(key: string): Promise<PlatformTenant[]> {
  const response = await api.get<PlatformTenant[]>('/api/v1/platform/tenants', { headers: headers(key) });
  return response.data;
}

export async function createTenant(
  key: string,
  body: { slug: string; name: string; plan: string },
): Promise<PlatformTenant> {
  const response = await api.post<PlatformTenant>('/api/v1/platform/tenants', body, { headers: headers(key) });
  return response.data;
}

export async function setTenantStatus(
  key: string,
  slug: string,
  status: 'ACTIVE' | 'SUSPENDED',
): Promise<PlatformTenant> {
  const response = await api.patch<PlatformTenant>(
    `/api/v1/platform/tenants/${slug}/status`,
    { status },
    { headers: headers(key) },
  );
  return response.data;
}
