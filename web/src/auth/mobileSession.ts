/** Mobile web sessions end when the page is hidden or closed. */

export const MOBILE_SESSION_END_KEY = 'pcp_web_mobile_session_end';

export function isMobileWebClient(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  return window.matchMedia('(max-width: 900px)').matches;
}

export function markMobileSessionEnded(): void {
  if (!isMobileWebClient()) {
    return;
  }
  try {
    localStorage.setItem(MOBILE_SESSION_END_KEY, String(Date.now()));
  } catch {
    /* private mode / quota */
  }
}

export function clearMobileSessionEndedMark(): void {
  try {
    localStorage.removeItem(MOBILE_SESSION_END_KEY);
  } catch {
    /* ignore */
  }
}

export function wasMobileSessionEnded(): boolean {
  if (!isMobileWebClient()) {
    return false;
  }
  try {
    return localStorage.getItem(MOBILE_SESSION_END_KEY) != null;
  } catch {
    return false;
  }
}
