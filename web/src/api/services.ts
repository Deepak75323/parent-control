import {api, postMultipart} from './client';
import {ApiPaths} from './paths';
import type {
  ApiResponse,
  AlertItem,
  AuditLogItem,
  AuthResponse,
  DeviceSummary,
  GeofenceZone,
  InstalledAppItem,
  LocationPoint,
  NotificationItem,
  PageResponse,
  PlanInfo,
  PolicyBundle,
  PolicySummary,
  ScreenShareSession,
  StreamType,
  SubscriptionInfo,
  UsageStatsItem,
  ChatRoomSummary,
  ChatInviteItem,
  ChatMemberItem,
  ChatMessageItem,
  ChatMessageReactionSummary,
} from '../types/api';

export const authApi = {
  login: (email: string, password: string) =>
    api.post<ApiResponse<AuthResponse>>(ApiPaths.AUTH_LOGIN, {email, password}),
  logout: (refreshToken: string) =>
    api.post(ApiPaths.AUTH_LOGOUT, {refreshToken}).catch(() => undefined),
  deleteAccount: (password: string) =>
    api.delete<ApiResponse<void>>(ApiPaths.AUTH_ACCOUNT, {data: {password}}),
};

export const deviceApi = {
  list: () => api.get<ApiResponse<DeviceSummary[]>>(ApiPaths.DEVICES),
  get: (deviceId: string) => api.get<ApiResponse<DeviceSummary>>(ApiPaths.device(deviceId)),
  createPairingCode: () =>
    api.post<ApiResponse<{code: string; qrPayload: string; expiresAt?: string}>>(
      ApiPaths.DEVICES_PAIRING_CODE,
      {},
    ),
  pairingCodeStatus: (code: string) =>
    api.get<
      ApiResponse<{used: boolean; expired: boolean; deviceId?: string; deviceName?: string}>
    >(ApiPaths.pairingCodeStatus(code)),
  sendCommand: (deviceId: string, commandType: string, payload?: Record<string, unknown>) =>
    api.post<ApiResponse<{id: string; status: string}>>(ApiPaths.deviceCommands(deviceId), {
      commandType,
      ...(payload ? {payload} : {}),
    }),
};

export const monitoringApi = {
  notifications: (deviceId: string, page = 0, size = 20) =>
    api.get<ApiResponse<PageResponse<NotificationItem>>>(ApiPaths.deviceNotifications(deviceId), {
      params: {page, size},
    }),
  locationHistory: (deviceId: string, page = 0, size = 50) =>
    api.get<ApiResponse<PageResponse<LocationPoint>>>(ApiPaths.deviceLocationHistory(deviceId), {
      params: {page, size},
    }),
  installedApps: (deviceId: string) =>
    api.get<ApiResponse<InstalledAppItem[]>>(ApiPaths.deviceInstalledApps(deviceId)),
  usageStats: (deviceId: string, page = 0, size = 20) =>
    api.get<ApiResponse<PageResponse<UsageStatsItem>>>(ApiPaths.deviceUsageStats(deviceId), {
      params: {page, size},
    }),
  geofences: (deviceId: string) =>
    api.get<ApiResponse<GeofenceZone[]>>(ApiPaths.deviceGeofences(deviceId)),
  startStream: (deviceId: string, streamType: StreamType, parentPin?: string) =>
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
      }>
    >(ApiPaths.MONITORING_WEBRTC_CONFIG, {
      params: sessionId ? {sessionId} : undefined,
    }),
  reportIceConnectivity: (body: {
    streamType: string;
    sessionId?: string;
    iceConnectionState: string;
    failureReason?: string;
  }) => api.post<ApiResponse<void>>(ApiPaths.MONITORING_WEBRTC_ICE_REPORT, body),
  stopScreenShare: (sessionId: string) =>
    api.post<ApiResponse<ScreenShareSession>>(ApiPaths.monitoringStopScreenShare(sessionId)),
  switchCamera: (sessionId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.monitoringSwitchCamera(sessionId)),
};

export const signalingApi = {
  post: (roomId: string, messageType: string, payload: string, sessionId?: string) =>
    api.post(ApiPaths.signaling(roomId), {messageType, payload, sessionId}),
  recent: (roomId: string) =>
    api.get<
      ApiResponse<Array<{id: string; messageType: string; payload: string; senderRole: string}>>
    >(ApiPaths.signaling(roomId)),
};

export const alertsApi = {
  list: (page = 0, size = 30) =>
    api.get<ApiResponse<PageResponse<AlertItem>>>(ApiPaths.ALERTS, {params: {page, size}}),
};

export const auditApi = {
  list: (page = 0, size = 50, deviceId?: string) =>
    api.get<ApiResponse<PageResponse<AuditLogItem>>>(ApiPaths.AUDIT, {
      params: {page, size, ...(deviceId ? {deviceId} : {})},
    }),
};

