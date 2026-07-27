/** Stable analytics event names for parent funnel / support triage. */
export const TelemetryEvents = {
  LOGIN_SUCCESS: 'auth.login_success',
  LOGIN_FAILURE: 'auth.login_failure',
  LOGOUT: 'auth.logout',
  REGISTER_SUCCESS: 'auth.register_success',
  PAIRING_CODE_CREATED: 'device.pairing_code_created',
  PAIRING_CODE_FAILED: 'device.pairing_code_failed',
  DEVICE_REVOKE: 'device.revoke_monitoring',
  STREAM_START: 'stream.start',
  STREAM_STOP: 'stream.stop',
  STREAM_AUTH_TEARDOWN: 'stream.auth_teardown',
  DELETE_ACCOUNT_SUCCESS: 'account.delete_success',
  DELETE_ACCOUNT_FAILURE: 'account.delete_failure',
  SUPPORT_EMAIL_OPENED: 'support.email_opened',
  SUPPORT_CORRELATION_COPIED: 'support.correlation_copied',
} as const;

export type TelemetryEventName = (typeof TelemetryEvents)[keyof typeof TelemetryEvents];
