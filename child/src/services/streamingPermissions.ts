import {nativeModules} from '../nativeModules';
import {flowLog} from './flowLogger';
import {grantScreenCapture} from './grantScreenCapture';

const WAIT_MS = 500;
const WAIT_ATTEMPTS = 40; // 20s — user needs time to read and approve Android dialogs

async function waitFor(check: () => Promise<boolean>): Promise<boolean> {
  for (let i = 0; i < WAIT_ATTEMPTS; i++) {
    if (await check()) return true;
    await new Promise<void>(resolve => setTimeout(resolve, WAIT_MS));
  }
  return false;
}

async function waitForProjectionSessionReady(): Promise<boolean> {
  return waitFor(async () => {
    if (await nativeModules.isProjectionLostAfterProcessRestart()) {
      return false;
    }
    return nativeModules.ensureMediaProjectionSessionReady().catch(() => false);
  });
}

/**
 * Grant or restore screen capture silently (accessibility auto-approve when armed).
 * Used when a parent starts live view and consent was never granted or was lost.
 */
async function ensureScreenCaptureConsent(): Promise<boolean> {
  if (await nativeModules.isMediaProjectionGranted()) {
    return true;
  }
  flowLog('MEDIA_PROJECTION', 'auto-granting screen capture for incoming stream');
  let granted = await grantScreenCapture();
  if (!granted) {
    granted = await waitFor(() => nativeModules.isMediaProjectionGranted());
  }
  return granted;
}

/**
 * Re-obtain screen capture after process death (common when granting other permissions
 * in Android Settings — MIUI often kills the app). Uses accessibility auto-approve when armed.
 */
export async function recoverScreenCaptureIfLost(): Promise<boolean> {
  if (await nativeModules.isMediaProjectionGranted()) {
    return true;
  }
  flowLog('PERMISSION_FLOW', 'recoverScreenCaptureIfLost — re-requesting MediaProjection');
  const granted = await ensureScreenCaptureConsent();
  const debug = await nativeModules.getMediaProjectionDebugState().catch(() => ({}));
  flowLog('PERMISSION_FLOW', 'recoverScreenCaptureIfLost finished', {granted, debug});
  return granted;
}

/** Grant camera (and optional legacy mic/screen) once — used during pairing/setup. */
export async function ensureAllStreamingPermissions(): Promise<{
  camera: boolean;
  mic: boolean;
  screen: boolean;
}> {
  flowLog('PERMISSION_FLOW', 'ensureAllStreamingPermissions start');
  let camera = await nativeModules.isCameraGranted();
  if (!camera) {
    await nativeModules.requestCameraPermission();
    camera = await waitFor(() => nativeModules.isCameraGranted());
  }
  // Mic is not required for camera-only live view.
  const mic = await nativeModules.isMicGranted();

  let screen = await nativeModules.isMediaProjectionGranted();
  flowLog('PERMISSION_FLOW', 'screen capture check before consent', {screen});
  if (!screen) {
    screen = await ensureScreenCaptureConsent();
    const debug = await nativeModules.getMediaProjectionDebugState().catch(() => ({}));
    flowLog('PERMISSION_FLOW', 'MediaProjection debug state', debug ?? {});
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
    if (!camera) {
      await nativeModules.requestCameraPermission();
      if (!(await waitFor(() => nativeModules.isCameraGranted()))) {
        throw new Error('Camera permission required — open Permissions on the child app');
      }
    }
    return;
  }

  if (streamType === 'SCREEN') {
    if (!(await ensureScreenCaptureConsent())) {
      throw new Error(
        'Screen sharing couldn’t start. Open Clock Time on the child phone and finish the required permission steps.',
      );
    }

    let ready = await waitForProjectionSessionReady();
    if (!ready && (await nativeModules.isProjectionLostAfterProcessRestart())) {
      flowLog('MEDIA_PROJECTION', 'projection lost after restart — retry auto-grant');
      if (await ensureScreenCaptureConsent()) {
        ready = await waitForProjectionSessionReady();
      }
    }

    if (!ready) {
      if (await nativeModules.isProjectionLostAfterProcessRestart()) {
        throw new Error(
          'Screen sharing needs to be set up again after the child phone restarted. Open Clock Time, then try Start Screen again.',
        );
      }
      throw new Error(
        'Screen sharing isn’t ready yet. Open Clock Time briefly on the child phone, then try again.',
      );
    }
  }
}
