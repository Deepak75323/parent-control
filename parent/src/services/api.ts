import axios, {type AxiosError, type InternalAxiosRequestConfig} from 'axios';
import {config} from '../utils/config';
import {correlationHeaders} from '../utils/correlation';
import {useAuthStore} from '../store/authStore';
import {useApiDebugStore} from '../store/apiDebugStore';
import {logErrorCurl, logRequestCurl, logResponseCurl, toCurl} from '../utils/curlLogger';
import {redactBody, REDACTED} from '../utils/logRedaction';
import {ApiPaths} from '../constants/apiPaths';

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
      .post<ApiResponse<AuthResponse>>(`${config.apiBaseUrl}${ApiPaths.AUTH_REFRESH}`, {
        refreshToken,
      })
      .then(res => {
        if (res.data.success) {
          const profile = res.data.data.profile;
          useAuthStore
            .getState()
            .setTokens(
              res.data.data.accessToken,
              res.data.data.refreshToken,
              profile?.email,
              profile?.emailVerified,
            );
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
        ? redactBody(config.data)
        : redactBody(JSON.stringify(config.data));
  const responseText =
    responseBody == null
      ? undefined
      : typeof responseBody === 'string'
        ? redactBody(responseBody)
        : redactBody(JSON.stringify(responseBody, null, 2));
  useApiDebugStore.getState().setLast({
    at: new Date().toISOString(),
    method: (config.method ?? 'get').toUpperCase(),
    url: `${config.baseURL ?? ''}${config.url ?? ''}`,
    status,
    ok,
    requestBody: body,
    responseBody: responseText,
    curl: toCurl(config),
    error: error ? REDACTED : undefined,
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
      !failedConfig.url?.includes(ApiPaths.AUTH_PREFIX)
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

export interface ParentProfile {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  timezone?: string;
  emailVerified?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  profile?: ParentProfile;
}

export const authApi = {
  register: (body: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    consentAccepted: string;
  }) => api.post<ApiResponse<AuthResponse>>(ApiPaths.AUTH_REGISTER, body),

  login: (email: string, password: string) =>
    api.post<ApiResponse<AuthResponse>>(ApiPaths.AUTH_LOGIN, {email, password}),

  forgotPassword: (email: string) =>
    api.post<ApiResponse<void>>(ApiPaths.AUTH_FORGOT_PASSWORD, {email}),

  resetPassword: (token: string, password: string) =>
    api.post<ApiResponse<void>>(ApiPaths.AUTH_RESET_PASSWORD, {token, password}),

  verifyEmail: (token: string) =>
    api.post<ApiResponse<void>>(ApiPaths.AUTH_VERIFY_EMAIL, {token}),

  resendVerification: () =>
    api.post<ApiResponse<void>>(ApiPaths.AUTH_RESEND_VERIFICATION),

  deleteAccount: (password: string) =>
    api.delete<ApiResponse<void>>(ApiPaths.AUTH_ACCOUNT, {data: {password}}),
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
  // Permission status fields uploaded by the child app each sync cycle
  permAccessibility?: boolean;
  permUsageStats?: boolean;
  permOverlay?: boolean;
  permBattery?: boolean;
  permScreenCapture?: boolean;
  permCamera?: boolean;
  permMicrophone?: boolean;
  permLocation?: boolean;
  permNotificationAccess?: boolean;
  permDeviceAdmin?: boolean;
  /** P1-08: Android 15+ dataSync quota / standby degradation. */
  monitoringDegraded?: boolean;
  monitoringMode?: string;
  standbyBucket?: string;
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
  | 'SYNC_INSTALLED_APPS'
  | 'SYNC_USAGE_STATS'
  | 'SYNC_POLICIES';

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
  deviceId?: string;
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
  createdAt?: string;
}

export interface InstalledAppItem {
  id: string;
  packageName: string;
  appLabel: string;
  appCategory?: string;
  /** Small PNG as base64 (no data-URL prefix). */
  iconBase64?: string | null;
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
  list: () => api.get<ApiResponse<DeviceSummary[]>>(ApiPaths.DEVICES),
  get: (deviceId: string) =>
    api.get<ApiResponse<DeviceSummary>>(ApiPaths.device(deviceId)),
  createPairingCode: () =>
    api.post<ApiResponse<{code: string; qrPayload: string; expiresAt?: string}>>(
      ApiPaths.DEVICES_PAIRING_CODE,
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
    >(ApiPaths.pairingCodeStatus(code)),
  sendCommand: (
    deviceId: string,
    commandType: DeviceCommandType,
    payload?: Record<string, unknown>,
    parentPin?: string,
  ) =>
    api.post<ApiResponse<{id: string; status: string}>>(
      ApiPaths.deviceCommands(deviceId),
      {
        commandType,
        ...(payload ? {payload} : {}),
        ...(parentPin ? {parentPin} : {}),
      },
    ),
  getTamperCode: (deviceId: string) =>
    api.get<
      ApiResponse<{active: boolean; code?: string; expiresAt?: string}>
    >(ApiPaths.deviceTamperCode(deviceId)),
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
  subscription: () => api.get<ApiResponse<SubscriptionInfo>>(ApiPaths.BILLING_SUBSCRIPTION),
  plans: () => api.get<ApiResponse<PlanInfo[]>>(ApiPaths.BILLING_PLANS),
  checkout: (planId: string) =>
    api.post<ApiResponse<{checkoutUrl: string; planId: string}>>(ApiPaths.BILLING_CHECKOUT, {
      planId,
    }),
};

export const auditApi = {
  list: (page = 0, size = 50, deviceId?: string) =>
    api.get<ApiResponse<PageResponse<AuditLogItem>>>(ApiPaths.AUDIT, {
      params: {page, size, ...(deviceId ? {deviceId} : {})},
    }),
};

export const monitoringApi = {
  notifications: (deviceId: string, page = 0, size = 20) =>
    api.get<ApiResponse<PageResponse<NotificationItem>>>(
      ApiPaths.deviceNotifications(deviceId),
      {params: {page, size}},
    ),
  locationHistory: (deviceId: string, page = 0, size = 50) =>
    api.get<ApiResponse<PageResponse<LocationPoint>>>(
      ApiPaths.deviceLocationHistory(deviceId),
      {params: {page, size}},
    ),
  usageStats: (deviceId: string, page = 0, size = 50) =>
    api.get<ApiResponse<PageResponse<UsageStatEntry>>>(
      ApiPaths.deviceUsageStats(deviceId),
      {params: {page, size}},
    ),
  installedApps: (deviceId: string) =>
    api.get<ApiResponse<InstalledAppItem[]>>(ApiPaths.deviceInstalledApps(deviceId)),
  geofences: (deviceId: string) =>
    api.get<ApiResponse<GeofenceZone[]>>(ApiPaths.deviceGeofences(deviceId)),
  startScreenShare: (deviceId: string) =>
    api.post<ApiResponse<ScreenShareSession>>(ApiPaths.MONITORING_START_SCREEN_SHARE, {
      deviceId,
    }),
  startStream: (
    deviceId: string,
    streamType: 'SCREEN' | 'CAMERA' | 'AUDIO',
    parentPin?: string,
  ) =>
    api.post<ApiResponse<ScreenShareSession>>(ApiPaths.MONITORING_START_STREAM, {
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
  stopScreenShare: (sessionId: string) =>
    api.post<ApiResponse<ScreenShareSession>>(
      ApiPaths.monitoringStopScreenShare(sessionId),
    ),
  switchCamera: (sessionId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.monitoringSwitchCamera(sessionId)),
};

export const alertsApi = {
  list: (page = 0, size = 20) =>
    api.get<ApiResponse<PageResponse<AlertItem>>>(ApiPaths.ALERTS, {params: {page, size}}),
};

export const timeExtensionApi = {
  listPending: () =>
    api.get<ApiResponse<TimeExtensionRequestItem[]>>(ApiPaths.TIME_EXTENSION_PENDING),
  listForDevice: (deviceId: string) =>
    api.get<ApiResponse<TimeExtensionRequestItem[]>>(
      ApiPaths.deviceTimeExtensionRequests(deviceId),
    ),
  respond: (requestId: string, approved: boolean, grantMinutes = 30) =>
    api.post<ApiResponse<TimeExtensionRequestItem>>(
      ApiPaths.timeExtensionRespond(requestId),
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

export interface AppBlockBundleRule {
  policyId: string;
  ruleId?: string;
  packageName: string;
  scheduleType?: string;
  scheduleJson?: Record<string, unknown>;
  enabled?: boolean;
}

export interface ScreenTimeBundleRule {
  policyId: string;
  ruleId?: string;
  packageName?: string | null;
  appCategory?: string | null;
  dailyLimitMinutes?: number;
  actionOnLimit?: string;
  warnAtPercent?: number;
  enabled?: boolean;
}

export interface PolicyBundle {
  policyVersion?: number;
  appBlocks?: AppBlockBundleRule[];
  screenTime?: ScreenTimeBundleRule[];
  deviceSchedules?: unknown[];
}

export const BLOCK_ALL_APPS_POLICY_NAME = 'Block All Apps';

export const policiesApi = {
  list: (type?: string) =>
    api.get<ApiResponse<PolicySummary[]>>(ApiPaths.POLICIES, {params: type ? {type} : {}}),
  deviceBundle: (deviceId: string) =>
    api.get<ApiResponse<PolicyBundle>>(ApiPaths.policyDeviceBundle(deviceId)),
  createAppBlock: (body: {
    name: string;
    deviceIds: string[];
    rules: Array<{packageName: string; scheduleType?: string; scheduleJson?: Record<string, unknown>}>;
  }) => api.post<ApiResponse<PolicySummary>>(ApiPaths.POLICY_APP_BLOCK, body),
  createScreenTime: (body: {
    name: string;
    deviceIds: string[];
    packageName?: string;
    appCategory?: string;
    dailyLimitMinutes: number;
    actionOnLimit?: string;
    warnAtPercent?: number;
    weeklyScheduleJson?: Record<string, {enabled: boolean; limitMinutes: number}>;
  }) => api.post<ApiResponse<PolicySummary>>(ApiPaths.POLICY_SCREEN_TIME, body),
  createDeviceSchedule: (body: {
    name: string;
    deviceIds: string[];
    scheduleType: string;
    startTime: string;
    endTime: string;
    daysOfWeek?: string[];
    lockDevice?: boolean;
  }) => api.post<ApiResponse<PolicySummary>>(ApiPaths.POLICY_DEVICE_SCHEDULE, body),
  createGeofence: (body: {
    name: string;
    deviceIds: string[];
    geofenceName: string;
    latitude: number;
    longitude: number;
    radiusM: number;
  }) => api.post<ApiResponse<PolicySummary>>(ApiPaths.POLICY_GEOFENCE, body),
  createStreaming: (body: {
    name: string;
    deviceIds: string[];
    screenShareAllowed?: boolean;
    cameraAllowed?: boolean;
    audioAllowed?: boolean;
  }) => api.post<ApiResponse<PolicySummary>>(ApiPaths.POLICY_STREAMING, body),
  delete: (policyId: string) => api.delete(ApiPaths.policy(policyId)),
};

export const parentPinApi = {
  setPin: (pin: string) => api.post(ApiPaths.PARENT_PIN, {pin}),
  verify: (pin: string) =>
    api.post<ApiResponse<{verified: boolean; silentStreamUnlocked?: boolean}>>(
      ApiPaths.PARENT_PIN_VERIFY,
      {pin},
    ),
  status: () =>
    api.get<ApiResponse<{pinConfigured: boolean; silentStreamUnlocked: boolean}>>(
      ApiPaths.PARENT_PIN_STATUS,
    ),
};

export const childLoginPasswordApi = {
  setPassword: (childLoginPassword: string) =>
    api.post(ApiPaths.PARENT_CHILD_LOGIN_PASSWORD, {childLoginPassword}),
  status: () =>
    api.get<ApiResponse<{configured: boolean; linkedDeviceCount: number}>>(
      ApiPaths.PARENT_CHILD_LOGIN_PASSWORD_STATUS,
    ),
};

export const familyHandleApi = {
  setHandle: (familyHandle: string) =>
    api.post<ApiResponse<{configured: boolean; familyHandle: string | null}>>(
      ApiPaths.PARENT_FAMILY_HANDLE,
      {familyHandle},
    ),
  status: () =>
    api.get<ApiResponse<{configured: boolean; familyHandle: string | null}>>(
      ApiPaths.PARENT_FAMILY_HANDLE_STATUS,
    ),
};

export const signalingApi = {
  post: (roomId: string, messageType: string, payload: string, sessionId?: string) =>
    api.post(ApiPaths.signaling(roomId), {messageType, payload, sessionId}),
  recent: (roomId: string) =>
    api.get<ApiResponse<Array<{id: string; messageType: string; payload: string; senderRole: string}>>>(
      ApiPaths.signaling(roomId),
    ),
};
