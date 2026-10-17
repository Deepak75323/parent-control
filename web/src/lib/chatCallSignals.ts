import type {ChatCallLiveEvent} from '../realtime/realtimeStore';

type ChatCallSignalHandler = (event: ChatCallLiveEvent) => void;

const handlers = new Set<ChatCallSignalHandler>();

/** Deliver every CALL_SIGNAL immediately — React state batching drops ICE/offers. */
export function dispatchChatCallSignal(event: ChatCallLiveEvent) {
  for (const handler of handlers) {
    try {
      handler(event);
    } catch (e) {
      console.warn('chat call signal handler failed', e);
    }
  }
}

export function subscribeChatCallSignals(handler: ChatCallSignalHandler): () => void {
  handlers.add(handler);
  return () => {
    handlers.delete(handler);
  };
}
