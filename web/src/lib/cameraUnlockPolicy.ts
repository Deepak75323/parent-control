/**
 * Camera unlock policy: biometric (when registered) then decrypt code.
 * Decrypt-only is allowed only when no fingerprint is registered on this browser.
 */
export function cameraUnlockNeedsFingerprintFirst(hasRegisteredFingerprint: boolean): boolean {
  return hasRegisteredFingerprint;
}

export function cameraUnlockCanEnterDecryptCode(opts: {
  hasRegisteredFingerprint: boolean;
  biometricPassed: boolean;
}): boolean {
  if (!opts.hasRegisteredFingerprint) {
    return true;
  }
  return opts.biometricPassed;
}
