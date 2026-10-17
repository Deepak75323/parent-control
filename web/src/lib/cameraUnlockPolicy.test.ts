import {describe, expect, it} from 'vitest';
import {
  cameraUnlockCanEnterDecryptCode,
  cameraUnlockNeedsFingerprintFirst,
} from './cameraUnlockPolicy';

describe('cameraUnlockPolicy', () => {
  it('requires fingerprint first when one is registered', () => {
    expect(cameraUnlockNeedsFingerprintFirst(true)).toBe(true);
    expect(cameraUnlockNeedsFingerprintFirst(false)).toBe(false);
  });

  it('blocks decrypt-code step until biometric passes when fingerprint is registered', () => {
    expect(
      cameraUnlockCanEnterDecryptCode({
        hasRegisteredFingerprint: true,
        biometricPassed: false,
      }),
    ).toBe(false);
    expect(
      cameraUnlockCanEnterDecryptCode({
        hasRegisteredFingerprint: true,
        biometricPassed: true,
      }),
    ).toBe(true);
  });

  it('allows decrypt-code only when no fingerprint is registered', () => {
    expect(
      cameraUnlockCanEnterDecryptCode({
        hasRegisteredFingerprint: false,
        biometricPassed: false,
      }),
    ).toBe(true);
  });
});
