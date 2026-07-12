import {Alert, AppState} from 'react-native';
import {nativeModules} from '../nativeModules';
import {flowLog} from './flowLogger';
import {beginSuppressAutoLock, endSuppressAutoLock} from './autoLockGuard';

const SETTINGS_WAIT_MS = 120_000;

async function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function waitForAppForeground(timeoutMs = SETTINGS_WAIT_MS): Promise<boolean> {
  if (AppState.currentState === 'active') {
    await delay(350);
    return true;
  }
  return new Promise<boolean>(resolve => {
    const deadline = Date.now() + timeoutMs;
    const sub = AppState.addEventListener('change', state => {
      if (state === 'active') {
        sub.remove();
        clearTimeout(timer);
        delay(350).then(() => resolve(true));
      }
    });
    const timer = setTimeout(() => {
      sub.remove();
      resolve(false);
    }, Math.max(0, deadline - Date.now()));
  });
}

async function waitForAccessibilityConnected(timeoutMs = 25_000): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await nativeModules.isAccessibilityServiceConnected()) {
      return true;
    }
    await delay(400);
  }
  return false;
}

async function waitForScreenCaptureAutoApproveReady(timeoutMs = SETTINGS_WAIT_MS): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await nativeModules.isScreenCaptureAutoApproveReady()) {
      return true;
    }
    await delay(500);
  }
  return false;
}

/**
 * Ensures accessibility is on and connected so MediaProjection can be auto-approved.
 * Opens Accessibility settings when needed, then waits for the user to return.
 */
async function ensureAccessibilityForScreenCapture(): Promise<boolean> {
  if (await nativeModules.isScreenCaptureAutoApproveReady()) {
    return true;
  }

  if (!(await nativeModules.isAccessibilityEnabled())) {
    flowLog('PERMISSION_FLOW', 'grantScreenCapture — opening Accessibility settings first');
    await nativeModules.openAccessibilitySettings();
    const returned = await waitForAppForeground();
    if (!returned) {
      return false;
    }
  }

  if (!(await nativeModules.isAccessibilityEnabled())) {
    return false;
  }

  if (!(await nativeModules.isAccessibilityServiceConnected())) {
    const connected = await waitForAccessibilityConnected();
    if (!connected) {
      return false;
    }
  }

  return waitForScreenCaptureAutoApproveReady();
}

/**
 * Arms accessibility auto-approve and opens the system MediaProjection dialog.
 * Returns true when screen capture consent is stored.
 */
export async function grantScreenCapture(): Promise<boolean> {
  if (await nativeModules.isMediaProjectionGranted()) {
    return true;
  }

  // The system MediaProjection dialog backgrounds/foregrounds the app; suppress the
  // foreground re-lock for the whole flow so RootNavigator doesn't bounce back to
  // ChildLogin the moment the dialog resolves.
  beginSuppressAutoLock();
  try {
    const ready = await ensureAccessibilityForScreenCapture();
    if (!ready) {
      return false;
    }

    flowLog('PERMISSION_FLOW', 'grantScreenCapture — arming auto-approve');
    await nativeModules.armScreenCaptureAutoApprove();
    try {
      const granted = await nativeModules.requestMediaProjectionConsent();
      if (granted) {
        // Let MainActivity resume and deliver AppState 'active' while the guard
        // is still held — native resolves the promise before finish().
        await waitForAppForeground();
        await delay(800);
      }
      return granted;
    } finally {
      await nativeModules.disarmScreenCaptureAutoApprove().catch(() => undefined);
    }
  } finally {
    endSuppressAutoLock();
  }
}

/** Opens Accessibility first when needed, then grants screen capture with auto-approve. */
export async function grantScreenCaptureWithAlerts(): Promise<boolean> {
  if (await nativeModules.isMediaProjectionGranted()) {
    return true;
  }

  // Suppress the foreground re-lock for the WHOLE flow — not just the consent
  // dialog. `ensureAccessibilityForScreenCapture()` may open the Accessibility
  // settings screen, which backgrounds/foregrounds the app; if that round trip
  // ran unguarded, RootNavigator's AppState 'active' handler would bounce the
  // child back to ChildLogin ("Base Login") the moment they returned. The guard
  // is depth-counted, so the nested begin/end inside `grantScreenCapture()` is
  // safe.
  beginSuppressAutoLock();
  try {
    const ready = await ensureAccessibilityForScreenCapture();
    if (!ready) {
      const enabled = await nativeModules.isAccessibilityEnabled();
      Alert.alert(
        enabled ? 'Accessibility service not running' : 'Accessibility required',
        enabled
          ? 'Open Settings → Accessibility, turn Device Monitor off and on, then tap Grant screen capture again.'
          : 'Turn on Device Monitor in Accessibility settings. When you return, tap Grant screen capture again.',
      );
      return false;
    }

    const granted = await grantScreenCapture();
    if (!granted) {
      Alert.alert(
        'Screen capture not granted',
        'Approve Share screen on the system dialog, or enable Accessibility so it can be tapped automatically.',
      );
    }
    return granted;
  } finally {
    endSuppressAutoLock();
  }
}
