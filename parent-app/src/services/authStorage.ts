import AsyncStorage from '@react-native-async-storage/async-storage';
import {resolveSecureAuthBridge} from './parentSecureAuthBridge';

const LEGACY_AUTH_KEY = 'pcp_parent_auth_v1';

export interface PersistedAuth {
  accessToken: string;
  refreshToken: string;
  email: string | null;
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
        if (parsed.refreshToken) return parsed;
      }
      return migrateFromAsyncStorage(bridge);
    }
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
    return;
  }
  await AsyncStorage.setItem(LEGACY_AUTH_KEY, json);
}

export async function clearPersistedAuth(): Promise<void> {
  const bridge = resolveSecureAuthBridge();
  if (bridge) {
    await bridge.clearSecureAuth();
  }
  await AsyncStorage.removeItem(LEGACY_AUTH_KEY);
}
