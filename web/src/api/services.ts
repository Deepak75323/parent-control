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
  ChatCallItem,
  ChatChannelItem,
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

export type DeletedMessagesPinStatus = {
  pinConfigured: boolean;
  unlocked: boolean;
  verified?: boolean;
};

export const deletedMessagesPinApi = {
  status: () =>
    api.get<ApiResponse<DeletedMessagesPinStatus>>(ApiPaths.PARENT_DELETED_MESSAGES_PIN_STATUS),
  create: (pin: string, confirmPin: string) =>
    api.post(ApiPaths.PARENT_DELETED_MESSAGES_PIN, {pin, confirmPin}),
  change: (currentPin: string, pin: string, confirmPin: string) =>
    api.put(ApiPaths.PARENT_DELETED_MESSAGES_PIN, {currentPin, pin, confirmPin}),
  verify: (pin: string) =>
    api.post<ApiResponse<DeletedMessagesPinStatus>>(ApiPaths.PARENT_DELETED_MESSAGES_PIN_VERIFY, {
      pin,
    }),
  lock: () => api.post(ApiPaths.PARENT_DELETED_MESSAGES_PIN_LOCK),
  reset: (pin: string, confirmPin: string) =>
    api.post(ApiPaths.PARENT_DELETED_MESSAGES_PIN_RESET, {pin, confirmPin}),
};

export type AutoGreetingSettings = {
  enabled: boolean;
  roomId: string | null;
  morningTime: string;
  eveningStart: string;
  eveningEnd: string;
  timezone: string;
  morningMessage: string;
  eveningMessage: string;
};

export type CameraPasswordStatus = {
  pinConfigured: boolean;
  verified?: boolean;
};

export const autoGreetingApi = {
  get: () => api.get<ApiResponse<AutoGreetingSettings>>(ApiPaths.PARENT_AUTO_GREETING),
  update: (body: {
    enabled: boolean;
    roomId?: string | null;
    morningTime?: string;
    eveningStart?: string;
    eveningEnd?: string;
    timezone?: string;
  }) => api.put<ApiResponse<AutoGreetingSettings>>(ApiPaths.PARENT_AUTO_GREETING, body),
};

