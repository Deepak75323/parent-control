/** Read JWT `sub` without verifying signature (token is already trusted for API use). */
export function userIdFromAccessToken(token: string | null | undefined): string | null {
  if (!token) {
    return null;
  }
  try {
    const part = token.split('.')[1];
    if (!part) {
      return null;
    }
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    const payload = JSON.parse(json) as {sub?: unknown};
    return typeof payload.sub === 'string' && payload.sub ? payload.sub : null;
  } catch {
    return null;
  }
}
