/**
 * API paths — keep identical to parent RN ApiPaths / backend ApiConstants.
 */
const V1 = '/api/v1';

export const ApiPaths = {
  AUTH_LOGIN: `${V1}/auth/login`,
  AUTH_REFRESH: `${V1}/auth/refresh`,
  AUTH_LOGOUT: `${V1}/auth/logout`,
  AUTH_ACCOUNT: `${V1}/auth/account`,

  DEVICES: `${V1}/devices`,
  DEVICES_PAIRING_CODE: `${V1}/devices/pairing-code`,
  device: (deviceId: string) => `${V1}/devices/${deviceId}`,
  deviceNotifications: (deviceId: string) => `${V1}/devices/${deviceId}/notifications`,
  deviceLocationHistory: (deviceId: string) => `${V1}/devices/${deviceId}/location-history`,
  deviceInstalledApps: (deviceId: string) => `${V1}/devices/${deviceId}/installed-apps`,
  deviceUsageStats: (deviceId: string) => `${V1}/devices/${deviceId}/usage-stats`,
  deviceGeofences: (deviceId: string) => `${V1}/devices/${deviceId}/geofences`,
  deviceCommands: (deviceId: string) => `${V1}/devices/${deviceId}/commands`,
  pairingCodeStatus: (code: string) =>
    `${V1}/devices/pairing-code/${encodeURIComponent(code)}/status`,

  BILLING_SUBSCRIPTION: `${V1}/billing/subscription`,
  BILLING_PLANS: `${V1}/billing/plans`,
  BILLING_CHECKOUT: `${V1}/billing/checkout`,

  AUDIT: `${V1}/audit`,
  ALERTS: `${V1}/alerts`,
  POLICIES: `${V1}/policies`,
  POLICY_APP_BLOCK: `${V1}/policies/app-block`,
  POLICY_SCREEN_TIME: `${V1}/policies/screen-time`,
  POLICY_GEOFENCE: `${V1}/policies/geofence`,
  policy: (policyId: string) => `${V1}/policies/${policyId}`,
  policyDeviceBundle: (deviceId: string) => `${V1}/policies/devices/${deviceId}/bundle`,

  MONITORING_START_STREAM: `${V1}/monitoring/start-stream`,
  MONITORING_WEBRTC_CONFIG: `${V1}/monitoring/webrtc-config`,
  MONITORING_WEBRTC_ICE_REPORT: `${V1}/monitoring/webrtc-ice-report`,
  signaling: (roomId: string) => `${V1}/monitoring/signaling/${roomId}`,
  monitoringStopScreenShare: (sessionId: string) =>
    `${V1}/monitoring/stop-screen-share/${sessionId}`,
  monitoringSwitchCamera: (sessionId: string) =>
    `${V1}/monitoring/switch-camera/${sessionId}`,

  PARENT_PIN: `${V1}/parent/pin`,
  PARENT_PIN_VERIFY: `${V1}/parent/pin/verify`,
  PARENT_PIN_STATUS: `${V1}/parent/pin/status`,
  PARENT_FAMILY_HANDLE: `${V1}/parent/family-handle`,
  PARENT_FAMILY_HANDLE_STATUS: `${V1}/parent/family-handle/status`,
  PARENT_READ_RECEIPTS: `${V1}/parent/read-receipts`,
  PARENT_READ_RECEIPTS_STATUS: `${V1}/parent/read-receipts/status`,

  CHAT_ROOMS: `${V1}/chat/rooms`,
  chatRoom: (roomId: string) => `${V1}/chat/rooms/${roomId}`,
  chatRoomClearHistory: (roomId: string) => `${V1}/chat/rooms/${roomId}/clear-history`,
  chatRoomLeave: (roomId: string) => `${V1}/chat/rooms/${roomId}/leave`,
  chatRoomMembers: (roomId: string) => `${V1}/chat/rooms/${roomId}/members`,
  chatRoomInvites: (roomId: string) => `${V1}/chat/rooms/${roomId}/invites`,
  chatRoomMessages: (roomId: string) => `${V1}/chat/rooms/${roomId}/messages`,
  chatRoomDelivered: (roomId: string) => `${V1}/chat/rooms/${roomId}/delivered`,
  chatRoomRead: (roomId: string) => `${V1}/chat/rooms/${roomId}/read`,
  chatMessage: (roomId: string, messageId: string) =>
    `${V1}/chat/rooms/${roomId}/messages/${messageId}`,
  chatMessageReactions: (roomId: string, messageId: string) =>
    `${V1}/chat/rooms/${roomId}/messages/${messageId}/reactions`,
  chatRoomMediaMessages: (roomId: string) => `${V1}/chat/rooms/${roomId}/messages/media`,
  chatMessageAttachment: (roomId: string, messageId: string) =>
    `${V1}/chat/rooms/${roomId}/messages/${messageId}/attachment`,
  chatMessageAttachmentView: (roomId: string, messageId: string) =>
    `${V1}/chat/rooms/${roomId}/messages/${messageId}/attachment/view`,
  CHAT_INVITES_PENDING: `${V1}/chat/invites/pending`,
  chatInviteRespond: (inviteId: string) => `${V1}/chat/invites/${inviteId}/respond`,
} as const;
