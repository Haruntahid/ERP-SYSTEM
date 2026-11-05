import { api } from '../../shared/api/client';

export interface Setting {
  key: string;
  value: string;
}

export async function fetchSettings(): Promise<Setting[]> {
  const response = await api.get<Setting[]>('/api/v1/settings');
  return response.data;
}

export async function updateSetting(key: string, value: string): Promise<Setting> {
  const response = await api.put<Setting>(`/api/v1/settings/${key}`, { value });
  return response.data;
}

export interface AccountLockoutStatus {
  userId: number;
  identity: string;
  locked: boolean;
  failedAttempts: number;
  lockedUntil?: string;
}

export interface CacheEvictResult {
  evictedCaches: string[];
  keysRemoved: number;
}

export async function fetchLockoutStatus(userId: number): Promise<AccountLockoutStatus> {
  const response = await api.get<AccountLockoutStatus>(`/api/v1/security/lockouts/${userId}`);
  return response.data;
}

export async function unlockUserAccount(userId: number): Promise<void> {
  await api.delete(`/api/v1/security/lockouts/${userId}`);
}

export async function revokeAllUserSessions(userId: number): Promise<void> {
  await api.post(`/api/v1/security/users/${userId}/revoke-sessions`);
}

export async function evictSchoolCache(cacheNames: string[] = ['all']): Promise<CacheEvictResult> {
  const response = await api.post<CacheEvictResult>('/api/v1/cache/evict', { cacheNames });
  return response.data;
}
