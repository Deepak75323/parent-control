import axios, {InternalAxiosRequestConfig} from 'axios';
import {getApiBaseUrl, resolveApiBaseUrl} from '../utils/config';
import {ApiPaths, HmacPathPrefixes, UnauthenticatedPaths} from '../constants/apiPaths';
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

  // Pairing and onboarding are unauthenticated — stale tokens must not be sent.
  if (UnauthenticatedPaths.some(path => req.url === path)) {
    delete req.headers.Authorization;
    return req;
  }

  const token = await getDeviceToken();
  if (token) {
    req.headers.Authorization = `Bearer ${token}`;
  }

  const method = (req.method ?? 'get').toUpperCase();
  const urlPath = req.url?.split('?')[0] ?? '';
  if (
    method === 'POST' &&
    HmacPathPrefixes.some(prefix => urlPath.startsWith(prefix)) &&
    urlPath !== ApiPaths.DEVICE_PAIR
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
    >(ApiPaths.DEVICE_PAIR, body),

  heartbeat: (body: Record<string, unknown>) =>
    api.post(ApiPaths.DEVICE_HEARTBEAT, body),

  updatePermissionStatus: (body: {
    accessibility: boolean;
    usageStats: boolean;
    overlay: boolean;
    battery: boolean;
    screenCapture: boolean;
    camera: boolean;
    microphone: boolean;
    location: boolean;
    notificationAccess: boolean;
    deviceAdmin: boolean;
  }) => api.post(ApiPaths.DEVICE_PERMISSION_STATUS, body),

  reportStreamFailure: (sessionId: string, reason: string) =>
    api.post(ApiPaths.streamSessionFailure(sessionId), {reason}),
};

export const tamperApi = {
  reportAttempt: (target?: 'accessibility' | 'device_admin') =>
    api.post<
      ApiResponse<{parentPinConfigured: boolean; message: string}>
    >(ApiPaths.DEVICE_TAMPER_ATTEMPT, target ? {target} : {}),

  verifyPin: (pin: string, target?: 'accessibility' | 'device_admin') =>
    api.post<
      ApiResponse<{verified: boolean; unlockExpiresAt?: string}>
    >(ApiPaths.DEVICE_TAMPER_VERIFY, target ? {pin, target} : {pin}),

  verifyCode: (code: string, target?: 'accessibility' | 'device_admin') =>
    api.post<
      ApiResponse<{verified: boolean; unlockExpiresAt?: string}>
    >(ApiPaths.DEVICE_TAMPER_VERIFY, target ? {code, target} : {code}),
};

export const monitoringApi = {
  ingestNotifications: (notifications: Array<{
    packageName?: string;
    title?: string;
    body?: string;
    postedAt: string;
  }>) => api.post<ApiResponse<{accepted: number}>>(ApiPaths.DEVICE_NOTIFICATIONS, {notifications}),

  ingestLocation: (body: {
    latitude: number;
    longitude: number;
    accuracyM?: number;
    recordedAt?: string;
  }) => api.post(ApiPaths.DEVICE_LOCATION, body),

  ingestUsageStats: (entries: Array<{
    packageName: string;
    usageMs: number;
    recordedAt: string;
  }>) => api.post<ApiResponse<{accepted: number}>>(ApiPaths.DEVICE_USAGE_STATS, {entries}),
};

export const policyApi = {
  fetchBundle: () => api.get<ApiResponse<unknown>>(ApiPaths.CHILD_POLICIES),
  ack: (policyVersion: string) =>
    api.post(ApiPaths.CHILD_POLICIES_ACK, {policyVersion}),
};

export const eventsApi = {
  reportViolation: (body: {
    policyId?: string;
    violationType: string;
    packageName?: string;
    details?: Record<string, unknown>;
  }) => api.post(ApiPaths.EVENTS_POLICY_VIOLATIONS, body),
};

export const streamingApi = {
  webrtcConfig: (sessionId?: string) =>
    api.get<
      ApiResponse<{
        iceServers: Array<{urls: string[]; username?: string; credential?: string}>;
        credentialExpiresAt?: number;
        credentialTtlSeconds?: number;
      }>
    >(ApiPaths.MONITORING_WEBRTC_CONFIG, {
      params: sessionId ? {sessionId} : undefined,
    }),
  reportIceConnectivity: (body: {
    streamType: string;
    sessionId?: string;
    iceConnectionState: string;
    selectedCandidateType?: string;
    turnUsed?: boolean;
    failureReason?: string;
  }) => api.post<ApiResponse<void>>(ApiPaths.MONITORING_WEBRTC_ICE_REPORT, body),
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
    >(ApiPaths.CHILD_STREAMING_PENDING),
  activeSession: () =>
    api.get<
      ApiResponse<{
        sessionId: string;
        signalingRoomId: string;
        streamType: string;
        status: string;
      } | null>
    >(ApiPaths.CHILD_STREAMING_ACTIVE),
};

export const signalingApi = {
  post: (roomId: string, messageType: string, payload: string, sessionId: string) =>
    api.post(ApiPaths.signaling(roomId), {messageType, payload, sessionId}),
  recent: (roomId: string) =>
    api.get<
      ApiResponse<Array<{id: string; messageType: string; payload: string; senderRole: string}>>
    >(ApiPaths.signaling(roomId)),
};

export const commandApi = {
  pending: () =>
    api.get<
      ApiResponse<Array<{id: string; commandType: string; payload?: string}>>
    >(ApiPaths.DEVICE_COMMANDS_PENDING),
  ack: (commandId: string, success: boolean, errorMessage?: string) =>
    api.post(ApiPaths.commandAck(commandId), {success, errorMessage}),
};
