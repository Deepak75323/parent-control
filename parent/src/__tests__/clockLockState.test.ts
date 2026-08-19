import {nextClockLockPhase, type ClockLockPhase} from '../utils/clockLockState';

function step(
  phase: ClockLockPhase,
  event: Parameters<typeof nextClockLockPhase>[1],
  authenticated: boolean,
): ClockLockPhase {
  return nextClockLockPhase(phase, event, {authenticated});
}

describe('nextClockLockPhase', () => {
  it('covers the dashboard with the clock when an authenticated session backgrounds', () => {
    expect(step('open', {type: 'APP_BACKGROUNDED'}, true)).toBe('clock');
  });

  it('keeps the app hidden behind the clock when there is no session', () => {
    expect(step('open', {type: 'APP_BACKGROUNDED'}, false)).toBe('clock');
    expect(step('clock', {type: 'APP_BACKGROUNDED'}, false)).toBe('clock');
  });

  it('stays on the clock until fingerprint succeeds', () => {
    expect(step('clock', {type: 'CORNER_TAPPED'}, true)).toBe('clock');
    expect(step('clock', {type: 'FINGERPRINT_FAILED'}, true)).toBe('clock');
  });

  it('asks for parent password only after fingerprint succeeds on a live session', () => {
    expect(step('clock', {type: 'FINGERPRINT_SUCCEEDED'}, true)).toBe('password');
  });

  it('stays on the clock until login is explicitly requested when the session is gone', () => {
    expect(step('clock', {type: 'FINGERPRINT_SUCCEEDED'}, false)).toBe('clock');
    expect(step('clock', {type: 'LOGIN_REQUESTED'}, false)).toBe('open');
  });

  it('opens the app after parent password only when already authenticated', () => {
    expect(step('password', {type: 'PASSWORD_SUCCEEDED'}, true)).toBe('open');
    expect(step('password', {type: 'PASSWORD_SUCCEEDED'}, false)).toBe('clock');
  });

  it('keeps the password sheet after a wrong password', () => {
    expect(step('password', {type: 'PASSWORD_FAILED'}, true)).toBe('password');
  });

  it('returns to the clock if password is cancelled', () => {
    expect(step('password', {type: 'PASSWORD_CANCELLED'}, true)).toBe('clock');
  });

  it('returns to the clock when the parent logs out', () => {
    expect(step('clock', {type: 'SESSION_CHANGED'}, false)).toBe('clock');
    expect(step('password', {type: 'SESSION_CHANGED'}, false)).toBe('clock');
  });

  it('does not lock immediately after an interactive login', () => {
    expect(step('open', {type: 'SESSION_CHANGED'}, true)).toBe('open');
  });

  it('covers a restored session with the clock on cold start', () => {
    expect(step('open', {type: 'SESSION_RESTORED'}, true)).toBe('clock');
    expect(step('open', {type: 'SESSION_RESTORED'}, false)).toBe('clock');
  });
});
