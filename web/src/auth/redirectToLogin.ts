/** Full-page redirect to login (works outside React Router, e.g. axios interceptors). */
export function redirectToLogin() {
  const base = (import.meta.env.BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';
  const loginPath = `${base}/login`;
  if (window.location.pathname === loginPath || window.location.pathname.endsWith('/login')) {
    return;
  }
  window.location.assign(loginPath);
}
