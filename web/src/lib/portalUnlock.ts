import type {ParentProfile} from '../types/api';

export type PortalUnlockLoginResult = {
  accessToken: string;
  refreshToken: string;
  email: string;
  profile: ParentProfile | null;
};

export type PortalUnlockControl = {
  disabled: boolean;
  canControl?: boolean;
};

export type PortalUnlockDeps = {
  email: string;
  password: string;
  login: (email: string, password: string) => Promise<PortalUnlockLoginResult>;
  setDisabled: (disabled: boolean, password: string) => Promise<PortalUnlockControl>;
  setTokens: (
    access: string,
    refresh: string,
    email?: string,
    profile?: ParentProfile | null,
  ) => void;
};

/** Always show unlock fields when the kill-switch is on (not only when canControl is known). */
export function shouldShowPortalUnlockForm(disabled: boolean): boolean {
  return disabled;
}

export function validatePortalUnlockInput(email: string, password: string): string | null {
  if (!email.trim()) {
    return 'Enter your email';
  }
  if (!password.trim()) {
    return 'Enter your account password';
  }
  return null;
}

export function portalUnlockErrorMessage(
  kind: 'credentials' | 'forbidden' | 'unknown' = 'unknown',
): string {
  if (kind === 'credentials') {
    return 'Could not unlock — check email and password';
  }
  if (kind === 'forbidden') {
    return 'Not authorized — only an admin can unlock the website';
  }
  return 'Could not unlock the website';
}

/**
 * Sign in with account credentials, then re-enable the parent portal.
 * Controllers (bootstrap admin / operators) succeed; everyone else is rejected.
 */
export async function unlockPortalWebsite(
  deps: PortalUnlockDeps,
): Promise<{ok: true} | {ok: false; error: string}> {
  const validation = validatePortalUnlockInput(deps.email, deps.password);
  if (validation) {
    return {ok: false, error: validation};
  }

  let session: PortalUnlockLoginResult;
  try {
    session = await deps.login(deps.email.trim(), deps.password);
  } catch {
    return {ok: false, error: portalUnlockErrorMessage('credentials')};
  }

  deps.setTokens(session.accessToken, session.refreshToken, session.email, session.profile);

  try {
    const control = await deps.setDisabled(false, deps.password);
    if (control.disabled) {
      return {ok: false, error: portalUnlockErrorMessage('forbidden')};
    }
    return {ok: true};
  } catch (err: unknown) {
    const status = (err as {response?: {status?: number}})?.response?.status;
    if (status === 403) {
      return {ok: false, error: portalUnlockErrorMessage('forbidden')};
    }
    if (status === 401) {
      return {ok: false, error: portalUnlockErrorMessage('credentials')};
    }
    return {ok: false, error: portalUnlockErrorMessage('unknown')};
  }
}
