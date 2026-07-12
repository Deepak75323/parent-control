import {monitoringApi, deviceApi} from './api';
import {getApiBaseUrl, resolveApiBaseUrl} from '../utils/config';
import {getDeviceToken} from './deviceStorage';
import {nativeModules} from '../nativeModules';
import {startPolicySync, stopPolicySync} from './policyService';
import {ensureDeviceWebSocket} from './deviceWebSocket';
import {slog, slogReset} from './streamDebug';

import {startStreamingListener, stopStreamingListener} from './streamingService';

const SYNC_INTERVAL_MS = 5 * 60 * 1000;
const COMMAND_POLL_INTERVAL_MS = 15 * 1000;

let timer: ReturnType<typeof setInterval> | null = null;
let commandTimer: ReturnType<typeof setInterval> | null = null;

async function syncNotifications(): Promise<void> {
  const pending = await nativeModules.getPendingNotifications();
  if (pending.length === 0) return;

  const notifications = pending.map(n => ({
    packageName: n.packageName,
    title: n.title,
    body: n.body,
    postedAt: new Date(n.postedAt).toISOString(),
  }));

  await monitoringApi.ingestNotifications(notifications);
  await nativeModules.clearPendingNotifications(pending.map(n => n.localId));
}

async function syncLocation(): Promise<void> {
  const loc = await nativeModules.getCurrentLocation();
  if (!loc) return;

  await monitoringApi.ingestLocation({
    latitude: loc.latitude,
    longitude: loc.longitude,
    accuracyM: loc.accuracyM,
    recordedAt: new Date().toISOString(),
  });
}

async function syncUsageStats(): Promise<void> {
  const entries = await nativeModules.getUsageStats();
  if (entries.length === 0) return;

  await monitoringApi.ingestUsageStats(
    entries.map(e => ({
      packageName: e.packageName,
      usageMs: Math.round(e.usageMs),
      recordedAt: e.recordedAt,
    })),
  );
}

async function syncHeartbeat(): Promise<void> {
  if (nativeModules.isAndroid) {
    await nativeModules.runSyncNow().catch(() => undefined);
    return;
  }
  const loc = await nativeModules.getCurrentLocation();
  const caps = null;
  await deviceApi.heartbeat({
    ...(loc
      ? {latitude: loc.latitude, longitude: loc.longitude}
      : {}),
    ...(caps
      ? {
          deviceOwnerActive: caps.deviceOwner,
          mediaProjectionGranted: caps.mediaProjectionGranted,
          silentScreenCapable: caps.silentScreenCapture,
          protectionLevel: caps.protectionLevel,
        }
      : {}),
  });
}

async function syncPermissionStatus(): Promise<void> {
  try {
    const [
      accessibility,
      usageStats,
      overlay,
      battery,
      mediaProjection,
      camera,
      mic,
      location,
      notification,
      deviceAdmin,
    ] = await Promise.all([
      nativeModules.isAccessibilityEnabled(),
      nativeModules.isUsageStatsGranted(),
      nativeModules.isOverlayGranted(),
      nativeModules.isBatteryOptimizationExempt(),
      nativeModules.isMediaProjectionGranted(),
      nativeModules.isCameraGranted(),
      nativeModules.isMicGranted(),
      nativeModules.isLocationGranted(),
      nativeModules.isNotificationListenerEnabled(),
      nativeModules.isDeviceAdminActive(),
    ]);
    await deviceApi.updatePermissionStatus({
      accessibility,
      usageStats,
      overlay,
      battery,
      screenCapture: mediaProjection && accessibility,
      camera,
      microphone: mic,
      location,
      notificationAccess: notification,
      deviceAdmin,
    });
  } catch {
    // Non-critical — do not fail the sync cycle
  }
}

async function syncNativeApiUrl(): Promise<void> {
  try {
    await resolveApiBaseUrl();
    await nativeModules.updateApiBaseUrl(getApiBaseUrl());
  } catch {
    // Native resolver will probe candidates on its own.
  }
}

async function pollDeviceCommands(): Promise<void> {
  const token = await getDeviceToken();
  if (!token) return;
  try {
    await nativeModules.runSyncNow();
  } catch {
    // Native sync owns command execution — do not ack from JS fallback (avoids false FAILED).
    slog('SYNC', 'native command poll failed — will retry');
  }
}

export async function runSyncCycle(): Promise<void> {
  const token = await getDeviceToken();
  if (!token) return;

  try {
    await syncNativeApiUrl();
    await syncNotifications();
    await syncLocation();
    await syncUsageStats();
    await syncHeartbeat();
    await syncPermissionStatus();
    await nativeModules.runSyncNow();
  } catch {
    // Native sync is fallback when RN HTTP fails
  }
}

export async function startMonitoringSync(
  deviceToken: string,
  deviceHmacKey?: string,
  parentDisplayName?: string,
): Promise<void> {
  slogReset();
  slog('SYNC', 'startMonitoringSync begin', {
    api: getApiBaseUrl(),
    hasToken: !!deviceToken,
    hasHmac: !!deviceHmacKey,
    parentDisplayName,
  });
  await resolveApiBaseUrl().catch(() => undefined);
  await nativeModules.saveSyncConfig(
    getApiBaseUrl(),
    deviceToken,
    deviceHmacKey ?? null,
    parentDisplayName ?? null,
  );
  slog('SYNC', 'saveSyncConfig done');
  await nativeModules.applyTamperProtection();
  slog('SYNC', 'tamper protection applied');
  await nativeModules.startForegroundService();
  slog('SYNC', 'foreground service started');
  // Activate the JS streaming listener NOW — before the sync cycle does any
  // network I/O — so that any stream request that arrives while runSyncCycle
  // is in-flight is handled by the JS path instead of being routed to the
  // native headless path by the coordinator (which still saw jsListenerActive=false).
  startStreamingListener();
  await nativeModules.setJsStreamingListenerActive(true);
  slog('SYNC', 'streaming listener activated early');
  await runSyncCycle();
  slog('SYNC', 'initial sync cycle done');

  if (timer) clearInterval(timer);
  timer = setInterval(runSyncCycle, SYNC_INTERVAL_MS);
  if (commandTimer) clearInterval(commandTimer);
  commandTimer = setInterval(() => {
    void pollDeviceCommands();
  }, COMMAND_POLL_INTERVAL_MS);
  ensureDeviceWebSocket().catch(() => undefined);
  startPolicySync();
  slog('SYNC', 'startMonitoringSync complete — streaming listener active');
}

export function stopMonitoringSync(): void {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
  if (commandTimer) {
    clearInterval(commandTimer);
    commandTimer = null;
  }
  stopPolicySync();
  stopStreamingListener();
  nativeModules.setJsStreamingListenerActive(false).catch(() => undefined);
  nativeModules.stopForegroundService();
}
