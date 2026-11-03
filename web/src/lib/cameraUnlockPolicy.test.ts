import {describe, expect, it} from 'vitest';
import {
  cameraUnlockCanEnterPassword,
  cameraUnlockNeedsFingerprintFirst,
} from './cameraUnlockPolicy';

describe('cameraUnlockPolicy', () => {
  it('requires fingerprint first when one is registered', () => {
    expect(cameraUnlockNeedsFingerprintFirst(true)).toBe(true);
    expect(cameraUnlockNeedsFingerprintFirst(false)).toBe(false);
  });

  it('blocks camera-password step until biometric passes when fingerprint is registered', () => {
    expect(
      cameraUnlockCanEnterPassword({
        hasRegisteredFingerprint: true,
        biometricPassed: false,
      }),
    ).toBe(false);
    expect(
      cameraUnlockCanEnterPassword({
        hasRegisteredFingerprint: true,
        biometricPassed: true,
      }),
    ).toBe(true);
  });

  it('allows camera password only when no fingerprint is registered', () => {
    expect(
      cameraUnlockCanEnterPassword({
        hasRegisteredFingerprint: false,
        biometricPassed: false,
      }),
    ).toBe(true);
  });
});
