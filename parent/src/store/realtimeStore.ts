import {create} from 'zustand';
import type {DeviceSummary, NotificationItem, LocationPoint, AlertItem} from '../services/api';

export interface DeviceStatusLive {
  deviceId: string;
  status?: string;
  batteryLevel?: number;
  charging?: boolean;
  networkType?: string;
  latitude?: number;
  longitude?: number;
  foregroundApp?: string;
  lastSeenAt?: string;
  mediaProjectionGranted?: boolean;
  mediaProjectionConsentState?: string;
  silentScreenCapable?: boolean;
  protectionLevel?: string;
  monitoringDegraded?: boolean;
  monitoringMode?: string;
  standbyBucket?: string;
  accessibilityEnabled?: boolean;
  accessibilityServiceConnected?: boolean;
  permAccessibility?: boolean;
  permBattery?: boolean;
  permAutostart?: boolean;
  permOverlay?: boolean;
  permCamera?: boolean;
  permMicrophone?: boolean;
  permNotifications?: boolean;
  permFullScreenIntent?: boolean;
  deviceReadinessScore?: number;
  readinessFailureTags?: string[];
}

interface StreamingStateLive {
  roomId: string;
  status: string;
  streamType: string;
  deviceId?: string;
  failureReason?: string;
}

interface RealtimeState {
  deviceStatuses: Record<string, DeviceStatusLive>;
  liveNotifications: NotificationItem[];
  liveLocations: Record<string, LocationPoint>;
  liveAlerts: AlertItem[];
  lastPolicyVersion?: string;
  streamingState?: StreamingStateLive;
  wsConnected: boolean;
  /** Incremented when parent should reload device list from API. */
  deviceListVersion: number;
  /** Incremented when a child completes pairing — triggers navigation. */
  pairingCompleteTick: number;
  lastPairedDevice?: {deviceId: string; deviceName: string};
  /** Dedupes rapid duplicate WS events for the same device. */
  lastPairingNotifiedAt: Record<string, number>;
  /** Active pairing code waiting for child to claim (app-level poll). */
  pendingPairingCode: string | null;
  codeExpiresAt: string | null;
  pairingStartedAt: number | null;
  pairingBaselineIds: Set<string>;
  pairingBaselineLastSeen: Record<string, string>;
  /** Status snapshot when the pairing code was created (detect re-pair of revoked devices). */
  pairingBaselineStatuses: Record<string, string>;
  setPendingPairingCode: (code: string, baselineDevices: DeviceSummary[], expiresAt?: string | null) => void;
  clearPendingPairingCode: () => void;
  bumpDeviceListVersion: () => void;
  notifyPairingComplete: (device: {deviceId: string; deviceName: string}) => void;
  /** Previous parent drops a child that was re-assigned to another family. */
  notifyDeviceRemoved: (deviceId: string) => void;
  setWsConnected: (connected: boolean) => void;
  upsertDeviceStatus: (event: DeviceStatusLive) => void;
  prependNotification: (item: NotificationItem) => void;
  setLiveLocation: (deviceId: string, point: LocationPoint) => void;
  prependAlert: (alert: AlertItem) => void;
  setLastPolicyUpdate: (version: string) => void;
  setStreamingState: (state: StreamingStateLive | undefined) => void;
  mergeDevices: (devices: DeviceSummary[]) => void;
  reset: () => void;
}