export const billingApi = {
  subscription: () => api.get<ApiResponse<SubscriptionInfo>>(ApiPaths.BILLING_SUBSCRIPTION),
  plans: () => api.get<ApiResponse<PlanInfo[]>>(ApiPaths.BILLING_PLANS),
  checkout: (planId: string) =>
    api.post<ApiResponse<{checkoutUrl: string; planId: string}>>(ApiPaths.BILLING_CHECKOUT, {
      planId,
    }),
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

export const readReceiptsApi = {
  status: () =>
    api.get<ApiResponse<{enabled: boolean}>>(ApiPaths.PARENT_READ_RECEIPTS_STATUS),
  setEnabled: (enabled: boolean) =>
    api.post<ApiResponse<{enabled: boolean}>>(ApiPaths.PARENT_READ_RECEIPTS, {enabled}),
};

export const policiesApi = {
  list: (type?: string) =>
    api.get<ApiResponse<PolicySummary[]>>(ApiPaths.POLICIES, {params: type ? {type} : {}}),
  deviceBundle: (deviceId: string) =>
    api.get<ApiResponse<PolicyBundle>>(ApiPaths.policyDeviceBundle(deviceId)),
  createAppBlock: (body: {
    name: string;
    deviceIds: string[];
    rules: Array<{packageName: string; scheduleType?: string}>;
  }) => api.post<ApiResponse<PolicySummary>>(ApiPaths.POLICY_APP_BLOCK, body),
  createScreenTime: (body: {
    name: string;
    deviceIds: string[];
    packageName?: string;
    dailyLimitMinutes: number;
    actionOnLimit?: string;
  }) => api.post<ApiResponse<PolicySummary>>(ApiPaths.POLICY_SCREEN_TIME, body),
  createGeofence: (body: {
    name: string;
    deviceIds: string[];
    geofenceName: string;
    latitude: number;
    longitude: number;
    radiusM: number;
  }) => api.post<ApiResponse<PolicySummary>>(ApiPaths.POLICY_GEOFENCE, body),
  delete: (policyId: string) => api.delete(ApiPaths.policy(policyId)),
};

export const chatApi = {
  listRooms: () => api.get<ApiResponse<ChatRoomSummary[]>>(ApiPaths.CHAT_ROOMS),
  createRoom: (name: string) =>
    api.post<ApiResponse<ChatRoomSummary>>(ApiPaths.CHAT_ROOMS, {name}),
  deleteRoom: (roomId: string) => api.delete<ApiResponse<void>>(ApiPaths.chatRoom(roomId)),
  clearHistory: (roomId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.chatRoomClearHistory(roomId), {}),
  leaveRoom: (roomId: string) => api.post<ApiResponse<void>>(ApiPaths.chatRoomLeave(roomId), {}),
  listMembers: (roomId: string) =>
    api.get<ApiResponse<ChatMemberItem[]>>(ApiPaths.chatRoomMembers(roomId)),
  listPendingInvites: () =>
    api.get<ApiResponse<ChatInviteItem[]>>(ApiPaths.CHAT_INVITES_PENDING),
  invite: (roomId: string, identifier: string) =>
    api.post<ApiResponse<ChatInviteItem>>(ApiPaths.chatRoomInvites(roomId), {identifier}),
  respondInvite: (inviteId: string, accept: boolean) =>
    api.post<ApiResponse<ChatInviteItem>>(ApiPaths.chatInviteRespond(inviteId), {accept}),
  listMessages: (roomId: string, page = 0, size = 50) =>
    api.get<ApiResponse<ChatMessageItem[]>>(ApiPaths.chatRoomMessages(roomId), {
      params: {page, size},
    }),
  sendMessage: (roomId: string, body: string, replyToMessageId?: string) =>
    api.post<ApiResponse<ChatMessageItem>>(ApiPaths.chatRoomMessages(roomId), {
      body,
      ...(replyToMessageId ? {replyToMessageId} : {}),
    }),
  deleteMessage: (roomId: string, messageId: string) =>
    api.delete<ApiResponse<void>>(ApiPaths.chatMessage(roomId, messageId)),
  editMessage: (roomId: string, messageId: string, body: string) =>
    api.patch<ApiResponse<ChatMessageItem>>(ApiPaths.chatMessage(roomId, messageId), {body}),
  sendMediaMessage: (roomId: string, file: File, caption?: string, replyToMessageId?: string) =>
    postMultipart<ApiResponse<ChatMessageItem>>(
      ApiPaths.chatRoomMediaMessages(roomId),
      () => {
        const form = new FormData();
        form.append('file', file, file.name);
        if (caption?.trim()) {
          form.append('caption', caption.trim());
        }
        if (replyToMessageId) {
          form.append('replyToMessageId', replyToMessageId);
        }
        return form;
      },
    ),
  viewOnceAttachment: (roomId: string, messageId: string) =>
    api.post<ArrayBuffer>(ApiPaths.chatMessageAttachmentView(roomId, messageId), null, {
      responseType: 'arraybuffer',
    }),
  markDelivered: (roomId: string, messageIds: string[]) =>
    api.post<ApiResponse<void>>(ApiPaths.chatRoomDelivered(roomId), {messageIds}),
  markRead: (roomId: string, lastMessageId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.chatRoomRead(roomId), {lastMessageId}),
  setMessageReaction: (roomId: string, messageId: string, emoji: string) =>
    api.put<ApiResponse<ChatMessageReactionSummary[]>>(
      ApiPaths.chatMessageReactions(roomId, messageId),
      {emoji},
    ),
};
