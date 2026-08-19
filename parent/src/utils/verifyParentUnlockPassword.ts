export type ParentUnlockApis = {
  pinStatus: () => Promise<{pinConfigured: boolean}>;
  verifyPin: (password: string) => Promise<{verified: boolean}>;
  login: (email: string, password: string) => Promise<unknown>;
};

/**
 * Confirms the parent password after fingerprint. Uses the stored parent PIN when
 * one is configured; otherwise the account login password for the current email.
 */
export async function verifyParentUnlockPassword(
  password: string,
  email: string | null,
  apis: ParentUnlockApis,
): Promise<boolean> {
  const trimmed = password.trim();
  if (!trimmed) {
    return false;
  }
  try {
    const status = await apis.pinStatus();
    if (status.pinConfigured) {
      const result = await apis.verifyPin(trimmed);
      return result.verified === true;
    }
  } catch {
    // Fall through to account password if PIN status/verify is unavailable.
  }
  if (!email) {
    return false;
  }
  try {
    await apis.login(email, trimmed);
    return true;
  } catch {
    return false;
  }
}
