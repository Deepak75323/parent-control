import {DEV_TAMPER_UNLOCK_PIN} from '../../constants/devTamper';
import {api} from '../api';
import {nativeModules} from '../../nativeModules';
import {flowLog} from '../flowLogger';
import {markOnboardingAuthenticated} from './onboardingSession';

export type ChildLoginResult =
  | {ok: true}
  | {ok: false; message: string};

type AuthResponse = {
  success: boolean;
  data: {authenticated: boolean; pairingCode?: string | null};
};

async function getAndroidId(): Promise<string> {
  return (await nativeModules.getAndroidId())?.trim() ?? '';
}

function loginErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const data = (err as {response?: {data?: {message?: string}}}).response?.data;
    if (data?.message?.trim()) {
      return data.message.trim();
    }
  }
  return fallback;
}

/** Normal login — child device password only. */
export async function authenticateWithPassword(password: string): Promise<ChildLoginResult> {
  const secret = password.trim();
  if (secret.length < 4) {
    return {ok: false, message: 'Enter at least 4 characters.'};
  }

  try {
    if (secret === DEV_TAMPER_UNLOCK_PIN) {
      await markOnboardingAuthenticated();
      flowLog('ONBOARDING', 'login ok (master pin offline)');
      return {ok: true};
    }

    const androidId = await getAndroidId();
    const {data} = await api.post<AuthResponse>('/api/v1/onboarding/authenticate', {
      childLoginPassword: secret,
      androidId: androidId || undefined,
    });

    if (!data.data.authenticated) {
      return {
        ok: false,
        message:
          'Incorrect password. Tap Forgot password to get a recovery code in the parent app Alerts.',
      };
    }

    await markOnboardingAuthenticated(data.data.pairingCode ?? null);
    flowLog('ONBOARDING', 'login ok (child password)');
    return {ok: true};
  } catch (e) {
    flowLog('ONBOARDING', 'login failed', {e});
    return {
      ok: false,
      message: loginErrorMessage(
        e,
        'Could not reach the server. Check USB/Wi‑Fi and try again.',
      ),
    };
  }
}

/** Ask backend to push a 6-digit recovery code to the parent app Alerts. */
export async function requestRecoveryCode(): Promise<ChildLoginResult> {
  try {
    const androidId = await getAndroidId();
    if (!androidId) {
      return {ok: false, message: 'Could not read device id. Restart the app and try again.'};
    }

    const {data} = await api.post<{
      success: boolean;
      data: {codeSent: boolean; message: string};
    }>('/api/v1/onboarding/recovery/request', {androidId});

    if (data.data.codeSent) {
      flowLog('ONBOARDING', 'recovery code requested');
      return {ok: true};
    }
    return {ok: false, message: data.data.message || 'Could not send recovery code.'};
  } catch (e) {
    flowLog('ONBOARDING', 'recovery request failed', {e});
    return {
      ok: false,
      message: loginErrorMessage(e, 'Could not request a recovery code. Check network.'),
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
    const {data} = await api.post<AuthResponse>('/api/v1/onboarding/authenticate', {
      recoveryCode: trimmed,
      androidId: androidId || undefined,
    });

    if (!data.data.authenticated) {
      return {ok: false, message: 'Incorrect or expired recovery code.'};
    }

    await markOnboardingAuthenticated(data.data.pairingCode ?? null);
    flowLog('ONBOARDING', 'login ok (recovery code)');
    return {ok: true};
  } catch (e) {
    flowLog('ONBOARDING', 'recovery verify failed', {e});
    return {
      ok: false,
      message: loginErrorMessage(e, 'Invalid or expired recovery code.'),
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
    const {data} = await api.post<AuthResponse>('/api/v1/onboarding/authenticate', {
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
