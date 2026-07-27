import {useEffect, useRef} from 'react';
import {AppState, type AppStateStatus} from 'react-native';
import {useAuthStore} from '../store/authStore';
import {useRealtimeStore} from '../store/realtimeStore';
import {connectWebSocket, disconnectWebSocket} from '../services/websocketService';

export function WebSocketLifecycle() {
  const accessToken = useAuthStore(s => s.accessToken);
  const hadTokenRef = useRef(false);

  useEffect(() => {
    if (accessToken) {
      hadTokenRef.current = true;
      connectWebSocket();
      return;
    }

    if (hadTokenRef.current) {
      disconnectWebSocket();
      useRealtimeStore.getState().reset();
      hadTokenRef.current = false;
    }
  }, [accessToken]);

  // Retry STOMP when returning to foreground if the handshake never completed.
  useEffect(() => {
    if (!accessToken) {
      return;
    }
    const onChange = (state: AppStateStatus) => {
      if (state !== 'active') {
        return;
      }
      if (!useRealtimeStore.getState().wsConnected) {
        connectWebSocket();
      }
    };
    const sub = AppState.addEventListener('change', onChange);
    return () => sub.remove();
  }, [accessToken]);

  return null;
}
