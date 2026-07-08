import axios, {type AxiosError, type InternalAxiosRequestConfig} from 'axios';
import {config} from '../utils/config';
import {useAuthStore} from '../store/authStore';
import {useApiDebugStore} from '../store/apiDebugStore';
import {logErrorCurl, logRequestCurl, logResponseCurl, toCurl} from '../utils/curlLogger';
import {correlationHeaders} from '../utils/correlation';

export const api = axios.create({
  baseURL: config.apiBaseUrl,
  timeout: 30000,
  headers: {'Content-Type': 'application/json'},
  validateStatus: status => status >= 200 && status < 300,
});

type RetriableConfig = InternalAxiosRequestConfig & {_retry?: boolean};

let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken) return null;

  if (!refreshInFlight) {
    refreshInFlight = axios
      .post<ApiResponse<AuthResponse>>(`${config.apiBaseUrl}/api/v1/auth/refresh`, {
        refreshToken,
      })
      .then(res => {
        if (res.data.success) {
          useAuthStore
            .getState()
            .setTokens(res.data.data.accessToken, res.data.data.refreshToken);
          return res.data.data.accessToken;
        }
        return null;
      })
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null;
      });
  }

  return refreshInFlight;
}

if (__DEV__) {
  // RN 0.76 bridgeless: logs appear in Metro terminal (`npm start`), not always in DevTools.
  console.warn('[API config] baseURL =', config.apiBaseUrl);
}

function recordDebug(
  config: InternalAxiosRequestConfig,
  ok: boolean,
  status?: number,
  responseBody?: unknown,
  error?: string,
): void {
  if (!__DEV__) return;
  const body =
    config.data == null
      ? undefined
      : typeof config.data === 'string'
        ? config.data
        : JSON.stringify(config.data);
  useApiDebugStore.getState().setLast({
    at: new Date().toISOString(),
    method: (config.method ?? 'get').toUpperCase(),
    url: `${config.baseURL ?? ''}${config.url ?? ''}`,
    status,
    ok,
    requestBody: body,
    responseBody:
      responseBody == null
        ? undefined
        : typeof responseBody === 'string'
          ? responseBody
          : JSON.stringify(responseBody, null, 2),
    curl: toCurl(config),
    error,
  });
}

api.interceptors.request.use(req => {
  Object.assign(req.headers, correlationHeaders());
  const token = useAuthStore.getState().accessToken;
  if (token) {
    req.headers.Authorization = `Bearer ${token}`;
  }
  logRequestCurl(req);
  return req;
});

api.interceptors.response.use(
  response => {
    logResponseCurl(response);
    recordDebug(response.config, true, response.status, response.data);
    return response;
  },
  error => {
    logErrorCurl(error);
    if (error.config) {
      recordDebug(
        error.config,
        false,
        error.response?.status,
        error.response?.data,
        error.message,
      );
    }

    const axiosError = error as AxiosError;
    const status = axiosError.response?.status;
    const failedConfig = axiosError.config as RetriableConfig | undefined;

    if (
      failedConfig &&
      (status === 401 || status === 403) &&
      !failedConfig._retry &&
      !failedConfig.url?.includes('/api/v1/auth/')
    ) {
      // If already logged out (no access token) don't attempt refresh — avoids
      // a flood of retry requests after a session expires while a poll timer is
      // still running (e.g. signaling poll during live monitoring).
      if (!useAuthStore.getState().accessToken) {
        return Promise.reject(error);
      }
      failedConfig._retry = true;
      return refreshAccessToken().then(newToken => {
        if (newToken) {
          failedConfig.headers = failedConfig.headers ?? {};
          failedConfig.headers.Authorization = `Bearer ${newToken}`;
          return api.request(failedConfig);
        }
        useAuthStore.getState().logout();
        return Promise.reject(error);
      });
    }

    return Promise.reject(error);
  },
);

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export const authApi = {
  register: (body: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    consentAccepted: string;
  }) => api.post<ApiResponse<AuthResponse>>('/api/v1/auth/register', body),

  login: (email: string, password: string) =>
    api.post<ApiResponse<AuthResponse>>('/api/v1/auth/login', {email, password}),
};

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface DeviceSummary {
  id: string;
  deviceName: string;
  status: string;
  batteryLevel?: number;
  charging?: boolean;
  networkType?: string;
  lastSeenAt?: string;
  lastLatitude?: number;
  lastLongitude?: number;
  foregroundApp?: string;
  deviceOwnerActive?: boolean;
  mediaProjectionGranted?: boolean;
  mediaProjectionConsentState?: string;
  silentScreenCapable?: boolean;
  protectionLevel?: 'FULL' | 'DEVICE_OWNER' | 'STANDARD' | string;
  kioskModeActive?: boolean;
  accessibilityEnabled?: boolean;
  accessibilityServiceConnected?: boolean;
}

