import axios from 'axios';
import {nativeModules} from '../nativeModules';
import {slog} from '../services/streamDebug';

/** Fallback when DEV_LAN_HOST is unset — override in child/.env for Wi‑Fi-only dev. */
const DEFAULT_LAN_HOST = process.env.DEV_LAN_HOST?.trim() || '192.168.1.5';

function requireProductionApiBaseUrl(): string {
  const fromEnv = process.env.API_BASE_URL?.trim();
  if (!fromEnv) {
    throw new Error(
      'API_BASE_URL must be set at build time for release builds. ' +
        'Configure it in child/.env before creating a production bundle.',
    );
  }
  return fromEnv.replace(/\/$/, '');
}

/** Candidate dev API bases — tried in order until /actuator/health succeeds. */
function buildCandidates(): string[] {
  if (!__DEV__) {
    return [requireProductionApiBaseUrl()];
  }
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
  if (!__DEV__) {
    return resolvedApiBaseUrl ?? requireProductionApiBaseUrl();
  }
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

/** Find a reachable API host (USB adb reverse, LAN, or production base). */
export async function resolveApiBaseUrl(force = false): Promise<string> {
  if (!__DEV__) {
    const base = requireProductionApiBaseUrl();
    const changed = resolvedApiBaseUrl !== base;
    resolvedApiBaseUrl = base;
    // Push once — calling updateApiBaseUrl on every axios request reconnects STOMP
    // and drops in-flight screen-share signaling.
    if (changed && nativeModules.isAndroid) {
      nativeModules.updateApiBaseUrl(base).catch(() => undefined);
    }
    return base;
  }
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
        const changed = resolvedApiBaseUrl !== base;
        resolvedApiBaseUrl = base;
        if (changed && nativeModules.isAndroid) {
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
  const next = url.replace(/\/$/, '');
  const changed = resolvedApiBaseUrl !== next;
  resolvedApiBaseUrl = next;
  if (changed && nativeModules.isAndroid) {
    nativeModules.updateApiBaseUrl(resolvedApiBaseUrl).catch(() => undefined);
  }
}
