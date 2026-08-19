import {NativeModules} from 'react-native';

export type FingerprintPromptResult = 'succeeded' | 'failed' | 'unavailable';

type ParentNativeBiometric = {
  authenticateBiometric?: () => Promise<string>;
};

/** System fingerprint sheet. Any enrolled strong biometric can match. */
export async function promptParentFingerprint(): Promise<FingerprintPromptResult> {
  const native = NativeModules.ParentNative as ParentNativeBiometric | undefined;
  if (!native?.authenticateBiometric) {
    return 'unavailable';
  }
  try {
    const result = await native.authenticateBiometric();
    if (result === 'succeeded' || result === 'failed' || result === 'unavailable') {
      return result;
    }
    return 'failed';
  } catch {
    return 'failed';
  }
}