export type DeviceCommandType =
  | 'ENABLE_KIOSK'
  | 'DISABLE_KIOSK'
  | 'REVOKE_MONITORING'
  | 'TAP'
  | 'SWIPE'
  | 'GLOBAL_BACK'
  | 'GLOBAL_HOME'
  | 'TYPE_TEXT'
  | 'LAUNCH_APP'
  | 'SYNC_INSTALLED_APPS';

export interface AuditLogItem {
  id: string;
  action: string;
  resource?: string;
  deviceId?: string;
  ipAddress?: string;
  metadataJson?: string;
  createdAt: string;
}

export interface NotificationItem {
  id: string;
  packageName?: string;
  title?: string;
  body?: string;
  postedAt: string;
}

export interface LocationPoint {
  id: string;
  latitude: number;
  longitude: number;
  accuracyM?: number;
  recordedAt: string;
}

export interface UsageStatEntry {
  id: string;
  deviceId: string;
  packageName: string;
  usageMs: number;
  recordedAt: string;
}

export interface InstalledAppItem {
  id: string;
  packageName: string;
  appLabel: string;
  appCategory?: string;
  syncedAt: string;
}

export interface GeofenceZone {
  policyId: string;
  ruleId: string;
  name: string;
  latitude: number;
  longitude: number;
  radiusM: number;
  alertOnEnter: boolean;
  alertOnExit: boolean;
}

export interface TimeExtensionRequestItem {
  id: string;
  deviceId: string;
  deviceName?: string;
  packageName: string;
  appLabel?: string;
  requestedMinutes: number;
  status: 'PENDING' | 'APPROVED' | 'DENIED' | 'EXPIRED';
  grantMinutes?: number;
  grantedUntil?: string;
  createdAt: string;
  respondedAt?: string;
}

export interface AlertItem {
  id: string;
  deviceId: string;
  alertType: string;
  severity: string;
  title: string;
  message?: string;
  payloadJson?: Record<string, unknown>;
  createdAt: string;
}

export interface ScreenShareSession {
  sessionId: string;
  deviceId: string;
  signalingRoomId: string;
  streamType?: string;
  status: string;
  startedAt: string;
  message?: string;
  captureMode?: 'NATIVE' | 'RN_DISPLAY_MEDIA';
  traceId?: string;
}

export const deviceApi = {
  list: () => api.get<ApiResponse<DeviceSummary[]>>('/api/v1/devices'),
  get: (deviceId: string) =>
    api.get<ApiResponse<DeviceSummary>>(`/api/v1/devices/${deviceId}`),
  createPairingCode: () =>
    api.post<ApiResponse<{code: string; qrPayload: string; expiresAt?: string}>>(
      '/api/v1/devices/pairing-code',
      {},
    ),
  getPairingCodeStatus: (code: string) =>
    api.get<
      ApiResponse<{
        code: string;
        used: boolean;
        expired: boolean;
        deviceId?: string;
        deviceName?: string;
      }>
    >(`/api/v1/devices/pairing-code/${encodeURIComponent(code)}/status`),
  sendCommand: (
    deviceId: string,
    commandType: DeviceCommandType,
    payload?: Record<string, unknown>,
    parentPin?: string,
  ) =>
    api.post<ApiResponse<{id: string; status: string}>>(
      `/api/v1/devices/${deviceId}/commands`,
      {
        commandType,
        ...(payload ? {payload} : {}),
        ...(parentPin ? {parentPin} : {}),
      },
    ),
  getTamperCode: (deviceId: string) =>
    api.get<
      ApiResponse<{active: boolean; code?: string; expiresAt?: string}>
    >(`/api/v1/devices/${deviceId}/tamper-code`),
};

