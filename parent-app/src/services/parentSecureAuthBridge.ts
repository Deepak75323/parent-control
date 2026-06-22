import {NativeModules, Platform} from 'react-native';

export type SecureAuthBridge = {
  saveSecureAuth: (json: string) => Promise<void>;
  loadSecureAuth: () => Promise<string | null>;
  clearSecureAuth: () => Promise<void>;
};

/** Resolves Android EncryptedSharedPreferences bridge when available. */
export function resolveSecureAuthBridge(): SecureAuthBridge | null {
  if (Platform.OS !== 'android') {
    return null;
  }
  const mod = NativeModules.ParentNative as Partial<SecureAuthBridge> | undefined;
  if (!mod?.saveSecureAuth || !mod.loadSecureAuth || !mod.clearSecureAuth) {
    return null;
  }
  return mod as SecureAuthBridge;
}
