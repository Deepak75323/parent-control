import {nativeModules} from '../nativeModules';
import {flowLog} from './flowLogger';

const WAIT_MS = 500;
const WAIT_ATTEMPTS = 40; // 20s — user needs time to read and approve Android dialogs

async function waitFor(check: () => Promise<boolean>): Promise<boolean> {
  for (let i = 0; i < WAIT_ATTEMPTS; i++) {
    if (await check()) return true;
    await new Promise<void>(resolve => setTimeout(resolve, WAIT_MS));
  }
  return false;
}

/**
 * Re-obtain screen capture after process death (common when granting other permissions
 * in Android Settings — MIUI often kills the app). Uses accessibility auto-approve when armed.
 */
export async function recoverScreenCaptureIfLost(): Promise<boolean> {
  if (await nativeModules.isMediaProjectionGranted()) {
    return true;
  }
  if (!(await nativeModules.needsScreenCaptureRecovery())) {
    return false;
  }
  flowLog('PERMISSION_FLOW', 'recoverScreenCaptureIfLost — re-requesting MediaProjection');
  try {
    await nativeModules.armScreenCaptureAutoApprove();
    let screen = await nativeModules.requestMediaProjectionConsent();
    if (!screen) {
      screen = await waitFor(() => nativeModules.isMediaProjectionGranted());
    }
    const debug = await nativeModules.getMediaProjectionDebugState().catch(() => ({}));
    flowLog('PERMISSION_FLOW', 'recoverScreenCaptureIfLost finished', {screen, debug});
    return screen;
  } finally {
    await nativeModules.disarmScreenCaptureAutoApprove().catch(() => undefined);
  }
}

/** Grant camera, mic, and screen capture once — used during pairing/setup. */
export async function ensureAllStreamingPermissions(): Promise<{
  camera: boolean;
  mic: boolean;
  screen: boolean;
}> {
  flowLog('PERMISSION_FLOW', 'ensureAllStreamingPermissions start');
  let camera = await nativeModules.isCameraGranted();
  let mic = await nativeModules.isMicGranted();
  if (!camera || !mic) {
    await nativeModules.requestCameraAndMicPermissions();
    camera = await waitFor(() => nativeModules.isCameraGranted());
    mic = await waitFor(() => nativeModules.isMicGranted());
  }

  let screen = await nativeModules.isMediaProjectionGranted();
  flowLog('PERMISSION_FLOW', 'screen capture check before consent', {screen});
  if (!screen) {
    try {
      await nativeModules.armScreenCaptureAutoApprove();
      flowLog('PERMISSION_FLOW', 'requesting MediaProjection consent');
      screen = await nativeModules.requestMediaProjectionConsent();
      flowLog('PERMISSION_FLOW', 'MediaProjection consent returned', {screen});
      if (!screen) {
        screen = await waitFor(() => nativeModules.isMediaProjectionGranted());
        flowLog('PERMISSION_FLOW', 'MediaProjection waitFor finished', {screen});
      }
      const debug = await nativeModules.getMediaProjectionDebugState().catch(() => ({}));
      flowLog('PERMISSION_FLOW', 'MediaProjection debug state', debug ?? {});
    } finally {
      await nativeModules.disarmScreenCaptureAutoApprove().catch(() => undefined);
    }
  }

  flowLog('PERMISSION_FLOW', 'ensureAllStreamingPermissions done', {camera, mic, screen});
  return {camera, mic, screen};
}

/** Ensure permissions for a specific live stream type before WebRTC starts. */
export async function ensurePermissionsForStream(streamType: string): Promise<void> {
  if (streamType === 'AUDIO') {
    const mic = await nativeModules.isMicGranted();
    if (!mic) {
      await nativeModules.requestCameraAndMicPermissions();
      if (!(await waitFor(() => nativeModules.isMicGranted()))) {
        throw new Error('Microphone permission required — open Permissions on the child app');
      }
    }
    return;
  }

  if (streamType === 'CAMERA') {
    const camera = await nativeModules.isCameraGranted();
    const mic = await nativeModules.isMicGranted();
    if (!camera || !mic) {
      await nativeModules.requestCameraAndMicPermissions();
      const ok =
        (await waitFor(() => nativeModules.isCameraGranted())) &&
        (await waitFor(() => nativeModules.isMicGranted()));
      if (!ok) {
        throw new Error('Camera and microphone required — open Permissions on the child app');
      }
    }
    return;
  }

  if (streamType === 'SCREEN') {
    if (await nativeModules.needsScreenCaptureRecovery()) {
      flowLog('MEDIA_PROJECTION', 'Screen capture needs recovery — attempting auto re-consent');
      if (!(await recoverScreenCaptureIfLost())) {
        throw new Error(
          'Screen capture consent was lost. Open the child app → Permissions → Grant screen capture again.',
        );
      }
    }
    if (!(await nativeModules.isMediaProjectionGranted())) {
      throw new Error(
        'Screen capture was not set up during pairing. Open the child app → complete pairing permissions once.',
      );
    }
    const ready = await waitFor(async () => {
      if (await nativeModules.isProjectionLostAfterProcessRestart()) {
        return false;
      }
      return nativeModules.ensureMediaProjectionSessionReady().catch(() => false);
    });
    if (!ready) {
      if (await nativeModules.isProjectionLostAfterProcessRestart()) {
        throw new Error(
          'Screen capture consent was lost after the app restarted. Open the child app → Permissions → Grant screen capture again.',
        );
      }
      throw new Error(
        'Screen capture session is not ready. Open the child app briefly, then try screen share again.',
      );
    }
    return;
  }
}
