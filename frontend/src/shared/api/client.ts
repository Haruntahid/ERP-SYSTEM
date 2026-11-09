import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
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

const PUBLIC_UNAUTHENTICATED_PATHS = [
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/public/',
  '/api/v1/tenant/config',
];

function isPublicPath(url: string | undefined): boolean {
  if (!url) return false;
  return PUBLIC_UNAUTHENTICATED_PATHS.some((prefix) => url.includes(prefix));
}

api.interceptors.request.use((config) => {
  const slug = resolveTenantSlug();
  if (slug) {
    config.headers.set('X-Tenant-ID', slug);
  }
  if (!isPublicPath(config.url)) {
    const token = getAccessToken();
    if (token && !config.headers.has('Authorization')) {
      config.headers.set('Authorization', `Bearer ${token}`);
    }
  }
  return config;
});

interface RefreshTokenResponse {
  accessToken: string;
  refreshToken: string;
}

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retriedAfterRefresh?: boolean;
}

let activeRefreshPromise: Promise<string | null> | null = null;

async function refreshAccessTokenOnce(): Promise<string | null> {
  if (activeRefreshPromise) {
    return activeRefreshPromise;
  }
  const currentRefreshToken = getRefreshToken();
  if (!currentRefreshToken) {
    return null;
  }

  activeRefreshPromise = (async () => {
    try {
      const slug = resolveTenantSlug();
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (slug) {
        headers['X-Tenant-ID'] = slug;
      }
      const res = await axios.post<RefreshTokenResponse>(
        `${import.meta.env.VITE_API_URL ?? ''}/api/v1/auth/refresh`,
        { refreshToken: currentRefreshToken },
        { headers }
      );
      setAuthTokens(res.data.accessToken, res.data.refreshToken);
      return res.data.accessToken;
    } catch {
      clearAuthTokens();
      return null;
    } finally {
      activeRefreshPromise = null;
    }
  })();

  return activeRefreshPromise;
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined;
    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retriedAfterRefresh &&
      !isPublicPath(originalRequest.url)
    ) {
      originalRequest._retriedAfterRefresh = true;
      const newAccessToken = await refreshAccessTokenOnce();
      if (newAccessToken) {
        originalRequest.headers.set('Authorization', `Bearer ${newAccessToken}`);
        return api(originalRequest);
      }
      if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/site')) {
        const slug = resolveTenantSlug();
        window.location.href = slug ? `/login?tenant=${encodeURIComponent(slug)}` : '/login';
      }
    }
    return Promise.reject(error);
  }
);

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
