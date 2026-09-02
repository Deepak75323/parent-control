import axios, {type AxiosError, type InternalAxiosRequestConfig} from 'axios';
import {redirectToLogin} from '../auth/redirectToLogin';
import {ApiPaths} from './paths';
import {useAuthStore} from '../auth/authStore';
import type {ApiResponse, AuthResponse} from '../types/api';
import {
  isApiPayloadEncryptionConfigured,
  unwrapResponseData,
  wrapRequestBody,
} from '../lib/apiPayloadCrypto';

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
    const refreshBody = isApiPayloadEncryptionConfigured()
      ? wrapRequestBody({refreshToken})
      : {refreshToken};
    refreshInFlight = axios
      .post(`${apiBase}${ApiPaths.AUTH_REFRESH}`, refreshBody, {
        headers: {'Content-Type': 'application/json'},
      })
      .then(res => {
        const payload = unwrapResponseData(res.data) as ApiResponse<AuthResponse> | undefined;
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
  if (typeof FormData !== 'undefined' && req.data instanceof FormData) {
    delete req.headers['Content-Type'];
  }
  const method = (req.method ?? 'get').toUpperCase();
  if (
    isApiPayloadEncryptionConfigured() &&
    method !== 'GET' &&
    method !== 'HEAD' &&
    req.data !== undefined &&
    req.data !== null &&
    !(typeof FormData !== 'undefined' && req.data instanceof FormData)
  ) {
    const data = req.data;
    const alreadyWrapped =
      typeof data === 'object' &&
      data !== null &&
      Object.keys(data as object).length === 1 &&
      typeof (data as {data?: unknown}).data === 'string';
    if (!alreadyWrapped) {
      req.data = wrapRequestBody(data);
    }
  }
  return req;
});

api.interceptors.response.use(
  res => {
    res.data = unwrapResponseData(res.data);
    return res;
  },
  async (error: AxiosError) => {
    if (error.response?.data != null) {
      error.response.data = unwrapResponseData(error.response.data);
    }
    const original = error.config as RetriableConfig | undefined;
    if (!original || error.response?.status !== 401 || original._retry) {
      return Promise.reject(error);
    }
    original._retry = true;
    const result = await refreshAccessToken();
    if (result.status !== 'ok') {
      useAuthStore.getState().clear();
      redirectToLogin();
      return Promise.reject(error);
    }
    original.headers.Authorization = `Bearer ${result.accessToken}`;
    return api(original);
  },
);

export function getApiBaseUrl(): string {
  return apiBase;
}
