/** Must match backend PortalAccessRules.BOOTSTRAP_ADMIN_EMAIL */
export const PORTAL_BOOTSTRAP_ADMIN_EMAIL = 'anilkumar@gmail.com';

export function isPortalBootstrapAdmin(email: string | null | undefined): boolean {
  return Boolean(email && email.trim().toLowerCase() === PORTAL_BOOTSTRAP_ADMIN_EMAIL);
}
