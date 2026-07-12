import {useEffect, useRef} from 'react';
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

  return null;
}
