import {Client, IMessage} from '@stomp/stompjs';
import {config} from '../utils/config';
import {useAuthStore} from '../store/authStore';
import {useRealtimeStore} from '../store/realtimeStore';
import {resubscribeSignalingRooms, clearSignalingSubscriptions} from './signalingWebSocket';
import {plog} from './pairingLog';
import {traceLog, traceError} from '../utils/traceLogger';
import type {AlertItem, NotificationItem, LocationPoint} from './api';

let client: Client | null = null;
let connectToken: string | null = null;

export function getWebSocketClient(): Client | null {
  return client;
}

function parseJson<T>(message: IMessage): T {
  return JSON.parse(message.body) as T;
}

function normalizeDeviceId(raw: unknown): string {
  return String(raw ?? '');
}

export function connectWebSocket(): void {
  const token = useAuthStore.getState().accessToken;
  if (!token) {
    plog('WS skip — no access token');
    return;
  }
  if (client?.active && connectToken === token) {
    return;
  }

  disconnectWebSocket();
  connectToken = token;
  const wsUrl = config.wsNativeUrl;
  plog('WS connecting', {url: wsUrl});

  client = new Client({
    brokerURL: wsUrl,
    webSocketFactory: () => new WebSocket(wsUrl),
    connectHeaders: {Authorization: `Bearer ${token}`},
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      plog('WS connected');
      traceLog('Socket', 'onConnect', 'STOMP connected', {url: wsUrl});
      useRealtimeStore.getState().setWsConnected(true);
      if (client) {
        resubscribeSignalingRooms(client);
      }

      client?.subscribe('/user/queue/device-status', msg => {
        const event = parseJson<{
          deviceId: string;
          deviceName?: string;
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
        }>(msg);
        const deviceId = normalizeDeviceId(event.deviceId);
        const store = useRealtimeStore.getState();
        // While a pairing code is active, only devices absent from the baseline snapshot
        // count as newly paired. Heartbeats from already-paired children must not
        // clear the code (deviceStatuses may be empty before the list API loads).
        const isNewlyPaired =
          deviceId &&
          store.pendingPairingCode &&
          store.pairingBaselineIds.size > 0 &&
          !store.pairingBaselineIds.has(deviceId);
        if (isNewlyPaired) {
          plog('WS device-status new device', {deviceId, deviceName: event.deviceName});
          store.notifyPairingComplete({
            deviceId,
            deviceName: event.deviceName?.trim() || 'Child device',
          });
        }
        store.upsertDeviceStatus({
          deviceId,
          status: event.status,
          batteryLevel: event.batteryLevel,
          charging: event.charging,
          networkType: event.networkType,
          latitude: event.latitude,
          longitude: event.longitude,
          foregroundApp: event.foregroundApp,
          lastSeenAt: event.lastSeenAt,
          mediaProjectionGranted: event.mediaProjectionGranted,
          mediaProjectionConsentState: event.mediaProjectionConsentState,
          silentScreenCapable: event.silentScreenCapable,
          protectionLevel: event.protectionLevel,
        });
      });

      client?.subscribe('/user/queue/device-paired', msg => {
        const event = parseJson<{
          deviceId: string;
          deviceName?: string;
          parentDisplayName?: string;
          eventType?: string;
        }>(msg);
        const deviceId = normalizeDeviceId(event.deviceId);
        const store = useRealtimeStore.getState();
        plog('WS device-paired', event);
        // Ignore stale/replay events when no pairing code is active — otherwise a
        // re-pair or reconnect clears the code before the parent can display it.
        if (!store.pendingPairingCode) {
          plog('WS device-paired ignored — no active pairing code');
          return;
        }
        store.notifyPairingComplete({
          deviceId,
          deviceName: event.deviceName?.trim() || 'Child device',
        });
      });

      client?.subscribe('/user/queue/notifications', msg => {
        const event = parseJson<{
          deviceId: string;
          id: string;
          packageName?: string;
          title?: string;
          body?: string;
          postedAt: string;
        }>(msg);
        const item: NotificationItem = {
          id: event.id,
          packageName: event.packageName,
          title: event.title,
          body: event.body,
          postedAt: event.postedAt,
        };
        useRealtimeStore.getState().prependNotification(item);
      });

      client?.subscribe('/user/queue/locations', msg => {
        const event = parseJson<{
          deviceId: string;
          id: string;
          latitude: number;
          longitude: number;
          accuracyM?: number;
          recordedAt: string;
        }>(msg);
        const point: LocationPoint = {
          id: event.id,
          latitude: event.latitude,
          longitude: event.longitude,
          accuracyM: event.accuracyM,
          recordedAt: event.recordedAt,
        };
        useRealtimeStore.getState().setLiveLocation(event.deviceId, point);
      });

      client?.subscribe('/user/queue/alerts', msg => {
        const event = parseJson<AlertItem>(msg);
        useRealtimeStore.getState().prependAlert(event);
      });

      client?.subscribe('/user/queue/policy-violations', msg => {
        const event = parseJson<{violationType: string; deviceId: string}>(msg);
        useRealtimeStore.getState().prependAlert({
          id: `pv-${Date.now()}`,
          deviceId: event.deviceId,
          alertType: event.violationType,
          severity: 'WARNING',
          title: 'Policy violation',
          message: event.violationType,
          createdAt: new Date().toISOString(),
        });
      });

      client?.subscribe('/user/queue/policy-updates', msg => {
        useRealtimeStore.getState().setLastPolicyUpdate(parseJson(msg).policyVersion);
      });

      client?.subscribe('/user/queue/geofence-events', msg => {
        const event = parseJson<{eventType: string; deviceId: string; geofenceName: string}>(msg);
        useRealtimeStore.getState().prependAlert({
          id: `geo-${Date.now()}`,
          deviceId: event.deviceId,
          alertType: event.eventType,
          severity: 'INFO',
          title: event.geofenceName,
          message: event.eventType.replace('GEOFENCE_', 'Geofence '),
          createdAt: new Date().toISOString(),
        });
      });

      client?.subscribe('/user/queue/streaming-state', msg => {
        const event = parseJson<{
          roomId: string;
          status: string;
          streamType: string;
          deviceId?: string;
          failureReason?: string;
          traceId?: string;
        }>(msg);
        traceLog('Socket', 'streaming-state', 'STOMP streaming-state received', event);
        useRealtimeStore.getState().setStreamingState(event);
      });
    },
    onDisconnect: () => {
      plog('WS disconnected');
      traceLog('Socket', 'onDisconnect', 'STOMP disconnected');
      useRealtimeStore.getState().setWsConnected(false);
    },
    onStompError: frame => {
      plog('WS stomp error', frame.headers.message);
      traceError('Socket', 'onStompError', 'STOMP error', frame.headers.message);
      useRealtimeStore.getState().setWsConnected(false);
    },
    onWebSocketClose: () => {
      plog('WS socket closed');
      traceLog('Socket', 'onWebSocketClose', 'WebSocket closed');
      useRealtimeStore.getState().setWsConnected(false);
    },
    onWebSocketError: event => {
      const message =
        event instanceof ErrorEvent
          ? event.message
          : event instanceof Event
            ? event.type
            : 'unknown';
      plog('WS transport error', message);
      traceError('Socket', 'onWebSocketError', 'WebSocket transport error', message);
      useRealtimeStore.getState().setWsConnected(false);
    },
  });

  client.activate();
}

export function disconnectWebSocket(): void {
  clearSignalingSubscriptions();
  if (client) {
    client.deactivate();
    client = null;
  }
  connectToken = null;
  useRealtimeStore.getState().setWsConnected(false);
}
