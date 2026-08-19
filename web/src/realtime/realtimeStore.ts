import {create} from 'zustand';
import type {AlertItem, DeviceStatusLive, LocationPoint, NotificationItem} from '../types/api';

export type StreamingStateLive = {
  roomId: string;
  status: string;
  streamType: string;
  deviceId?: string;
  failureReason?: string;
};

interface RealtimeState {
  wsConnected: boolean;
  /** Bumped when a device is paired/removed so list pages can reload. */
  deviceListEpoch: number;
  deviceStatuses: Record<string, DeviceStatusLive>;
  removedDeviceIds: Record<string, true>;
  liveAlerts: AlertItem[];
  liveNotifications: NotificationItem[];
  liveLocations: Record<string, LocationPoint>;
  streamingState?: StreamingStateLive;
  setWsConnected: (connected: boolean) => void;
  bumpDeviceList: () => void;
  upsertDeviceStatus: (event: DeviceStatusLive) => void;
  removeDevice: (deviceId: string) => void;
  prependAlert: (alert: AlertItem) => void;
  prependNotification: (item: NotificationItem) => void;
  setLiveLocation: (deviceId: string, point: LocationPoint) => void;
  setStreamingState: (event: StreamingStateLive | undefined) => void;
  reset: () => void;
}

export const useRealtimeStore = create<RealtimeState>(set => ({
  wsConnected: false,
  deviceListEpoch: 0,
  deviceStatuses: {},
  removedDeviceIds: {},
  liveAlerts: [],
  liveNotifications: [],
  liveLocations: {},
  streamingState: undefined,
  setWsConnected: wsConnected => set({wsConnected}),
  bumpDeviceList: () => set(state => ({deviceListEpoch: state.deviceListEpoch + 1})),
  upsertDeviceStatus: event =>
    set(state => {
      const {[event.deviceId]: _cleared, ...removedDeviceIds} = state.removedDeviceIds;
      return {
        removedDeviceIds,
        deviceStatuses: {
          ...state.deviceStatuses,
          [event.deviceId]: {...state.deviceStatuses[event.deviceId], ...event},
        },
      };
    }),
  removeDevice: deviceId =>
    set(state => {
      const {[deviceId]: _removed, ...deviceStatuses} = state.deviceStatuses;
      const {[deviceId]: _loc, ...liveLocations} = state.liveLocations;
      return {
        deviceStatuses,
        liveLocations,
        removedDeviceIds: {...state.removedDeviceIds, [deviceId]: true},
        deviceListEpoch: state.deviceListEpoch + 1,
      };
    }),
  prependAlert: alert =>
    set(state => ({liveAlerts: [alert, ...state.liveAlerts].slice(0, 50)})),
  prependNotification: item =>
    set(state => ({liveNotifications: [item, ...state.liveNotifications].slice(0, 100)})),
  setLiveLocation: (deviceId, point) =>
    set(state => ({liveLocations: {...state.liveLocations, [deviceId]: point}})),
  setStreamingState: streamingState => set({streamingState}),
  reset: () =>
    set({
      wsConnected: false,
      deviceListEpoch: 0,
      deviceStatuses: {},
      removedDeviceIds: {},
      liveAlerts: [],
      liveNotifications: [],
      liveLocations: {},
      streamingState: undefined,
    }),
}));
