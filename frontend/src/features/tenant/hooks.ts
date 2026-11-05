import { useQuery } from '@tanstack/react-query';
import { fetchTenantConfig } from './api';
import { resolveTenantSlug } from '../../shared/tenant/tenant';

export function useTenantConfig() {
  const slug = resolveTenantSlug();
  return useQuery({
    queryKey: ['tenant-config', slug],
    queryFn: fetchTenantConfig,
    enabled: slug !== null,
  });
}
