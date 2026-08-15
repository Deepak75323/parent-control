import {
  nextConnectToken,
  isCurrentToken,
  invalidateAllConnectTokens,
  __resetForTests,
} from '../connectInvocationToken';

describe('connectInvocationToken', () => {
  beforeEach(() => {
    __resetForTests();
  });

  it('nextConnectToken issues strictly increasing tokens', () => {
    const a = nextConnectToken();
    const b = nextConnectToken();
    const c = nextConnectToken();
    expect(b).toBeGreaterThan(a);
    expect(c).toBeGreaterThan(b);
  });

  it('only the most recently issued token is current', () => {
    const a = nextConnectToken();
    const b = nextConnectToken();
    expect(isCurrentToken(a)).toBe(false);
    expect(isCurrentToken(b)).toBe(true);
  });

  it('invalidateAllConnectTokens invalidates the last-issued token too', () => {
    const t = nextConnectToken();
    expect(isCurrentToken(t)).toBe(true);
    invalidateAllConnectTokens();
    expect(isCurrentToken(t)).toBe(false);
  });

  it('a token issued after invalidateAllConnectTokens is current again', () => {
    const stale = nextConnectToken();
    invalidateAllConnectTokens();
    const fresh = nextConnectToken();
    expect(isCurrentToken(stale)).toBe(false);
    expect(isCurrentToken(fresh)).toBe(true);
  });

  it('models rapid start/stop/start: old session bails, new session runs', () => {
    // Session A begins its connect attempt.
    const sessionA = nextConnectToken();
    expect(isCurrentToken(sessionA)).toBe(true);

    // Parent taps Stop → stopActiveStream → disconnectStream(true) invalidates A.
    invalidateAllConnectTokens();
    expect(isCurrentToken(sessionA)).toBe(false);

    // Session A's retry loop wakes up after the backoff and checks its token — bails.
    // Meanwhile, parent taps Start again → session B begins.
    const sessionB = nextConnectToken();
    expect(isCurrentToken(sessionB)).toBe(true);
    expect(isCurrentToken(sessionA)).toBe(false);
  });
});
