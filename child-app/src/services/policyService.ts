import {policyApi} from './api';
import {nativeModules} from '../nativeModules';

/** Background sync only — parent policy changes apply silently (no child alert). */
const POLICY_SYNC_MS = 2 * 60 * 1000;

let timer: ReturnType<typeof setInterval> | null = null;

export async function syncPolicies(): Promise<void> {
  if (nativeModules.isAndroid) {
    await nativeModules.syncPoliciesNow();
    return;
  }
  await policyApi.fetchBundle();
}

export function startPolicySync(): void {
  if (timer) return;
  syncPolicies().catch(() => undefined);
  timer = setInterval(() => {
    syncPolicies().catch(() => undefined);
  }, POLICY_SYNC_MS);
}

export function stopPolicySync(): void {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}