export interface SubscriptionInfo {
  id: string;
  planId: string;
  status: string;
  active: boolean;
  trialEndsAt?: string;
  currentPeriodEnd?: string;
  maxDevices: number;
  liveStreamingAllowed: boolean;
}

export interface PlanInfo {
  planId: string;
  maxDevices: number;
  liveStreamingAllowed: boolean;
}

export const billingApi = {
  subscription: () => api.get<ApiResponse<SubscriptionInfo>>('/api/v1/billing/subscription'),
  plans: () => api.get<ApiResponse<PlanInfo[]>>('/api/v1/billing/plans'),
  checkout: (planId: string) =>
    api.post<ApiResponse<{checkoutUrl: string; planId: string}>>('/api/v1/billing/checkout', {
      planId,
    }),
};

export const auditApi = {
  list: (page = 0, size = 50, deviceId?: string) =>
    api.get<ApiResponse<PageResponse<AuditLogItem>>>('/api/v1/audit', {
      params: {page, size, ...(deviceId ? {deviceId} : {})},
    }),
};

export const monitoringApi = {
  notifications: (deviceId: string, page = 0, size = 20) =>
    api.get<ApiResponse<PageResponse<NotificationItem>>>(
      `/api/v1/devices/${deviceId}/notifications`,
      {params: {page, size}},
    ),
  locationHistory: (deviceId: string, page = 0, size = 50) =>
    api.get<ApiResponse<PageResponse<LocationPoint>>>(
      `/api/v1/devices/${deviceId}/location-history`,
      {params: {page, size}},
    ),
  usageStats: (deviceId: string, page = 0, size = 50) =>
    api.get<ApiResponse<PageResponse<UsageStatEntry>>>(
      `/api/v1/devices/${deviceId}/usage-stats`,
      {params: {page, size}},
    ),
  installedApps: (deviceId: string) =>
    api.get<ApiResponse<InstalledAppItem[]>>(`/api/v1/devices/${deviceId}/installed-apps`),
  geofences: (deviceId: string) =>
    api.get<ApiResponse<GeofenceZone[]>>(`/api/v1/devices/${deviceId}/geofences`),
  startScreenShare: (deviceId: string) =>
    api.post<ApiResponse<ScreenShareSession>>('/api/v1/monitoring/start-screen-share', {
      deviceId,
    }),
  startStream: (
    deviceId: string,
    streamType: 'SCREEN' | 'CAMERA' | 'AUDIO',
    parentPin?: string,
  ) =>
    api.post<ApiResponse<ScreenShareSession>>('/api/v1/monitoring/start-stream', {
      deviceId,
      streamType,
      ...(parentPin ? {parentPin} : {}),
    }),
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
  stopScreenShare: (sessionId: string) =>
    api.post<ApiResponse<ScreenShareSession>>(
      `/api/v1/monitoring/stop-screen-share/${sessionId}`,
    ),
  switchCamera: (sessionId: string) =>
    api.post<ApiResponse<void>>(`/api/v1/monitoring/switch-camera/${sessionId}`),
};

export const alertsApi = {
  list: (page = 0, size = 20) =>
    api.get<ApiResponse<PageResponse<AlertItem>>>('/api/v1/alerts', {params: {page, size}}),
};

