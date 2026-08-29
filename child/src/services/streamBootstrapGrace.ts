/** How long JS poll waits for backend ACTIVE before tearing down a starting stream. */
export const BACKEND_SESSION_BOOTSTRAP_GRACE_MS = 30_000;

type BackendActive = {
  sessionId: string;
  status?: string;
} | null | undefined;

export type BootstrapTeardownInput = {
  activeSessionId: string | null;
  activeRoom: string | null;
  starting: boolean;
  connecting: boolean;
  streamActive: boolean;
  hasNativePublisherOwner: boolean;
  lastStartAttemptAt: number;
  backendActive: BackendActive;
  now?: number;
};

/** True when poll must not tear down — backend may still be catching up with local bootstrap. */
export function shouldSuppressBackendSessionTeardown(input: BootstrapTeardownInput): boolean {
  const now = input.now ?? Date.now();
  const inGrace =
    input.lastStartAttemptAt > 0 &&
    now - input.lastStartAttemptAt < BACKEND_SESSION_BOOTSTRAP_GRACE_MS;

  const bootstrapping = input.starting || input.connecting || inGrace;

  const hasLocal =
    !!input.activeRoom ||
    !!input.activeSessionId ||
    input.streamActive ||
    input.hasNativePublisherOwner;

  if (!hasLocal || !bootstrapping) {
    return false;
  }

  const backend = input.backendActive;
  if (!backend?.sessionId) {
    return true;
  }

  if (input.activeSessionId && backend.sessionId === input.activeSessionId) {
    return true;
  }

  const backendIsLiveOtherSession =
    (backend.status === 'ACTIVE' || backend.status === 'PENDING') &&
    backend.sessionId !== input.activeSessionId;

  return !backendIsLiveOtherSession;
}
