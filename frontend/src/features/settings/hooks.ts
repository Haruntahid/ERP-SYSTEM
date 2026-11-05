import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchSettings } from './api';
import { resolveTenantSlug } from '../../shared/tenant/tenant';

export function useSettings() {
  const slug = resolveTenantSlug();
  const query = useQuery({
    queryKey: ['settings', slug],
    queryFn: fetchSettings,
    enabled: slug !== null,
  });

  const settings = useMemo(() => {
    const map: Record<string, string> = {};
    (query.data ?? []).forEach((s) => {
      map[s.key] = s.value;
    });
    return map;
  }, [query.data]);

  return { settings, isLoading: query.isLoading };
}
