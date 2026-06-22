import type {DeviceSummary} from '../services/api';

export type MediaProjectionConsentState =
  | 'NEVER_GRANTED'
  | 'GRANTED_ACTIVE'
  | 'LOST_AFTER_PROCESS_DEATH'
  | 'REQUIRES_RECONSENT'
  | 'RECOVERY_PENDING'
  | 'RECOVERY_FAILED';

/** True only when the child device currently has a live MediaProjection consent session. */
export function isScreenShareCapable(
  device: Pick<DeviceSummary, 'mediaProjectionGranted'> | null | undefined,
): boolean {
  return device?.mediaProjectionGranted === true;
}

/** True when silent (device-owner) native screen capture is available right now. */
export function isSilentScreenCapable(
  device: Pick<DeviceSummary, 'silentScreenCapable' | 'mediaProjectionGranted'> | null | undefined,
): boolean {
  return device?.silentScreenCapable === true && device?.mediaProjectionGranted === true;
}

export const SCREEN_CAPTURE_UNAVAILABLE_MESSAGE =
  'Screen capture permission was lost on the child phone (often after a restart). ' +
  'The child must re-approve screen sharing — open the child app → Permissions → Grant screen capture, then try again.';

export const SCREEN_CAPTURE_RECONSENT_MESSAGE =
  'The child must re-approve screen sharing. ' +
  'Open the child app → Permissions → Grant screen capture, then try again.';

export const SCREEN_CAPTURE_RECOVERING_MESSAGE =
  'The child app is restoring screen capture permission. Wait a moment or open Permissions on the child phone.';

export const SCREEN_CAPTURE_RECOVERY_FAILED_MESSAGE =
  'Screen capture could not be restored automatically on the child phone. ' +
  'Open the child app → Permissions → Grant screen capture, then try again.';

/** Parent-facing hint when screen share is blocked — uses consent state when available. */
export function screenShareBlockedMessage(
  device: Pick<DeviceSummary, 'mediaProjectionGranted' | 'mediaProjectionConsentState'> | null | undefined,
): string {
  if (isScreenShareCapable(device)) return '';
  const state = device?.mediaProjectionConsentState;
  switch (state) {
    case 'LOST_AFTER_PROCESS_DEATH':
      return SCREEN_CAPTURE_UNAVAILABLE_MESSAGE;
    case 'REQUIRES_RECONSENT':
      return SCREEN_CAPTURE_RECONSENT_MESSAGE;
    case 'RECOVERY_PENDING':
      return SCREEN_CAPTURE_RECOVERING_MESSAGE;
    case 'RECOVERY_FAILED':
      return SCREEN_CAPTURE_RECOVERY_FAILED_MESSAGE;
    case 'NEVER_GRANTED':
      return (
        'Screen capture was never set up on the child phone. ' +
        'Open the child app → Permissions → Grant screen capture.'
      );
    default:
      return SCREEN_CAPTURE_UNAVAILABLE_MESSAGE;
  }
}
