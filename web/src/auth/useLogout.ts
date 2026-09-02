import {useCallback} from 'react';
import {useNavigate} from 'react-router-dom';
import {authApi} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {useLiveStreamStore} from '../livestream/liveStreamStore';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {disconnectWebSocket} from '../realtime/websocket';

export function useLogout() {
  const refreshToken = useAuthStore(s => s.refreshToken);
  const clear = useAuthStore(s => s.clear);
  const navigate = useNavigate();

  return useCallback(async () => {
    if (refreshToken) {
      await authApi.logout(refreshToken);
    }
    disconnectWebSocket();
    useRealtimeStore.getState().reset();
    void useLiveStreamStore.getState().stop();
    useLiveStreamStore.getState().reset();
    clear();
    navigate('/login', {replace: true});
  }, [refreshToken, clear, navigate]);
}
