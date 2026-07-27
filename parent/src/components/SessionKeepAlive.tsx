import {useEffect, useRef} from 'react';
import {AppState, type AppStateStatus} from 'react-native';
import {useAuthStore} from '../store/authStore';
import {refreshAccessToken} from '../services/api';

/** Refresh a couple of minutes before access-token expiry so the parent stays signed in. */
const REFRESH_SKEW_MS = 2 * 60 * 1000;
const MIN_DELAY_MS = 5_000;

/**
 * Keeps the parent session alive by refreshing the access token before it expires.
 * Only a rejected refresh token (or explicit Sign out) clears the session.
 */
export function SessionKeepAlive() {
  const accessToken = useAuthStore(s => s.accessToken);
  const expiresAt = useAuthStore(s => s.accessTokenExpiresAtMs);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const clearTimer = () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };

    if (!accessToken || !expiresAt) {
      clearTimer();
      return;
    }

    const schedule = () => {
      clearTimer();
      const delay = Math.max(MIN_DELAY_MS, expiresAt - Date.now() - REFRESH_SKEW_MS);
      timerRef.current = setTimeout(() => {
        void refreshAccessToken().then(result => {
          if (result.status === 'invalid') {
            void useAuthStore.getState().logout();
            return;
          }
          if (result.status === 'transient') {
            // Retry soon while staying signed in.
            timerRef.current = setTimeout(() => {
              void refreshAccessToken();
            }, 30_000);
          }
        });
      }, delay);
    };

    schedule();

    const onAppState = (state: AppStateStatus) => {
      if (state === 'active') {
        const remaining = (expiresAt ?? 0) - Date.now();
        if (remaining <= REFRESH_SKEW_MS) {
          void refreshAccessToken();
        } else {
          schedule();
        }
      }
    };
    const sub = AppState.addEventListener('change', onAppState);

    return () => {
      clearTimer();
      sub.remove();
    };
  }, [accessToken, expiresAt]);

  return null;
}
