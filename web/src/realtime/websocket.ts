import {Client, type IMessage} from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {useAuthStore} from '../auth/authStore';
import {dispatchChatCallSignal} from '../lib/chatCallSignals';
import {useRealtimeStore} from './realtimeStore';
import {
  clearSignalingSubscriptions,
  resubscribeSignalingRooms,
} from './signalingWebSocket';
import type {AlertItem, NotificationItem, LocationPoint, ChatInviteItem, ChatMessageItem} from '../types/api';

let client: Client | null = null;

export function getWebSocketClient(): Client | null {
  return client;
}

function parseJson<T>(message: IMessage): T {
  return JSON.parse(message.body) as T;
}

function wsUrl(): string {
  const fromEnv = (import.meta.env.VITE_WS_URL as string | undefined)?.trim();
  if (fromEnv) {
    return fromEnv.replace(/\/$/, '');
  }
  // Same-origin SockJS via Vite proxy
  return `${window.location.origin}/ws`;
}

export function connectWebSocket(): void {
  const token = useAuthStore.getState().accessToken;
  if (!token) {
    return;
  }
  if (client?.connected) {
    return;
  }

  disconnectWebSocket();

  const url = wsUrl();
  client = new Client({
    webSocketFactory: () => new SockJS(url) as unknown as WebSocket,
    connectHeaders: {Authorization: `Bearer ${token}`},
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
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
        }>(msg);
        const deviceId = String(event.deviceId ?? '');
        if (!deviceId) {
          return;
        }
        useRealtimeStore.getState().upsertDeviceStatus({
          deviceId,
          deviceName: event.deviceName,
          status: event.status,
          batteryLevel: event.batteryLevel,
          charging: event.charging,
          networkType: event.networkType,
          latitude: event.latitude,
          longitude: event.longitude,
          foregroundApp: event.foregroundApp,
          lastSeenAt: event.lastSeenAt,
        });
      });

      client?.subscribe('/user/queue/device-removed', msg => {
        const event = parseJson<{deviceId: string}>(msg);
        const deviceId = String(event.deviceId ?? '');
        if (deviceId) {
          useRealtimeStore.getState().removeDevice(deviceId);
        }
      });

      client?.subscribe('/user/queue/device-paired', () => {
        useRealtimeStore.getState().bumpDeviceList();
      });

      client?.subscribe('/user/queue/alerts', msg => {
        const event = parseJson<AlertItem>(msg);
        useRealtimeStore.getState().prependAlert(event);
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
          deviceId: event.deviceId,
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

      client?.subscribe('/user/queue/streaming-state', msg => {
        const event = parseJson<{
          roomId: string;
          status: string;
          streamType: string;
          deviceId?: string;
          failureReason?: string;
        }>(msg);
        useRealtimeStore.getState().setStreamingState(event);
      });

      client?.subscribe('/user/queue/chat-messages', msg => {
        const event = parseJson<
          | ChatMessageItem
          | {eventType: string; roomId: string; messageId?: string}
        >(msg);
        if ('eventType' in event && event.eventType === 'MESSAGE_DELETED' && event.messageId) {
          useRealtimeStore.getState().removeLiveChatMessage(event.messageId);
          return;
        }
        if ('eventType' in event && event.eventType === 'CHAT_CLEARED') {
          useRealtimeStore.getState().clearLiveChatMessagesForRoom(event.roomId);
          return;
        }
        if (
          'eventType' in event &&
          event.eventType === 'MESSAGE_RECEIPT_UPDATED' &&
          event.messageId &&
          'receiptStatus' in event
        ) {
          useRealtimeStore
            .getState()
            .updateLiveChatMessageReceipt(
              event.messageId,
              event.receiptStatus as ChatMessageItem['receiptStatus'],
            );
          return;
        }
        if (
          'eventType' in event &&
          event.eventType === 'MESSAGE_REACTIONS_UPDATED' &&
          event.messageId &&
          'reactions' in event
        ) {
          useRealtimeStore
            .getState()
            .updateLiveChatMessageReactions(
              event.messageId,
              event.reactions as ChatMessageItem['reactions'],
            );
          return;
        }
        if (
          'eventType' in event &&
          typeof event.eventType === 'string' &&
          event.eventType.startsWith('CALL_')
        ) {
          const callEvent = event as {
            eventType: string;
            roomId: string;
            callId: string;
            userId?: string;
            toUserId?: string;
            messageType?: string;
            payload?: string;
            status?: string;
          };
          // CALL_SIGNAL must not go through a single React state slot — ICE floods drop OFFER/ANSWER.
          if (callEvent.eventType === 'CALL_SIGNAL') {
            dispatchChatCallSignal({...callEvent, seq: 0});
            return;
          }
          useRealtimeStore.getState().pushChatCallEvent(callEvent);
          return;
        }
        if (
          'eventType' in event &&
          event.eventType === 'TYPING' &&
          'userId' in event &&
          typeof (event as {userId?: string}).userId === 'string'
        ) {
          const typingEvent = event as {
            roomId: string;
            userId: string;
            nickname?: string;
            label?: string;
            typing?: boolean;
          };
          useRealtimeStore.getState().setChatTyping(
            typingEvent.roomId,
            {
              userId: typingEvent.userId,
              nickname: typingEvent.nickname ?? '',
              label: typingEvent.label ?? '',
            },
            Boolean(typingEvent.typing),
          );
          return;
        }
        useRealtimeStore.getState().upsertLiveChatMessage(event as ChatMessageItem);
      });

      client?.subscribe('/user/queue/chat-invites', msg => {
        parseJson<ChatInviteItem>(msg);
        useRealtimeStore.getState().bumpChatInvites();
      });

      client?.subscribe('/user/queue/chat-rooms', msg => {
        const event = parseJson<{eventType: string; roomId?: string}>(msg);
        if (event.eventType === 'ROOM_DELETED' && event.roomId) {
          useRealtimeStore.getState().notifyChatRoomDeleted();
        } else if (
          (event.eventType === 'MEMBER_LEFT' || event.eventType === 'MEMBER_REMOVED') &&
          event.roomId
        ) {
          useRealtimeStore.getState().markChatRoomRemoved(event.roomId);
          useRealtimeStore.getState().notifyChatRoomDeleted();
        } else {
          useRealtimeStore.getState().bumpChatRooms();
        }
      });
    },
    onDisconnect: () => {
      useRealtimeStore.getState().setWsConnected(false);
    },
    onStompError: () => {
      useRealtimeStore.getState().setWsConnected(false);
    },
    onWebSocketClose: () => {
      useRealtimeStore.getState().setWsConnected(false);
    },
  });

  client.activate();
}

export function disconnectWebSocket(): void {
  clearSignalingSubscriptions();
  if (client) {
    void client.deactivate();
    client = null;
  }
  useRealtimeStore.getState().setWsConnected(false);
}
