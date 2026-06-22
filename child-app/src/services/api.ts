import axios, {InternalAxiosRequestConfig} from 'axios';
import {getApiBaseUrl, resolveApiBaseUrl} from '../utils/config';
import {getDeviceHmacKey, getDeviceToken} from './deviceStorage';
import {signDeviceRequest} from '../utils/deviceHmac';
import {correlationHeaders} from '../utils/correlation';
import {slog} from './streamDebug';

export const api = axios.create({
  timeout: 30000,
  headers: {'Content-Type': 'application/json'},
});

api.interceptors.request.use(async (req: InternalAxiosRequestConfig) => {
  try {
    await resolveApiBaseUrl();
  } catch {
    // Fall through — request will fail with network error and surface in UI.
  }
  req.baseURL = getApiBaseUrl();
  Object.assign(req.headers, correlationHeaders());

  // Pairing is unauthenticated — stale tokens must not be sent.
  if (req.url === '/api/v1/device/pair') {
    delete req.headers.Authorization;
    return req;
  }

  const token = await getDeviceToken();
  if (token) {
    req.headers.Authorization = `Bearer ${token}`;
  }

  const method = (req.method ?? 'get').toUpperCase();
  const urlPath = req.url?.split('?')[0] ?? '';
  const hmacPaths = [
    '/api/v1/device/',
    '/api/v1/child/',
    '/api/v1/events/',
    '/api/v1/monitoring/signaling/',
  ];
  if (
    method === 'POST' &&
    hmacPaths.some(prefix => urlPath.startsWith(prefix)) &&
    urlPath !== '/api/v1/device/pair'
  ) {
    const hmacKey = await getDeviceHmacKey();
    if (hmacKey) {
      const path = urlPath;
      const body = typeof req.data === 'string' ? req.data : JSON.stringify(req.data ?? {});
      const {timestamp, nonce, signature} = signDeviceRequest(hmacKey, method, path, body);
      req.headers['X-Device-Timestamp'] = timestamp;
      req.headers['X-Device-Nonce'] = nonce;
      req.headers['X-Device-Signature'] = signature;
    }
  }
  return req;
});

api.interceptors.response.use(
  res => {
    const url = res.config.url ?? '';
    if (url.includes('streaming') || url.includes('signaling') || url.includes('/device/pair')) {
      slog('API', `${res.status} ${res.config.method?.toUpperCase()} ${url}`, {
        success: (res.data as {success?: boolean})?.success,
        message: (res.data as {message?: string})?.message,
      });
    }
    return res;
  },
  err => {
    const url = err.config?.url ?? '';
    if (url.includes('streaming') || url.includes('signaling') || url.includes('/device/pair')) {
      slog('API', `FAIL ${err.response?.status ?? '?'} ${err.config?.method?.toUpperCase()} ${url}`, {
        message: err.message,
        body: err.response?.data,
      });
    }
    return Promise.reject(err);
  },
);

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export const deviceApi = {
  pair: (body: {
    pairingCode: string;
    deviceName: string;
    androidId: string;
    consentAccepted: string;
    deviceModel?: string;
    osVersion?: string;
    appVersion?: string;
    integrityToken?: string;
    trustSignals?: Record<string, unknown>;
    fcmToken?: string;
  }) =>
    api.post<
      ApiResponse<{
        deviceId: string;
        deviceToken: string;
        deviceHmacKey?: string;
        parentDisplayName?: string;
        deviceName?: string;
      }>
    >('/api/v1/device/pair', body),

  heartbeat: (body: Record<string, unknown>) =>
    api.post('/api/v1/device/heartbeat', body),

  reportStreamFailure: (sessionId: string, reason: string) =>
    api.post(`/api/v1/device/stream-sessions/${sessionId}/failure`, {reason}),
};

export const monitoringApi = {
  ingestNotifications: (notifications: Array<{
    packageName?: string;
    title?: string;
    body?: string;
    postedAt: string;
  }>) => api.post<ApiResponse<{accepted: number}>>('/api/v1/device/notifications', {notifications}),

  ingestLocation: (body: {
    latitude: number;
    longitude: number;
    accuracyM?: number;
    recordedAt?: string;
  }) => api.post('/api/v1/device/location', body),

  ingestUsageStats: (entries: Array<{
    packageName: string;
    usageMs: number;
    recordedAt: string;
  }>) => api.post<ApiResponse<{accepted: number}>>('/api/v1/device/usage-stats', {entries}),
};

export const policyApi = {
  fetchBundle: () => api.get<ApiResponse<unknown>>('/api/v1/child/policies'),
  ack: (policyVersion: string) =>
    api.post('/api/v1/child/policies/ack', {policyVersion}),
};

export const eventsApi = {
  reportViolation: (body: {
    policyId?: string;
    violationType: string;
    packageName?: string;
    details?: Record<string, unknown>;
  }) => api.post('/api/v1/events/policy-violations', body),
};

export const streamingApi = {
  webrtcConfig: (sessionId?: string) =>
    api.get<
      ApiResponse<{
        iceServers: Array<{urls: string[]; username?: string; credential?: string}>;
        credentialExpiresAt?: number;
        credentialTtlSeconds?: number;
      }>
    >('/api/v1/monitoring/webrtc-config', {
      params: sessionId ? {sessionId} : undefined,
    }),
  reportIceConnectivity: (body: {
    streamType: string;
    sessionId?: string;
    iceConnectionState: string;
    selectedCandidateType?: string;
    turnUsed?: boolean;
    failureReason?: string;
  }) => api.post<ApiResponse<void>>('/api/v1/monitoring/webrtc-ice-report', body),
  pendingSessions: () =>
    api.get<
      ApiResponse<
        Array<{
          sessionId: string;
          signalingRoomId: string;
          streamType: string;
          status: string;
        }>
      >
    >('/api/v1/child/streaming/pending'),
  activeSession: () =>
    api.get<
      ApiResponse<{
        sessionId: string;
        signalingRoomId: string;
        streamType: string;
        status: string;
      } | null>
    >('/api/v1/child/streaming/active'),
};

export const signalingApi = {
  post: (roomId: string, messageType: string, payload: string, sessionId: string) =>
    api.post(`/api/v1/monitoring/signaling/${roomId}`, {messageType, payload, sessionId}),
  recent: (roomId: string) =>
    api.get<
      ApiResponse<Array<{id: string; messageType: string; payload: string; senderRole: string}>>
    >(`/api/v1/monitoring/signaling/${roomId}`),
};

export const commandApi = {
  pending: () =>
    api.get<
      ApiResponse<Array<{id: string; commandType: string; payload?: string}>>
    >('/api/v1/device/commands/pending'),
  ack: (commandId: string, success: boolean, errorMessage?: string) =>
    api.post(`/api/v1/device/commands/${commandId}/ack`, {success, errorMessage}),
};
