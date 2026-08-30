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
  mediaProjectionGranted?: boolean;
  mediaProjectionConsentState?: string;
  protectionLevel?: string;
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

export interface AlertItem {
  id: string;
  deviceId: string;
  alertType: string;
  severity: string;
  title: string;
  message?: string;
  createdAt: string;
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

export interface UsageStatsItem {
  id: string;
  deviceId?: string;
  packageName: string;
  usageMs: number;
  recordedAt: string;
  createdAt?: string;
}

export interface PolicySummary {
  id: string;
  name: string;
  type: string;
  deviceIds: string[];
  updatedAt: string;
}

export interface AppBlockBundleRule {
  policyId: string;
  ruleId?: string;
  packageName: string;
  scheduleType?: string;
  enabled?: boolean;
}

export interface ScreenTimeBundleRule {
  policyId: string;
  ruleId?: string;
  packageName?: string | null;
  dailyLimitMinutes?: number;
  actionOnLimit?: string;
  enabled?: boolean;
}

export interface PolicyBundle {
  policyVersion?: number;
  appBlocks?: AppBlockBundleRule[];
  screenTime?: ScreenTimeBundleRule[];
}

export interface DeviceStatusLive {
  deviceId: string;
  deviceName?: string;
  status?: string;
  batteryLevel?: number;
  charging?: boolean;
  networkType?: string;
  latitude?: number;
  longitude?: number;
  foregroundApp?: string;
  lastSeenAt?: string;
}

export interface AuditLogItem {
  id: string;
  action: string;
  resource?: string;
  deviceId?: string;
  ipAddress?: string;
  metadataJson?: string;
  createdAt: string;
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

export interface ScreenShareSession {
  sessionId: string;
  deviceId: string;
  signalingRoomId: string;
  streamType?: string;
  status: string;
  startedAt: string;
  message?: string;
}

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

export type StreamType = 'SCREEN' | 'CAMERA' | 'AUDIO';

export interface ChatRoomSummary {
  id: string;
  name: string;
  ownerUserId: string;
  owner: boolean;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ChatInviteItem {
  id: string;
  roomId: string;
  roomName: string;
  inviterUserId: string;
  inviterLabel: string;
  inviteeIdentifier: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED';
  createdAt: string;
}

export interface ChatMessageItem {
  id: string;
  roomId: string;
  senderUserId: string;
  senderLabel: string;
  body: string;
  createdAt: string;
}

