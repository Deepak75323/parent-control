/** Device credentials — in-memory cache backed by native SharedPreferences. */

import {nativeModules} from '../nativeModules';
import {getApiBaseUrl} from '../utils/config';

let cachedToken: string | null = null;
let cachedHmacKey: string | null = null;
let cachedParentDisplayName: string | null = null;

export async function saveDeviceCredentials(
  token: string,
  hmacKey?: string | null,
  parentDisplayName?: string | null,
): Promise<void> {
  cachedToken = token;
  if (hmacKey) {
    cachedHmacKey = hmacKey;
  }
  if (parentDisplayName) {
    cachedParentDisplayName = parentDisplayName;
  }
  await nativeModules.saveSyncConfig(
    getApiBaseUrl(),
    token,
    hmacKey ?? null,
    parentDisplayName ?? cachedParentDisplayName,
  );
}

export async function getParentDisplayName(): Promise<string | null> {
  if (cachedParentDisplayName) {
    return cachedParentDisplayName;
  }
  if (nativeModules.isAndroid) {
    cachedParentDisplayName = await nativeModules.getParentDisplayName();
  }
  return cachedParentDisplayName;
}

export async function saveDeviceToken(token: string): Promise<void> {
  cachedToken = token;
}

export async function restoreDeviceCredentials(): Promise<string | null> {
  if (cachedToken) {
    return cachedToken;
  }
  if (!nativeModules.isAndroid) {
    return null;
  }
  const stored = await nativeModules.getStoredSyncConfig();
  if (stored.isPaired && stored.deviceToken) {
    cachedToken = stored.deviceToken;
    cachedHmacKey = stored.deviceHmacKey ?? null;
    cachedParentDisplayName = stored.parentDisplayName ?? null;
    return cachedToken;
  }
  return null;
}

export async function getDeviceToken(): Promise<string | null> {
  if (cachedToken) {
    return cachedToken;
  }
  return restoreDeviceCredentials();
}

export async function getDeviceHmacKey(): Promise<string | null> {
  if (cachedHmacKey) {
    return cachedHmacKey;
  }
  await restoreDeviceCredentials();
  return cachedHmacKey;
}

export async function clearDeviceCredentials(): Promise<void> {
  cachedToken = null;
  cachedHmacKey = null;
  cachedParentDisplayName = null;
  if (nativeModules.isAndroid) {
    await nativeModules.clearPairedCredentials().catch(() => undefined);
  }
}

/** @deprecated use clearDeviceCredentials */
export async function clearDeviceToken(): Promise<void> {
  await clearDeviceCredentials();
}