export const timeExtensionApi = {
  listPending: () =>
    api.get<ApiResponse<TimeExtensionRequestItem[]>>('/api/v1/time-extension-requests/pending'),
  listForDevice: (deviceId: string) =>
    api.get<ApiResponse<TimeExtensionRequestItem[]>>(
      `/api/v1/devices/${deviceId}/time-extension-requests`,
    ),
  respond: (requestId: string, approved: boolean, grantMinutes = 30) =>
    api.post<ApiResponse<TimeExtensionRequestItem>>(
      `/api/v1/time-extension-requests/${requestId}/respond`,
      {approved, grantMinutes},
    ),
};

export interface PolicySummary {
  id: string;
  name: string;
  policyType: string;
  enabled: boolean;
  version: number;
  deviceIds: string[];
  updatedAt: string;
}

export const policiesApi = {
  list: (type?: string) =>
    api.get<ApiResponse<PolicySummary[]>>('/api/v1/policies', {params: type ? {type} : {}}),
  createAppBlock: (body: {
    name: string;
    deviceIds: string[];
    rules: Array<{packageName: string; scheduleType?: string; scheduleJson?: Record<string, unknown>}>;
  }) => api.post<ApiResponse<PolicySummary>>('/api/v1/policies/app-block', body),
  createScreenTime: (body: {
    name: string;
    deviceIds: string[];
    packageName?: string;
    appCategory?: string;
    dailyLimitMinutes: number;
    actionOnLimit?: string;
    warnAtPercent?: number;
    weeklyScheduleJson?: Record<string, {enabled: boolean; limitMinutes: number}>;
  }) => api.post<ApiResponse<PolicySummary>>('/api/v1/policies/screen-time', body),
  createDeviceSchedule: (body: {
    name: string;
    deviceIds: string[];
    scheduleType: string;
    startTime: string;
    endTime: string;
    daysOfWeek?: string[];
    lockDevice?: boolean;
  }) => api.post<ApiResponse<PolicySummary>>('/api/v1/policies/device-schedule', body),
  createGeofence: (body: {
    name: string;
    deviceIds: string[];
    geofenceName: string;
    latitude: number;
    longitude: number;
    radiusM: number;
  }) => api.post<ApiResponse<PolicySummary>>('/api/v1/policies/geofence', body),
  createStreaming: (body: {
    name: string;
    deviceIds: string[];
    screenShareAllowed?: boolean;
    cameraAllowed?: boolean;
    audioAllowed?: boolean;
  }) => api.post<ApiResponse<PolicySummary>>('/api/v1/policies/streaming', body),
  delete: (policyId: string) => api.delete(`/api/v1/policies/${policyId}`),
};

export const parentPinApi = {
  setPin: (pin: string) => api.post('/api/v1/parent/pin', {pin}),
  verify: (pin: string) =>
    api.post<ApiResponse<{verified: boolean; silentStreamUnlocked?: boolean}>>(
      '/api/v1/parent/pin/verify',
      {pin},
    ),
  status: () =>
    api.get<ApiResponse<{pinConfigured: boolean; silentStreamUnlocked: boolean}>>(
      '/api/v1/parent/pin/status',
    ),
};

export const childLoginPasswordApi = {
  setPassword: (childLoginPassword: string) =>
    api.post('/api/v1/parent/child-login-password', {childLoginPassword}),
  status: () =>
    api.get<ApiResponse<{configured: boolean; linkedDeviceCount: number}>>(
      '/api/v1/parent/child-login-password/status',
    ),
};

export const signalingApi = {
  post: (roomId: string, messageType: string, payload: string, sessionId?: string) =>
    api.post(`/api/v1/monitoring/signaling/${roomId}`, {messageType, payload, sessionId}),
  recent: (roomId: string) =>
    api.get<ApiResponse<Array<{id: string; messageType: string; payload: string; senderRole: string}>>>(
      `/api/v1/monitoring/signaling/${roomId}`,
    ),
};
