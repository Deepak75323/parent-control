import {api} from '../api';
import {nativeModules} from '../../nativeModules';
import {flowLog} from '../flowLogger';
import {navTraceAuth} from '../../navigation/navTrace';
import {resolveApiBaseUrl} from '../../utils/config';
import {markOnboardingAuthenticated} from './onboardingSession';
import {ApiPaths} from '../../constants/apiPaths';

export type ChildLoginResult =
  | {ok: true}
  | {ok: false; message: string};

type AuthResponse = {
  success: boolean;
  data: {authenticated: boolean; pairingCode?: string | null};
};

async function getAndroidId(): Promise<string> {
  for (let attempt = 0; attempt < 3; attempt++) {
    const id = (await nativeModules.getAndroidId())?.trim().toLowerCase() ?? '';
    if (id) {
      return id;
    }
    await new Promise(resolve => setTimeout(resolve, 50 * (attempt + 1)));
  }
  return '';
}

async function postOnboardingAuth(body: {
  childLoginPassword?: string;
  familyHandle?: string;
  recoveryCode?: string;
  pairingCode?: string;
  setupCodeOnly?: boolean;
  androidId?: string;
}): Promise<AuthResponse> {
  const {data} = await api.post<AuthResponse>(ApiPaths.ONBOARDING_AUTH, body);
  return data;
}

function loginErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const axiosErr = err as {
      response?: {status?: number; data?: {message?: string}};
      message?: string;
    };
    const status = axiosErr.response?.status;
    if (status === 429) {
      return 'Too many tries. Wait a minute, then try again.';
    }
    if (status != null && status >= 500) {
      return 'Something went wrong. Please try again in a moment.';
    }
    const data = axiosErr.response?.data;
    if (data?.message?.trim()) {
      return data.message.trim();
    }
    if (!axiosErr.response) {
      return 'No internet connection. Check Wi‑Fi or mobile data and try again.';
    }
  }
  return fallback;
}

/**
 * Normal login — family password, plus family handle when the device is not yet paired.
 */
export async function authenticateWithPassword(
  password: string,
  familyHandle?: string,
): Promise<ChildLoginResult> {
  const secret = password.trim();
  if (secret.length < 4) {
    return {ok: false, message: 'Enter at least 4 characters.'};
  }

  const handle = familyHandle?.trim().replace(/^@/, '') ?? '';

  try {
    const androidId = await getAndroidId();
    navTraceAuth('authenticateWithPassword request', {
      androidIdPresent: !!androidId,
      androidIdLen: androidId.length,
      familyHandlePresent: handle.length > 0,
    });

    try {
      await resolveApiBaseUrl(true);
    } catch {
      return {
        ok: false,
        message: 'No internet connection. Check Wi‑Fi or mobile data and try again.',
      };
    }

    const auth = await postOnboardingAuth({
      childLoginPassword: secret,
      androidId: androidId || undefined,
      ...(handle ? {familyHandle: handle} : {}),
    });

    if (!auth.data.authenticated) {
      flowLog('ONBOARDING', 'login rejected', {
        androidIdSuffix: androidId.slice(-4) || null,
      });
      return {
        ok: false,
        message: handle
          ? 'Incorrect handle or password. Check the family handle in the parent app Settings.'
          : 'Incorrect password. Tap Forgot password to get a verification code in the parent app Alerts.',
      };
    }

    await markOnboardingAuthenticated(auth.data.pairingCode ?? null);
    navTraceAuth('authenticateWithPassword child password', {authenticated: true, tokenValid: true});
    flowLog('ONBOARDING', 'login ok (child password)');
    return {ok: true};
  } catch (e) {
    flowLog('ONBOARDING', 'login failed', {e});
    return {
      ok: false,
      message: loginErrorMessage(
        e,
        'No internet connection. Check Wi‑Fi or mobile data and try again.',
      ),
    };
  }
}

/** Ask backend to push a 6-digit recovery code to the parent app Alerts. */
export async function requestRecoveryCode(): Promise<ChildLoginResult> {
  try {
    const androidId = await getAndroidId();
    if (!androidId) {
      return {
        ok: false,
        message: 'Couldn’t read this phone’s ID. Restart the app and try again.',
      };
    }

    const {data} = await api.post<{
      success: boolean;
      data: {codeSent: boolean; message: string};
    }>(ApiPaths.ONBOARDING_RECOVERY, {androidId});

    if (data.data.codeSent) {
      flowLog('ONBOARDING', 'recovery code requested');
      return {ok: true};
    }
    return {
      ok: false,
      message: data.data.message || 'Couldn’t send the verification code. Please try again.',
    };
  } catch (e) {
    flowLog('ONBOARDING', 'recovery request failed', {e});
    return {
      ok: false,
      message: loginErrorMessage(
        e,
        'Something went wrong. Please try again in a moment.',
      ),
    };
  }
}

/** Recovery login — 6-digit code from parent Alerts. */
export async function authenticateWithRecoveryCode(code: string): Promise<ChildLoginResult> {
  const trimmed = code.trim();
  if (trimmed.length < 6) {
    return {ok: false, message: 'Enter the 6-digit code from the parent app Alerts.'};
  }

  try {
    const androidId = await getAndroidId();
    const {data} = await api.post<AuthResponse>(ApiPaths.ONBOARDING_AUTH, {
      recoveryCode: trimmed,
      androidId: androidId || undefined,
    });

    if (!data.data.authenticated) {
      return {ok: false, message: 'Incorrect or expired verification code.'};
    }

    await markOnboardingAuthenticated(data.data.pairingCode ?? null);
    flowLog('ONBOARDING', 'login ok (recovery code)');
    return {ok: true};
  } catch (e) {
    flowLog('ONBOARDING', 'recovery verify failed', {e});
    return {
      ok: false,
      message: loginErrorMessage(e, 'Invalid or expired verification code.'),
    };
  }
}

/** Fallback — 8-character setup code from parent Pair & manage (first-time / unpaired). */
export async function authenticateWithSetupCode(setupCode: string): Promise<ChildLoginResult> {
  const code = setupCode.trim().toUpperCase();
  if (code.length < 8) {
    return {ok: false, message: 'Enter the 8-character setup code from the parent app.'};
  }

  try {
    const {data} = await api.post<AuthResponse>(ApiPaths.ONBOARDING_AUTH, {
      pairingCode: code,
      setupCodeOnly: true,
    });

    if (!data.data.authenticated) {
      return {ok: false, message: 'Invalid or expired setup code.'};
    }

    await markOnboardingAuthenticated(data.data.pairingCode ?? code);
    flowLog('ONBOARDING', 'login ok (setup code)');
    return {ok: true};
  } catch (e) {
    flowLog('ONBOARDING', 'setup code failed', {e});
    return {
      ok: false,
      message: loginErrorMessage(e, 'Invalid or expired setup code.'),
    };
  }
}
