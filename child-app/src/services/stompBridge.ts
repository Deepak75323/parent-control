/**
 * P1-6: Signaling bridge — React Native must NOT open a parallel STOMP connection.
 * Native [NativeStompOwner] is the single authoritative STOMP owner; JS receives
 * WebRTC signaling messages via native events only.
 */
import {NativeEventEmitter, NativeModules, Platform} from 'react-native';
import {flowLog} from './flowLogger';
import {slog} from './streamDebug';
import {nativeModules} from '../nativeModules';

const signalingHandlers = new Map<string, (payload: Record<string, unknown>) => void>();
let signalingSubscription: {remove: () => void} | null = null;
let bridgeInitialized = false;

function attachSignalingListener(): void {
  if (Platform.OS !== 'android' || signalingSubscription) return;
  const emitter = new NativeEventEmitter(NativeModules.ChildMonitorNative);
  signalingSubscription = emitter.addListener(
    'nativeSignalingMessage',
    (event: {roomId?: string; body?: string}) => {
      const roomId = event.roomId;
      const body = event.body;
      if (!roomId || !body) return;
      const handler = signalingHandlers.get(roomId);
      if (!handler) return;
      try {
        const payload = JSON.parse(body) as Record<string, unknown>;
        slog('WS', 'signaling push (native STOMP)', {
          roomId,
          type: payload.messageType,
          from: payload.senderRole,
        });
        handler(payload);
      } catch (err) {
        slog('WS', 'signaling parse failed', String(err));
      }
    },
  );
}

async function ensureNativeStompBridge(): Promise<void> {
  if (Platform.OS !== 'android') {
    slog('WS', 'non-Android — native STOMP bridge N/A');
    return;
  }
  if (!bridgeInitialized) {
    attachSignalingListener();
    bridgeInitialized = true;
    flowLog('STOMP', 'native authoritative STOMP bridge initialized');
  }
  await nativeModules.ensureNativeStompConnected();
}

export async function ensureDeviceWebSocket(): Promise<void> {
  await ensureNativeStompBridge();
}

export function subscribeSignalingRoom(
  roomId: string,
  handler: (payload: Record<string, unknown>) => void,
): void {
  signalingHandlers.set(roomId, handler);
  void ensureNativeStompBridge().then(() => {
    nativeModules.subscribeNativeSignalingRoom(roomId).catch(err => {
      slog('WS', 'native signaling subscribe failed', String(err));
    });
  });
}

export function unsubscribeSignalingRoom(roomId: string): void {
  signalingHandlers.delete(roomId);
  nativeModules.unsubscribeNativeSignalingRoom(roomId).catch(() => undefined);
  slog('WS', 'unsubscribed signaling room', roomId);
}

/** Device topics are native-only — no-op for backward compatibility. */
export function subscribeStreamRequests(
  _handler: (event: {
    roomId: string;
    streamType: string;
    sessionId: string;
    cameraFacing?: string;
  }) => void,
): void {
  slog('WS', 'subscribeStreamRequests — native STOMP owner handles device topics');
}

export function subscribeStreamStop(
  _handler: (event: {sessionId?: string; roomId?: string}) => void,
): void {
  slog('WS', 'subscribeStreamStop — native STOMP owner handles device topics');
}

export function subscribeStreamControl(
  _handler: (event: {sessionId?: string; command?: string}) => void,
): void {
  slog('WS', 'subscribeStreamControl — native STOMP owner handles device topics');
}

export function subscribeDeviceCommands(
  _handler: (event: {commandId: string; commandType: string}) => void,
): void {
  slog('WS', 'subscribeDeviceCommands — native DeviceCommandProcessor handles commands');
}

export function pauseStreamWebSocketSubscriptions(): void {
  // No JS STOMP subscriptions to pause.
}

export function disconnectDeviceWebSocket(): void {
  signalingHandlers.clear();
  signalingSubscription?.remove();
  signalingSubscription = null;
  bridgeInitialized = false;
}

export async function getStompOwnershipHealth(): Promise<Record<string, unknown>> {
  return nativeModules.getStompOwnershipHealth();
}
