import axios, {type AxiosError, type InternalAxiosRequestConfig} from 'axios';
import {ApiPaths} from './paths';
import {useAuthStore} from '../auth/authStore';
import type {ApiResponse, AuthResponse} from '../types/api';

const apiBase = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';

export const api = axios.create({
  baseURL: apiBase,
  timeout: 30000,
  headers: {'Content-Type': 'application/json'},
});

type RetriableConfig = InternalAxiosRequestConfig & {_retry?: boolean};

type RefreshResult = {status: 'ok'; accessToken: string} | {status: 'invalid'};

let refreshInFlight: Promise<RefreshResult> | null = null;

export async function refreshAccessToken(): Promise<RefreshResult> {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken) {
    return {status: 'invalid'};
  }
  if (!refreshInFlight) {
    refreshInFlight = axios
      .post<ApiResponse<AuthResponse>>(`${apiBase}${ApiPaths.AUTH_REFRESH}`, {refreshToken}, {
        headers: {'Content-Type': 'application/json'},
      })
      .then(res => {
        const payload = res.data;
        if (payload?.success && payload.data?.accessToken && payload.data.refreshToken) {
          useAuthStore.getState().setTokens(
            payload.data.accessToken,
            payload.data.refreshToken,
            payload.data.profile?.email,
            payload.data.profile ?? null,
          );
          return {status: 'ok' as const, accessToken: payload.data.accessToken};
        }
        return {status: 'invalid' as const};
      })
      .catch(() => ({status: 'invalid' as const}))
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

api.interceptors.request.use(req => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    req.headers.Authorization = `Bearer ${token}`;
  }
  return req;
});

api.interceptors.response.use(
  res => res,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined;
    if (!original || error.response?.status !== 401 || original._retry) {
      return Promise.reject(error);
    }
    original._retry = true;
    const result = await refreshAccessToken();
    if (result.status !== 'ok') {
      useAuthStore.getState().clear();
      return Promise.reject(error);
    }
    original.headers.Authorization = `Bearer ${result.accessToken}`;
    return api(original);
  },
);

export function getApiBaseUrl(): string {
  return apiBase;
}