export const useRealtimeStore = create<RealtimeState>((set, get) => ({
  deviceStatuses: {},
  liveNotifications: [],
  liveLocations: {},
  liveAlerts: [],
  wsConnected: false,
  deviceListVersion: 0,
  pairingCompleteTick: 0,
  lastPairingNotifiedAt: {},
  pendingPairingCode: null,
  codeExpiresAt: null,
  pairingStartedAt: null,
  pairingBaselineIds: new Set<string>(),
  pairingBaselineLastSeen: {},
  pairingBaselineStatuses: {},
  setPendingPairingCode: (code, baselineDevices, expiresAt) => {
    get().mergeDevices(baselineDevices);
    set({
      pendingPairingCode: code,
      codeExpiresAt: expiresAt ?? null,
      pairingStartedAt: Date.now(),
      pairingBaselineIds: new Set(baselineDevices.map(d => d.id)),
      pairingBaselineLastSeen: Object.fromEntries(
        baselineDevices.map(d => [d.id, d.lastSeenAt ?? '']),
      ),
      pairingBaselineStatuses: Object.fromEntries(
        baselineDevices.map(d => [d.id, (d.status ?? '').toUpperCase()]),
      ),
      // Clear stale banner from a previous pairing session.
      lastPairedDevice: undefined,
    });
  },
  clearPendingPairingCode: () =>
    set({
      pendingPairingCode: null,
      codeExpiresAt: null,
      pairingStartedAt: null,
      pairingBaselineIds: new Set<string>(),
      pairingBaselineLastSeen: {},
      pairingBaselineStatuses: {},
    }),
  bumpDeviceListVersion: () => set(state => ({deviceListVersion: state.deviceListVersion + 1})),
  notifyPairingComplete: device => {
    const now = Date.now();
    const lastAt = get().lastPairingNotifiedAt[device.deviceId] ?? 0;
    if (now - lastAt < 2000) {
      return;
    }
    set(state => ({
      deviceListVersion: state.deviceListVersion + 1,
      pairingCompleteTick: state.pairingCompleteTick + 1,
      lastPairedDevice: device,
      lastPairingNotifiedAt: {...state.lastPairingNotifiedAt, [device.deviceId]: now},
      pendingPairingCode: null,
      codeExpiresAt: null,
      pairingStartedAt: null,
      pairingBaselineIds: new Set<string>(),
      pairingBaselineLastSeen: {},
      pairingBaselineStatuses: {},
    }));
  },
  notifyDeviceRemoved: deviceId => {
    if (!deviceId) {
      return;
    }
    set(state => {
      const {[deviceId]: _removed, ...deviceStatuses} = state.deviceStatuses;
      const {[deviceId]: _loc, ...liveLocations} = state.liveLocations;
      return {
        deviceStatuses,
        liveLocations,
        deviceListVersion: state.deviceListVersion + 1,
      };
    });
  },
  setWsConnected: wsConnected => set({wsConnected}),
  upsertDeviceStatus: event =>
    set(state => ({
      deviceStatuses: {
        ...state.deviceStatuses,
        [event.deviceId]: {...state.deviceStatuses[event.deviceId], ...event},
      },
    })),
  prependNotification: item =>
    set(state => ({
      liveNotifications: [item, ...state.liveNotifications].slice(0, 100),
    })),
  setLiveLocation: (deviceId, point) =>
    set(state => ({
      liveLocations: {...state.liveLocations, [deviceId]: point},
    })),
  prependAlert: alert =>
    set(state => ({
      liveAlerts: [alert, ...state.liveAlerts].slice(0, 50),
    })),
  setLastPolicyUpdate: version => set({lastPolicyVersion: version}),
  setStreamingState: streamingState => set({streamingState}),
  mergeDevices: devices => {
    const statuses = {...get().deviceStatuses};
    devices.forEach(d => {
      statuses[d.id] = {
        deviceId: d.id,
        status: d.status,
        batteryLevel: d.batteryLevel,
        charging: d.charging,
        networkType: d.networkType,
        latitude: d.lastLatitude,
        longitude: d.lastLongitude,
        lastSeenAt: d.lastSeenAt,
        mediaProjectionGranted: d.mediaProjectionGranted,
        mediaProjectionConsentState: d.mediaProjectionConsentState,
        silentScreenCapable: d.silentScreenCapable,
        protectionLevel: d.protectionLevel,
        monitoringDegraded: d.monitoringDegraded,
        monitoringMode: d.monitoringMode,
        standbyBucket: d.standbyBucket,
      };
    });
    set({deviceStatuses: statuses});
  },
  reset: () =>
    set({
      deviceStatuses: {},
      liveNotifications: [],
      liveLocations: {},
      liveAlerts: [],
      wsConnected: false,
      deviceListVersion: 0,
      pairingCompleteTick: 0,
      lastPairedDevice: undefined,
      lastPairingNotifiedAt: {},
      pendingPairingCode: null,
      codeExpiresAt: null,
      pairingStartedAt: null,
      pairingBaselineIds: new Set<string>(),
      pairingBaselineLastSeen: {},
      pairingBaselineStatuses: {},
    }),
}));
