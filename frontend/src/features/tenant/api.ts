import { api } from '../../shared/api/client';

export interface TenantConfig {
  slug: string;
  name: string;
  plan: string;
  features: Record<string, boolean>;
}

export async function fetchTenantConfig(): Promise<TenantConfig> {
  const response = await api.get<TenantConfig>('/api/v1/tenant/config');
  return response.data;
}
