import axios, { AxiosError } from 'axios';
import { resolveTenantSlug } from '../tenant/tenant';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const slug = resolveTenantSlug();
  if (slug) {
    config.headers.set('X-Tenant-ID', slug);
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
