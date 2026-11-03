/**
 * Camera unlock policy: biometric (when registered) then camera password.
 * Password-only is allowed only when no fingerprint is registered on this browser.
 * The camera password is its own credential — never the chat decrypt code.
 */
export function cameraUnlockNeedsFingerprintFirst(hasRegisteredFingerprint: boolean): boolean {
  return hasRegisteredFingerprint;
}

export function cameraUnlockCanEnterPassword(opts: {
  hasRegisteredFingerprint: boolean;
  biometricPassed: boolean;
}): boolean {
  if (!opts.hasRegisteredFingerprint) {
    return true;
  }
  return opts.biometricPassed;
}