export const cameraPasswordApi = {
  status: () =>
    api.get<ApiResponse<CameraPasswordStatus>>(ApiPaths.PARENT_CAMERA_PASSWORD_STATUS),
  create: (pin: string, confirmPin: string) =>
    api.post(ApiPaths.PARENT_CAMERA_PASSWORD, {pin, confirmPin}),
  change: (currentPin: string, pin: string, confirmPin: string) =>
    api.put(ApiPaths.PARENT_CAMERA_PASSWORD, {currentPin, pin, confirmPin}),
  verify: (pin: string) =>
    api.post<ApiResponse<CameraPasswordStatus>>(ApiPaths.PARENT_CAMERA_PASSWORD_VERIFY, {pin}),
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

export const sharePresenceApi = {
  status: () =>
    api.get<ApiResponse<{enabled: boolean}>>(ApiPaths.PARENT_SHARE_PRESENCE_STATUS),
  setEnabled: (enabled: boolean) =>
    api.post<ApiResponse<{enabled: boolean}>>(ApiPaths.PARENT_SHARE_PRESENCE, {enabled}),
};

export type FriendRequestItem = {
  id: string;
  requesterUserId: string;
  addresseeUserId: string;
  status: string;
  identifier: string;
  counterpartLabel?: string;
  counterpartNickname?: string;
  dmRoomId?: string | null;
  createdAt?: string;
};

export type FriendSummaryItem = {
  friendshipId: string;
  userId: string;
  label: string;
  nickname: string;
  dmRoomId?: string | null;
};

export const friendsApi = {
  list: () => api.get<ApiResponse<FriendSummaryItem[]>>(ApiPaths.PARENT_FRIENDS),
  pendingIncoming: () =>
    api.get<ApiResponse<FriendRequestItem[]>>(ApiPaths.PARENT_FRIENDS_PENDING),
  pendingOutgoing: () =>
    api.get<ApiResponse<FriendRequestItem[]>>(ApiPaths.PARENT_FRIENDS_OUTGOING),
  sendRequest: (identifier: string) =>
    api.post<ApiResponse<FriendRequestItem>>(ApiPaths.PARENT_FRIENDS_REQUESTS, {identifier}),
  respond: (requestId: string, accept: boolean) =>
    api.post<ApiResponse<FriendRequestItem>>(ApiPaths.friendRequestRespond(requestId), {
      accept,
    }),
  openDm: (friendUserId: string) =>
    api.post<ApiResponse<FriendSummaryItem>>(ApiPaths.friendOpenDm(friendUserId)),
  remove: (friendUserId: string) =>
    api.delete<ApiResponse<void>>(ApiPaths.friendRemove(friendUserId)),
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

export type ChatMessageDeleteScope = 'FOR_ME' | 'FOR_EVERYONE';

export const chatApi = {
  listRooms: () => api.get<ApiResponse<ChatRoomSummary[]>>(ApiPaths.CHAT_ROOMS),
  createRoom: (name: string) =>
    api.post<ApiResponse<ChatRoomSummary>>(ApiPaths.CHAT_ROOMS, {name}),
  deleteRoom: (roomId: string) => api.delete<ApiResponse<void>>(ApiPaths.chatRoom(roomId)),
  clearHistory: (roomId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.chatRoomClearHistory(roomId), {}),
  clearChat: (roomId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.chatRoomClearChat(roomId), {}),
  leaveRoom: (roomId: string) => api.post<ApiResponse<void>>(ApiPaths.chatRoomLeave(roomId), {}),
  listMembers: (roomId: string) =>
    api.get<ApiResponse<ChatMemberItem[]>>(ApiPaths.chatRoomMembers(roomId)),
  partnerPresence: (roomId: string) =>
    api.get<
      ApiResponse<{
        userId?: string | null;
        online: boolean;
        lastSeenAt?: string | null;
        hidden: boolean;
      }>
    >(ApiPaths.chatRoomPresence(roomId)),
  membersPresence: (roomId: string) =>
    api.get<
      ApiResponse<
        Array<{
          userId: string;
          online: boolean;
          lastSeenAt?: string | null;
          hidden: boolean;
        }>
      >
    >(ApiPaths.chatRoomMembersPresence(roomId)),
  removeMember: (roomId: string, userId: string) =>
    api.delete<ApiResponse<void>>(ApiPaths.chatRoomMember(roomId, userId)),
  updateMember: (
    roomId: string,
    userId: string,
    body: {role?: 'ADMIN' | 'MEMBER'; muted?: boolean; canDecrypt?: boolean},
  ) => api.patch<ApiResponse<ChatMemberItem>>(ApiPaths.chatRoomMember(roomId, userId), body),
  transferOwnership: (roomId: string, userId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.chatRoomTransferOwnership(roomId), {userId}),
  setMembersPanelShared: (roomId: string, shared: boolean) =>
    api.patch<ApiResponse<ChatRoomSummary>>(ApiPaths.chatRoomMembersPanelShare(roomId), {
      shared,
    }),
  listChannels: (roomId: string) =>
    api.get<ApiResponse<ChatChannelItem[]>>(ApiPaths.chatRoomChannels(roomId)),
  createChannel: (roomId: string, name: string, channelType: 'TEXT' | 'VOICE') =>
    api.post<ApiResponse<ChatChannelItem>>(ApiPaths.chatRoomChannels(roomId), {
      name,
      channelType,
    }),
  startCall: (roomId: string, channelId?: string) =>
    api.post<ApiResponse<ChatCallItem>>(ApiPaths.chatRoomCalls(roomId), {
      ...(channelId ? {channelId} : {}),
    }),
  getActiveCall: (roomId: string, channelId?: string) =>
    api.get<ApiResponse<ChatCallItem | null>>(ApiPaths.chatRoomActiveCall(roomId), {
      params: channelId ? {channelId} : undefined,
    }),
  joinCall: (roomId: string, callId: string) =>
    api.post<ApiResponse<ChatCallItem>>(ApiPaths.chatRoomCallJoin(roomId, callId), {}),
  leaveCall: (roomId: string, callId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.chatRoomCallLeave(roomId, callId), {}),
  endCall: (roomId: string, callId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.chatRoomCallEnd(roomId, callId), {}),
  signalCall: (
    roomId: string,
    callId: string,
    body: {toUserId: string; messageType: 'OFFER' | 'ANSWER' | 'ICE'; payload: string},
  ) => api.post<ApiResponse<void>>(ApiPaths.chatRoomCallSignal(roomId, callId), body),
  listPendingInvites: () =>
    api.get<ApiResponse<ChatInviteItem[]>>(ApiPaths.CHAT_INVITES_PENDING),
  invite: (roomId: string, identifier: string) =>
    api.post<ApiResponse<ChatInviteItem>>(ApiPaths.chatRoomInvites(roomId), {identifier}),
  respondInvite: (inviteId: string, accept: boolean) =>
    api.post<ApiResponse<ChatInviteItem>>(ApiPaths.chatInviteRespond(inviteId), {accept}),
  listMessages: (
    roomId: string,
    page = 0,
    size = 50,
    channelId?: string,
    includeDeleted = false,
  ) =>
    api.get<ApiResponse<ChatMessageItem[]>>(ApiPaths.chatRoomMessages(roomId), {
      params: {
        page,
        size,
        ...(channelId ? {channelId} : {}),
        ...(includeDeleted ? {includeDeleted: true} : {}),
      },
    }),
  sendMessage: (roomId: string, body: string, replyToMessageId?: string, channelId?: string) =>
    api.post<ApiResponse<ChatMessageItem>>(ApiPaths.chatRoomMessages(roomId), {
      body,
      ...(replyToMessageId ? {replyToMessageId} : {}),
      ...(channelId ? {channelId} : {}),
    }),
  deleteMessage: (roomId: string, messageId: string, scope: ChatMessageDeleteScope = 'FOR_ME') =>
    api.delete<ApiResponse<void>>(ApiPaths.chatMessage(roomId, messageId), {params: {scope}}),
  editMessage: (roomId: string, messageId: string, body: string) =>
    api.patch<ApiResponse<ChatMessageItem>>(ApiPaths.chatMessage(roomId, messageId), {body}),
  sendMediaMessage: (
    roomId: string,
    file: File,
    caption?: string,
    replyToMessageId?: string,
    channelId?: string,
  ) =>
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
        if (channelId) {
          form.append('channelId', channelId);
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
  unlockDecrypt: (roomId: string, pin: string) =>
    api.post<ApiResponse<{cryptoSalt: string}>>(ApiPaths.chatRoomDecryptUnlock(roomId), {pin}),
  decryptStatus: () =>
    api.get<ApiResponse<{configured: boolean; cryptoSalt?: string | null}>>(
      ApiPaths.CHAT_DECRYPT_STATUS,
    ),
  createDecryptPin: (pin: string) =>
    api.post<ApiResponse<{cryptoSalt: string}>>(ApiPaths.CHAT_DECRYPT_PIN, {pin}),
  verifyDecryptPin: (pin: string) =>
    api.post<ApiResponse<void>>(ApiPaths.CHAT_DECRYPT_VERIFY, {pin}),
  resetDecryptPin: (roomId: string, newPin: string) =>
    api.post<ApiResponse<{cryptoSalt: string}>>(ApiPaths.chatRoomDecryptReset(roomId), {
      newPin,
    }),
  notifyTyping: (roomId: string, typing: boolean) =>
    api.post<ApiResponse<void>>(ApiPaths.chatRoomTyping(roomId), {typing}),
  markUnread: (roomId: string, messageId: string) =>
    api.post<ApiResponse<void>>(ApiPaths.chatMessageMarkUnread(roomId, messageId)),
  setMessageReaction: (roomId: string, messageId: string, emoji: string) =>
    api.put<ApiResponse<ChatMessageReactionSummary[]>>(
      ApiPaths.chatMessageReactions(roomId, messageId),
      {emoji},
    ),
};
