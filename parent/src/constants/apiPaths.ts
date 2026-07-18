/**
 * Central API path constants for the parent React Native app.
 * Keep values identical to backend {@code ApiConstants}.
 */
const V1 = '/api/v1';

export const ApiPaths = {
  V1,
  AUTH: `${V1}/auth`,
  AUTH_REGISTER: `${V1}/auth/register`,
  AUTH_LOGIN: `${V1}/auth/login`,
  AUTH_REFRESH: `${V1}/auth/refresh`,
  AUTH_FORGOT_PASSWORD: `${V1}/auth/forgot-password`,
  AUTH_RESET_PASSWORD: `${V1}/auth/reset-password`,
  AUTH_VERIFY_EMAIL: `${V1}/auth/verify-email`,
  AUTH_RESEND_VERIFICATION: `${V1}/auth/resend-verification`,
  AUTH_ACCOUNT: `${V1}/auth/account`,
  AUTH_PREFIX: `${V1}/auth/`,

  DEVICES: `${V1}/devices`,
  DEVICES_PAIRING_CODE: `${V1}/devices/pairing-code`,
  device: (deviceId: string) => `${V1}/devices/${deviceId}`,
  deviceCommands: (deviceId: string) => `${V1}/devices/${deviceId}/commands`,
  deviceTamperCode: (deviceId: string) => `${V1}/devices/${deviceId}/tamper-code`,
  deviceNotifications: (deviceId: string) => `${V1}/devices/${deviceId}/notifications`,
  deviceLocationHistory: (deviceId: string) => `${V1}/devices/${deviceId}/location-history`,
  deviceUsageStats: (deviceId: string) => `${V1}/devices/${deviceId}/usage-stats`,
  deviceInstalledApps: (deviceId: string) => `${V1}/devices/${deviceId}/installed-apps`,
  deviceGeofences: (deviceId: string) => `${V1}/devices/${deviceId}/geofences`,
  deviceTimeExtensionRequests: (deviceId: string) =>
    `${V1}/devices/${deviceId}/time-extension-requests`,
  pairingCodeStatus: (code: string) =>
    `${V1}/devices/pairing-code/${encodeURIComponent(code)}/status`,

  BILLING: `${V1}/billing`,
  BILLING_SUBSCRIPTION: `${V1}/billing/subscription`,
  BILLING_PLANS: `${V1}/billing/plans`,
  BILLING_CHECKOUT: `${V1}/billing/checkout`,

  AUDIT: `${V1}/audit`,
  ALERTS: `${V1}/alerts`,
  POLICIES: `${V1}/policies`,
  POLICY_APP_BLOCK: `${V1}/policies/app-block`,
  POLICY_SCREEN_TIME: `${V1}/policies/screen-time`,
  POLICY_DEVICE_SCHEDULE: `${V1}/policies/device-schedule`,
  POLICY_GEOFENCE: `${V1}/policies/geofence`,
  POLICY_STREAMING: `${V1}/policies/streaming`,
  policy: (policyId: string) => `${V1}/policies/${policyId}`,
  policyDeviceBundle: (deviceId: string) => `${V1}/policies/devices/${deviceId}/bundle`,

  MONITORING: `${V1}/monitoring`,
  MONITORING_SIGNALING: `${V1}/monitoring/signaling`,
  MONITORING_START_SCREEN_SHARE: `${V1}/monitoring/start-screen-share`,
  MONITORING_START_STREAM: `${V1}/monitoring/start-stream`,
  MONITORING_WEBRTC_CONFIG: `${V1}/monitoring/webrtc-config`,
  MONITORING_WEBRTC_ICE_REPORT: `${V1}/monitoring/webrtc-ice-report`,
  signaling: (roomId: string) => `${V1}/monitoring/signaling/${roomId}`,
  monitoringStopScreenShare: (sessionId: string) =>
    `${V1}/monitoring/stop-screen-share/${sessionId}`,
  monitoringSwitchCamera: (sessionId: string) =>
    `${V1}/monitoring/switch-camera/${sessionId}`,

  TIME_EXTENSION_PENDING: `${V1}/time-extension-requests/pending`,
  timeExtensionRespond: (requestId: string) =>
    `${V1}/time-extension-requests/${requestId}/respond`,

  PARENT_PIN: `${V1}/parent/pin`,
  PARENT_PIN_VERIFY: `${V1}/parent/pin/verify`,
  PARENT_PIN_STATUS: `${V1}/parent/pin/status`,
  PARENT_CHILD_LOGIN_PASSWORD: `${V1}/parent/child-login-password`,
  PARENT_CHILD_LOGIN_PASSWORD_STATUS: `${V1}/parent/child-login-password/status`,
  PARENT_FAMILY_HANDLE: `${V1}/parent/family-handle`,
  PARENT_FAMILY_HANDLE_STATUS: `${V1}/parent/family-handle/status`,

  STREAMING: `${V1}/streaming`,
  SIGNALING: `${V1}/signaling`,
} as const;
