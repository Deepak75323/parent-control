/**
 * Blocks ServiceStatus menu navigations briefly after screen transitions.
 * Phantom touches from ChildLogin "Continue" or Diagnostics "Back to home"
 * can arrive 3–4s after ServiceStatus gains focus (delayed touch lift).
 */
export const SERVICE_STATUS_MENU_GUARD_MS = 2500;

/** Extra cooldown for Diagnostics — the usual phantom-tap target after Back to home. */
export const DEBUG_DASHBOARD_REOPEN_COOLDOWN_MS = 5000;

let suppressMenuUntil = 0;
let blockDebugDashboardUntil = 0;

export function suppressServiceStatusMenus(
  ms: number = SERVICE_STATUS_MENU_GUARD_MS,
): void {
  suppressMenuUntil = Math.max(suppressMenuUntil, Date.now() + ms);
}

export function isServiceStatusMenuSuppressed(): boolean {
  return Date.now() < suppressMenuUntil;
}

export function blockDebugDashboardReopen(
  ms: number = DEBUG_DASHBOARD_REOPEN_COOLDOWN_MS,
): void {
  blockDebugDashboardUntil = Math.max(blockDebugDashboardUntil, Date.now() + ms);
  // Diagnostics is the phantom target — keep general menu guard aligned.
  suppressServiceStatusMenus(ms);
}

export function isDebugDashboardReopenBlocked(): boolean {
  return Date.now() < blockDebugDashboardUntil;
}
