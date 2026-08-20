/**
 * Central API path constants for the child React Native app.
 * Keep values identical to backend routes — do not invent new paths here.
 */
const V1 = '/api/v1';

export const ApiPaths = {
  V1,
  DEVICE: `${V1}/device`,
  DEVICE_PREFIX: `${V1}/device/`,
  DEVICE_PAIR: `${V1}/device/pair`,
  DEVICE_HEARTBEAT: `${V1}/device/heartbeat`,
  DEVICE_ANALYTICS_BATCH: `${V1}/device/analytics/batch`,
  DEVICE_PERMISSION_STATUS: `${V1}/device/permission-status`,
  DEVICE_NOTIFICATIONS: `${V1}/device/notifications`,
  DEVICE_LOCATION: `${V1}/device/location`,
  DEVICE_USAGE_STATS: `${V1}/device/usage-stats`,
  DEVICE_COMMANDS_PENDING: `${V1}/device/commands/pending`,
  DEVICE_TAMPER_ATTEMPT: `${V1}/device/tamper/attempt`,
  DEVICE_TAMPER_VERIFY: `${V1}/device/tamper/verify`,
  DEVICE_TAMPER_OFFLINE_PIN: `${V1}/device/tamper/offline-pin`,
  streamSessionFailure: (sessionId: string) =>
    `${V1}/device/stream-sessions/${sessionId}/failure`,
  commandAck: (commandId: string) => `${V1}/device/commands/${commandId}/ack`,

  ONBOARDING_AUTH: `${V1}/onboarding/authenticate`,
  ONBOARDING_RECOVERY: `${V1}/onboarding/recovery/request`,

  CHILD: `${V1}/child`,
  CHILD_PREFIX: `${V1}/child/`,
  CHILD_POLICIES: `${V1}/child/policies`,
  CHILD_POLICIES_ACK: `${V1}/child/policies/ack`,
  CHILD_STREAMING_PENDING: `${V1}/child/streaming/pending`,
  CHILD_STREAMING_ACTIVE: `${V1}/child/streaming/active`,

  EVENTS: `${V1}/events`,
  EVENTS_PREFIX: `${V1}/events/`,
  EVENTS_POLICY_VIOLATIONS: `${V1}/events/policy-violations`,

  MONITORING_SIGNALING: `${V1}/monitoring/signaling`,
  MONITORING_SIGNALING_PREFIX: `${V1}/monitoring/signaling/`,
  MONITORING_WEBRTC_CONFIG: `${V1}/monitoring/webrtc-config`,
  MONITORING_WEBRTC_ICE_REPORT: `${V1}/monitoring/webrtc-ice-report`,
  signaling: (roomId: string) => `${V1}/monitoring/signaling/${roomId}`,

  /** @deprecated prefer DEVICE_TAMPER_VERIFY */
  TAMPER_VERIFY: `${V1}/device/tamper/verify`,
  STREAMING: `${V1}/streaming`,
} as const;

export const HmacPathPrefixes = [
  ApiPaths.DEVICE_PREFIX,
  ApiPaths.CHILD_PREFIX,
  ApiPaths.EVENTS_PREFIX,
  ApiPaths.MONITORING_SIGNALING_PREFIX,
] as const;

export const UnauthenticatedPaths = [
  ApiPaths.DEVICE_PAIR,
  ApiPaths.ONBOARDING_AUTH,
  ApiPaths.ONBOARDING_RECOVERY,
] as const;
