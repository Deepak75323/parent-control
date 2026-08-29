import {
  BACKEND_SESSION_BOOTSTRAP_GRACE_MS,
  shouldSuppressBackendSessionTeardown,
} from '../streamBootstrapGrace';

const base = {
  activeSessionId: 'sess-a',
  activeRoom: 'room-a',
  starting: false,
  connecting: false,
  streamActive: false,
  hasNativePublisherOwner: false,
  lastStartAttemptAt: 1_000,
  backendActive: null as {sessionId: string; status?: string} | null,
  now: 5_000,
};

describe('shouldSuppressBackendSessionTeardown', () => {
  it('suppresses when backend active is empty during bootstrap grace', () => {
    expect(
      shouldSuppressBackendSessionTeardown({
        ...base,
        starting: true,
        backendActive: null,
      }),
    ).toBe(true);
  });

  it('suppresses when backend matches session but status not yet ACTIVE', () => {
    expect(
      shouldSuppressBackendSessionTeardown({
        ...base,
        connecting: true,
        backendActive: {sessionId: 'sess-a', status: 'STARTING'},
      }),
    ).toBe(true);
  });

  it('does not suppress after bootstrap grace expires', () => {
    expect(
      shouldSuppressBackendSessionTeardown({
        ...base,
        lastStartAttemptAt: 0,
        backendActive: null,
      }),
    ).toBe(false);
  });

  it('does not suppress when backend has a different live session', () => {
    expect(
      shouldSuppressBackendSessionTeardown({
        ...base,
        starting: true,
        backendActive: {sessionId: 'sess-b', status: 'ACTIVE'},
      }),
    ).toBe(false);
  });

  it('respects grace window from lastStartAttemptAt', () => {
    const startedAt = 10_000;
    expect(
      shouldSuppressBackendSessionTeardown({
        ...base,
        lastStartAttemptAt: startedAt,
        backendActive: null,
        now: startedAt + BACKEND_SESSION_BOOTSTRAP_GRACE_MS - 1,
      }),
    ).toBe(true);
    expect(
      shouldSuppressBackendSessionTeardown({
        ...base,
        lastStartAttemptAt: startedAt,
        backendActive: null,
        now: startedAt + BACKEND_SESSION_BOOTSTRAP_GRACE_MS + 1,
      }),
    ).toBe(false);
  });
});
