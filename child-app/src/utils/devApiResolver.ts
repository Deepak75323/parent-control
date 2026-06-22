import axios from 'axios';
import {nativeModules} from '../nativeModules';
import {slog} from '../services/streamDebug';

/** Fallback when DEV_LAN_HOST is unset — override in child-app/.env for Wi‑Fi-only dev. */
const DEFAULT_LAN_HOST = process.env.DEV_LAN_HOST?.trim() || '192.168.1.8';

/** Candidate dev API bases — tried in order until /actuator/health succeeds. */
function buildCandidates(): string[] {
  const fromEnv = process.env.API_BASE_URL?.trim();
  const lanHost = process.env.DEV_LAN_HOST?.trim() || DEFAULT_LAN_HOST;
  const list = [
    fromEnv,
    // USB + adb reverse (most reliable for a physical phone on a Mac)
    'http://127.0.0.1:8080',
    `http://${lanHost}:8080`,
    'http://10.0.2.2:8080',
  ].filter((v): v is string => !!v);
  return [...new Set(list.map(u => u.replace(/\/$/, '')))];
}

let resolvedApiBaseUrl: string | null = null;
let resolvePromise: Promise<string> | null = null;

export function getApiBaseUrl(): string {
  return resolvedApiBaseUrl ?? buildCandidates()[0] ?? 'http://127.0.0.1:8080';
}

export function getWsNativeUrl(): string {
  const base = getApiBaseUrl();
  return base.replace(/^http/, 'ws') + '/ws-native';
}

async function probe(baseUrl: string): Promise<boolean> {
  try {
    const res = await axios.get(`${baseUrl}/actuator/health`, {timeout: 4000});
    return res.status === 200 && (res.data as {status?: string})?.status === 'UP';
  } catch {
    return false;
  }
}

/** Find a reachable dev API host (USB adb reverse or same Wi‑Fi). */
export async function resolveApiBaseUrl(force = false): Promise<string> {
  if (resolvedApiBaseUrl && !force) {
    return resolvedApiBaseUrl;
  }
  if (resolvePromise && !force) {
    return resolvePromise;
  }

  resolvePromise = (async () => {
    const candidates = buildCandidates();
    slog('API', 'resolveApiBaseUrl probing', candidates);

    for (const base of candidates) {
      if (await probe(base)) {
        resolvedApiBaseUrl = base;
        if (nativeModules.isAndroid) {
          nativeModules.updateApiBaseUrl(base).catch(() => undefined);
        }
        slog('API', 'resolveApiBaseUrl ok', base);
        return base;
      }
    }

    resolvedApiBaseUrl = candidates[0] ?? 'http://127.0.0.1:8080';
    slog('API', 'resolveApiBaseUrl none reachable', resolvedApiBaseUrl);
    throw new Error(`Cannot reach backend (tried ${candidates.join(', ')})`);
  })();

  try {
    return await resolvePromise;
  } finally {
    resolvePromise = null;
  }
}

export async function isApiReachable(force = false): Promise<boolean> {
  try {
    await resolveApiBaseUrl(force);
    return true;
  } catch {
    return false;
  }
}

export function setResolvedApiBaseUrl(url: string): void {
  resolvedApiBaseUrl = url.replace(/\/$/, '');
  if (nativeModules.isAndroid) {
    nativeModules.updateApiBaseUrl(resolvedApiBaseUrl).catch(() => undefined);
  }
}
