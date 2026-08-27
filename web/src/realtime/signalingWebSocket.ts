import {Client, type IMessage} from '@stomp/stompjs';
import {connectWebSocket, getWebSocketClient} from './websocket';

const signalingHandlers = new Map<string, (payload: Record<string, unknown>) => void>();
const activeSubscriptions = new Map<string, {unsubscribe: () => void}>();

function parseJson<T>(message: IMessage): T {
  return JSON.parse(message.body) as T;
}

function doSubscribe(roomId: string, handler: (payload: Record<string, unknown>) => void): void {
  const client = getWebSocketClient();
  if (!client?.connected) {
    return;
  }
  if (activeSubscriptions.has(roomId)) {
    return;
  }

  const sub = client.subscribe(`/topic/signaling/${roomId}`, msg => {
    handler(parseJson<Record<string, unknown>>(msg));
  });
  activeSubscriptions.set(roomId, sub);
}

export function subscribeSignalingRoom(
  roomId: string,
  handler: (payload: Record<string, unknown>) => void,
): void {
  signalingHandlers.set(roomId, handler);
  const client = getWebSocketClient();
  if (client?.connected) {
    doSubscribe(roomId, handler);
    return;
  }
  connectWebSocket();
}

export function unsubscribeSignalingRoom(roomId: string): void {
  signalingHandlers.delete(roomId);
  activeSubscriptions.get(roomId)?.unsubscribe();
  activeSubscriptions.delete(roomId);
}

export function resubscribeSignalingRooms(client: Client): void {
  activeSubscriptions.clear();
  for (const [roomId, handler] of signalingHandlers) {
    const sub = client.subscribe(`/topic/signaling/${roomId}`, msg => {
      handler(parseJson<Record<string, unknown>>(msg));
    });
    activeSubscriptions.set(roomId, sub);
  }
}

export function clearSignalingSubscriptions(): void {
  for (const sub of activeSubscriptions.values()) {
    sub.unsubscribe();
  }
  activeSubscriptions.clear();
  signalingHandlers.clear();
}
