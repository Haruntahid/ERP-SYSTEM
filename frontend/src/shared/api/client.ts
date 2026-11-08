import axios, { AxiosError } from 'axios';
import { resolveTenantSlug } from '../tenant/tenant';

const ACCESS_TOKEN_KEY = 'erp_access_token';
const REFRESH_TOKEN_KEY = 'erp_refresh_token';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setAuthTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearAuthTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const slug = resolveTenantSlug();
  if (slug) {
    config.headers.set('X-Tenant-ID', slug);
  }
  const token = getAccessToken();
  if (token && !config.headers.has('Authorization')) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const problem = (error as AxiosError<ProblemDetail>).response?.data;
    if (problem?.detail) {
      return problem.detail;
    }
    if (error.response?.status) {
      return `Request failed (${error.response.status})`;
    }
    return 'Network error';
  }
  return error instanceof Error ? error.message : 'Unexpected error';
}
