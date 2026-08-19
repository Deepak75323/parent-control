export type ClockLockPhase = 'open' | 'clock' | 'password';

export type ClockLockEvent =
  | {type: 'APP_BACKGROUNDED'}
  | {type: 'CORNER_TAPPED'}
  | {type: 'LOGIN_REQUESTED'}
  | {type: 'FINGERPRINT_SUCCEEDED'}
  | {type: 'FINGERPRINT_FAILED'}
  | {type: 'PASSWORD_SUCCEEDED'}
  | {type: 'PASSWORD_FAILED'}
  | {type: 'PASSWORD_CANCELLED'}
  | {type: 'SESSION_CHANGED'}
  | {type: 'SESSION_RESTORED'};

export type ClockLockContext = {
  authenticated: boolean;
};

/**
 * Parent clock lock: camouflage first, then fingerprint, then parent password.
 * The dashboard is revealed only when the parent session is already logged in.
 */
export function nextClockLockPhase(
  phase: ClockLockPhase,
  event: ClockLockEvent,
  ctx: ClockLockContext,
): ClockLockPhase {
  if (event.type === 'SESSION_CHANGED') {
    return ctx.authenticated ? phase : 'clock';
  }

  if (event.type === 'APP_BACKGROUNDED' || event.type === 'SESSION_RESTORED') {
    return 'clock';
  }

  if (!ctx.authenticated) {
    if (event.type === 'LOGIN_REQUESTED') {
      return 'open';
    }
    return 'clock';
  }

  switch (event.type) {
    case 'CORNER_TAPPED':
    case 'FINGERPRINT_FAILED':
    case 'PASSWORD_CANCELLED':
      return 'clock';
    case 'FINGERPRINT_SUCCEEDED':
      return phase === 'clock' ? 'password' : phase;
    case 'PASSWORD_SUCCEEDED':
      return 'open';
    case 'PASSWORD_FAILED':
      return 'password';
    default:
      return phase;
  }
}
