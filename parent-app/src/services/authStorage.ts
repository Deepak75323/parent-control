import AsyncStorage from '@react-native-async-storage/async-storage';
import {resolveSecureAuthBridge} from './parentSecureAuthBridge';

const LEGACY_AUTH_KEY = 'pcp_parent_auth_v1';
const LOG_TAG = 'PcpAuthStorage';

export interface PersistedAuth {
  accessToken: string;
  refreshToken: string;
  email: string | null;
}

/** Dev-only rollback: allow plain AsyncStorage when secure bridge is absent. */
export function isInsecureAuthStorageAllowed(): boolean {
  return typeof __DEV__ !== 'undefined' && __DEV__;
}

function logAuthStorage(event: string, detail?: string): void {
  const msg = detail ? `${event}: ${detail}` : event;
  if (__DEV__) {
    console.info(`[${LOG_TAG}] ${msg}`);
  }
}

export class InsecureAuthStorageError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InsecureAuthStorageError';
  }
}

async function migrateFromAsyncStorage(
  bridge: NonNullable<ReturnType<typeof resolveSecureAuthBridge>>,
): Promise<PersistedAuth | null> {
  try {
    const raw = await AsyncStorage.getItem(LEGACY_AUTH_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PersistedAuth;
    if (!parsed.refreshToken) return null;
    await bridge.saveSecureAuth(JSON.stringify(parsed));
    await AsyncStorage.removeItem(LEGACY_AUTH_KEY);
    logAuthStorage('migrated legacy AsyncStorage auth to secure store');
    return parsed;
  } catch {
    return null;
  }
}

export async function loadPersistedAuth(): Promise<PersistedAuth | null> {
  try {
    const bridge = resolveSecureAuthBridge();
    if (bridge) {
      const raw = await bridge.loadSecureAuth();
      if (raw) {
        const parsed = JSON.parse(raw) as PersistedAuth;
        if (parsed.refreshToken) {
          logAuthStorage('loaded auth from secure store');
          return parsed;
        }
      }
      return migrateFromAsyncStorage(bridge);
    }
    if (!isInsecureAuthStorageAllowed()) {
      logAuthStorage('refused insecure load — secure bridge unavailable in release');
      return null;
    }
    logAuthStorage('insecure dev fallback load from AsyncStorage');
    const raw = await AsyncStorage.getItem(LEGACY_AUTH_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PersistedAuth;
    if (!parsed.refreshToken) return null;
    return parsed;
  } catch {
    return null;
  }
}

export async function savePersistedAuth(auth: PersistedAuth): Promise<void> {
  const json = JSON.stringify(auth);
  const bridge = resolveSecureAuthBridge();
  if (bridge) {
    await bridge.saveSecureAuth(json);
    await AsyncStorage.removeItem(LEGACY_AUTH_KEY);
    logAuthStorage('saved auth to secure store');
    return;
  }
  if (!isInsecureAuthStorageAllowed()) {
    logAuthStorage('refused insecure save — secure bridge unavailable in release');
    throw new InsecureAuthStorageError(
      'Secure credential storage is unavailable. Reinstall the parent app or use an Android build with native secure storage.',
    );
  }
  logAuthStorage('insecure dev fallback save to AsyncStorage');
  await AsyncStorage.setItem(LEGACY_AUTH_KEY, json);
}

export async function clearPersistedAuth(): Promise<void> {
  const bridge = resolveSecureAuthBridge();
  if (bridge) {
    await bridge.clearSecureAuth();
  }
  await AsyncStorage.removeItem(LEGACY_AUTH_KEY);
  logAuthStorage('cleared persisted auth');
}
