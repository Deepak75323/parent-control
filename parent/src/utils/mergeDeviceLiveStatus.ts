import type {DeviceSummary} from '../services/api';
import type {DeviceStatusLive} from '../store/realtimeStore';

/** Overlay live WebSocket readiness fields onto the last REST snapshot. */
export function mergeDeviceWithLiveStatus(
  device: DeviceSummary,
  live?: DeviceStatusLive | null,
): DeviceSummary {
  if (!live) return device;

  const merged: DeviceSummary = {...device};

  if (live.accessibilityEnabled !== undefined) {
    merged.accessibilityEnabled = live.accessibilityEnabled;
  }
  if (live.accessibilityServiceConnected !== undefined) {
    merged.accessibilityServiceConnected = live.accessibilityServiceConnected;
  }
  if (live.permAccessibility !== undefined) {
    merged.permAccessibility = live.permAccessibility;
  }
  if (live.permBattery !== undefined) {
    merged.permBattery = live.permBattery;
  }
  if (live.permAutostart !== undefined) {
    merged.permAutostart = live.permAutostart;
  }
  if (live.permOverlay !== undefined) {
    merged.permOverlay = live.permOverlay;
  }
  if (live.permCamera !== undefined) {
    merged.permCamera = live.permCamera;
  }
  if (live.permMicrophone !== undefined) {
    merged.permMicrophone = live.permMicrophone;
  }
  if (live.permNotifications !== undefined) {
    merged.permNotifications = live.permNotifications;
  }
  if (live.permFullScreenIntent !== undefined) {
    merged.permFullScreenIntent = live.permFullScreenIntent;
  }
  if (live.monitoringDegraded !== undefined) {
    merged.monitoringDegraded = live.monitoringDegraded;
  }
  if (live.deviceReadinessScore !== undefined) {
    merged.deviceReadinessScore = live.deviceReadinessScore;
  }
  if (live.readinessFailureTags !== undefined) {
    merged.readinessFailureTags = live.readinessFailureTags;
  }

  return merged;
}
