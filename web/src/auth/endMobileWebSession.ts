import {ApiPaths} from '../api/paths';
import {useLiveStreamStore} from '../livestream/liveStreamStore';
import {useRealtimeStore} from '../realtime/realtimeStore';
import {disconnectWebSocket} from '../realtime/websocket';
import {useAuthStore} from './authStore';
import {isMobileWebClient, markMobileSessionEnded} from './mobileSession';

function logoutBeacon(refreshToken: string): void {
  const apiBase =
    (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';
  const url = `${apiBase}${ApiPaths.AUTH_LOGOUT}`;
  const body = JSON.stringify({refreshToken});
  try {
    if (typeof navigator.sendBeacon === 'function') {
      navigator.sendBeacon(url, new Blob([body], {type: 'application/json'}));
    }
  } catch {
    /* tab may already be closing */
  }
}

/** Clear local auth on mobile when the browser tab/app is hidden or closed. */
export function endMobileWebSession(options?: {beacon?: boolean}): void {
  if (!isMobileWebClient()) {
    return;
  }

  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken && !useAuthStore.getState().accessToken) {
    markMobileSessionEnded();
    return;
  }

  if (options?.beacon !== false && refreshToken) {
    logoutBeacon(refreshToken);
  }

  disconnectWebSocket();
  useRealtimeStore.getState().reset();
  void useLiveStreamStore.getState().stop();
  useLiveStreamStore.getState().reset();
  useAuthStore.getState().clear();
  markMobileSessionEnded();
}
