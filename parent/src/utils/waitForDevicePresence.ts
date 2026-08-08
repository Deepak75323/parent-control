import {deviceApi, DeviceSummary} from '../services/api';
import {isDevicePresentOnline} from './devicePresence';

/**
 * Poll {@link deviceApi.get} for {@code deviceId} until {@link isDevicePresentOnline} is true or
 * the budget elapses. Optionally kicks off an FCM wake first so a child that briefly lost network
 * posts a fresh heartbeat within the same window instead of waiting for its 5-minute alarm.
 *
 * Kept independent of any React screen so both live view and future flows (audio, camera, ring)
 * can share the same "briefly-offline → try to wake → retry" behavior.
 */
export interface WaitForPresenceOptions {
  /** Total time budget in ms. Defaults to 8000 to stay under a comfortable UI wait. */
  timeoutMs?: number;
  /** Delay between poll attempts in ms. Defaults to 1500. */
  pollIntervalMs?: number;
  /** Send an FCM wake before the first poll. Defaults to true. */
  wake?: boolean;
  /** Called before each status fetch — useful to update UI copy. */
  onAttempt?: (attempt: number) => void;
  /** Injected clock/sleep for tests. */
  now?: () => number;
  sleep?: (ms: number) => Promise<void>;
}

export interface WaitForPresenceResult {
  present: boolean;
  device: DeviceSummary | null;
  attempts: number;
  wakeDispatched: boolean;
}

const defaultSleep = (ms: number) =>
  new Promise<void>(resolve => setTimeout(resolve, ms));

export async function waitForDevicePresence(
  deviceId: string,
  options: WaitForPresenceOptions = {},
): Promise<WaitForPresenceResult> {
  const {
    timeoutMs = 8000,
    pollIntervalMs = 1500,
    wake = true,
    onAttempt,
    now = () => Date.now(),
    sleep = defaultSleep,
  } = options;

  const started = now();
  let wakeDispatched = false;
  let attempts = 0;
  let lastDevice: DeviceSummary | null = null;

  if (wake) {
    try {
      const wakeRes = await deviceApi.wake(deviceId);
      wakeDispatched = wakeRes.data?.data?.fcmDispatched === true;
    } catch {
      // Wake is best-effort. If the endpoint is missing or fails, keep polling — the
      // child's own 5-minute heartbeat may still land within the window.
      wakeDispatched = false;
    }
  }

  while (true) {
    attempts += 1;
    onAttempt?.(attempts);

    try {
      const res = await deviceApi.get(deviceId);
      lastDevice = res.data?.data ?? null;
      if (
        lastDevice &&
        isDevicePresentOnline(lastDevice.status, lastDevice.lastSeenAt ?? null, now())
      ) {
        return {present: true, device: lastDevice, attempts, wakeDispatched};
      }
    } catch {
      // Ignore transient fetch errors — either the next poll picks it up or we time out.
    }

    if (now() - started + pollIntervalMs >= timeoutMs) {
      return {present: false, device: lastDevice, attempts, wakeDispatched};
    }
    await sleep(pollIntervalMs);
  }
}
