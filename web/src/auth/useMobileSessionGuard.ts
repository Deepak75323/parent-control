import {useEffect} from 'react';
import {redirectToLogin} from './redirectToLogin';
import {endMobileWebSession} from './endMobileWebSession';
import {isMobileWebClient} from './mobileSession';
import {useAuthStore} from './authStore';

/** On mobile web, end the session when the page is hidden or closed. */
export function useMobileSessionGuard(enabled: boolean) {
  useEffect(() => {
    if (!enabled || !isMobileWebClient()) {
      return;
    }

    const endSession = () => {
      endMobileWebSession({beacon: true});
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        endSession();
      }
    };

    const onPageHide = () => {
      endSession();
    };

    const onPageShow = (event: PageTransitionEvent) => {
      if (!event.persisted) {
        return;
      }
      endSession();
      if (!useAuthStore.getState().accessToken) {
        redirectToLogin();
      }
    };

    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('pagehide', onPageHide);
    window.addEventListener('pageshow', onPageShow);

    return () => {
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('pagehide', onPageHide);
      window.removeEventListener('pageshow', onPageShow);
    };
  }, [enabled]);
}
