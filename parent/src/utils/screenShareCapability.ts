import type {DeviceSummary} from '../services/api';

export type MediaProjectionConsentState =
  | 'NEVER_GRANTED'
  | 'GRANTED_ACTIVE'
  | 'LOST_AFTER_PROCESS_DEATH'
  | 'REQUIRES_RECONSENT'
  | 'RECOVERY_PENDING'
  | 'RECOVERY_FAILED';

type ScreenShareDevice = Pick<
  DeviceSummary,
  'mediaProjectionGranted' | 'mediaProjectionConsentState'
>;

/** Consent states that block screen share even when mediaProjectionGranted is stale true. */
const BLOCKING_CONSENT_STATES: ReadonlySet<MediaProjectionConsentState> = new Set([
  'NEVER_GRANTED',
  'LOST_AFTER_PROCESS_DEATH',
  'REQUIRES_RECONSENT',
  'RECOVERY_PENDING',
  'RECOVERY_FAILED',
]);

/**
 * True only when the child device currently has live MediaProjection consent.
 * Rejects stale granted=true when consent state is not GRANTED_ACTIVE.
 */
export function isScreenShareCapable(device: ScreenShareDevice | null | undefined): boolean {
  if (!device || device.mediaProjectionGranted !== true) {
    return false;
  }
  const state = device.mediaProjectionConsentState as MediaProjectionConsentState | undefined;
  if (state == null) {
    return true;
  }
  if (BLOCKING_CONSENT_STATES.has(state)) {
    return false;
  }
  return state === 'GRANTED_ACTIVE';
}

/**
 * Merge REST device snapshot with live WebSocket status for screen-share gating.
 * Either source may lag after re-grant — capable if either reports GRANTED_ACTIVE.
 */
export function mergeScreenShareCapability(
  live: ScreenShareDevice | null | undefined,
  device: ScreenShareDevice | null | undefined,
): ScreenShareDevice {
  if (isScreenShareCapable(device)) {
    return {
      mediaProjectionGranted: true,
      mediaProjectionConsentState: 'GRANTED_ACTIVE',
    };
  }
  if (isScreenShareCapable(live)) {
    return {
      mediaProjectionGranted: live!.mediaProjectionGranted,
      mediaProjectionConsentState: live!.mediaProjectionConsentState,
    };
  }
  return {
    mediaProjectionGranted:
      device?.mediaProjectionGranted ?? live?.mediaProjectionGranted ?? false,
    mediaProjectionConsentState:
      device?.mediaProjectionConsentState ?? live?.mediaProjectionConsentState,
  };
}

export function isScreenShareCapableMerged(
  live: ScreenShareDevice | null | undefined,
  device: ScreenShareDevice | null | undefined,
): boolean {
  return isScreenShareCapable(live) || isScreenShareCapable(device);
}

/** True when silent (device-owner) native screen capture is available right now. */
export function isSilentScreenCapable(
  device: Pick<DeviceSummary, 'silentScreenCapable' | 'mediaProjectionGranted' | 'mediaProjectionConsentState'> | null | undefined,
): boolean {
  return device?.silentScreenCapable === true && isScreenShareCapable(device);
}

export const SCREEN_CAPTURE_UNAVAILABLE_MESSAGE =
  'Screen sharing needs to be set up again on the child phone (this often happens after a restart). ' +
  'Tap Start Screen to continue.';

export const SCREEN_CAPTURE_RECONSENT_MESSAGE =
  'Screen sharing needs to be set up again on the child phone. Tap Start Screen to continue.';

export const SCREEN_CAPTURE_RECOVERING_MESSAGE =
  'Screen sharing isn’t ready yet on the child phone. Please wait a moment, then try again.';

export const SCREEN_CAPTURE_RECOVERY_FAILED_MESSAGE =
  'Couldn’t start screen sharing on the child phone. Tap Start Screen again after permissions look complete in Clock Time.';

/** Parent-facing hint when screen share is blocked — uses consent state when available. */
export function screenShareBlockedMessage(device: ScreenShareDevice | null | undefined): string {
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
        'Screen sharing hasn’t been set up on the child phone yet. ' +
        'Complete permissions in Clock Time, or tap Start Screen to continue.'
      );
    default:
      return SCREEN_CAPTURE_UNAVAILABLE_MESSAGE;
  }
}

/** Short copy for compact Remote View card (1–2 lines). */
export function screenShareBlockedShortMessage(
  device: ScreenShareDevice | null | undefined,
): string {
  if (isScreenShareCapable(device)) return '';
  const state = device?.mediaProjectionConsentState;
  switch (state) {
    case 'NEVER_GRANTED':
      return 'Screen sharing isn’t set up on the child phone yet.';
    case 'LOST_AFTER_PROCESS_DEATH':
      return 'Screen sharing needs setup again after a restart.';
    case 'REQUIRES_RECONSENT':
      return 'Screen sharing needs to be set up again.';
    case 'RECOVERY_PENDING':
      return 'Screen sharing isn’t ready yet — wait a moment.';
    case 'RECOVERY_FAILED':
      return 'Couldn’t start screen sharing — try Start Screen again.';
    default:
      return 'Screen sharing isn’t available right now.';
  }
}
