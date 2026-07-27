import {create} from 'zustand';
import {clearPersistedAuth, savePersistedAuth} from '../services/authStorage';

const DEFAULT_ACCESS_TTL_MS = 15 * 60 * 1000;

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  email: string | null;
  emailVerified: boolean;
  /** Epoch ms when the current access token should be treated as expired. */
  accessTokenExpiresAtMs: number | null;
  setTokens: (
    access: string,
    refresh: string,
    email?: string,
    emailVerified?: boolean,
    expiresInSeconds?: number,
  ) => void;
  setEmailVerified: (verified: boolean) => void;
  /** Ends live streams on the child, then clears local auth. */
  logout: () => Promise<void>;
}

let logoutInFlight: Promise<void> | null = null;

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  email: null,
  emailVerified: true,
  accessTokenExpiresAtMs: null,
  setTokens: (accessToken, refreshToken, email, emailVerified, expiresInSeconds) => {
    const resolvedEmail = email ?? get().email ?? null;
    const resolvedVerified = emailVerified ?? get().emailVerified ?? true;
    const ttlMs =
      expiresInSeconds && expiresInSeconds > 0
        ? expiresInSeconds * 1000
        : DEFAULT_ACCESS_TTL_MS;
    const accessTokenExpiresAtMs = Date.now() + ttlMs;
    set({
      accessToken,
      refreshToken,
      email: resolvedEmail,
      emailVerified: resolvedVerified,
      accessTokenExpiresAtMs,
    });
    void import('../services/telemetry').then(({identifyUser}) => {
      identifyUser(resolvedEmail);
    }).catch(() => undefined);
    savePersistedAuth({
      accessToken,
      refreshToken,
      email: resolvedEmail,
      emailVerified: resolvedVerified,
      accessTokenExpiresAtMs,
    }).catch(() => undefined);
  },
  setEmailVerified: verified => {
    set({emailVerified: verified});
    const {accessToken, refreshToken, email, accessTokenExpiresAtMs} = get();
    if (accessToken && refreshToken) {
      savePersistedAuth({
        accessToken,
        refreshToken,
        email,
        emailVerified: verified,
        accessTokenExpiresAtMs: accessTokenExpiresAtMs ?? undefined,
      }).catch(() => undefined);
    }
  },
  logout: () => {
    if (logoutInFlight) return logoutInFlight;
    logoutInFlight = (async () => {
      try {
        const {teardownActiveStreamOnAuthEnd} = await import(
          '../services/streamAuthTeardown'
        );
        await teardownActiveStreamOnAuthEnd();
      } catch {
        // ignore — always clear auth below
      }
      try {
        const {identifyUser, track} = await import('../services/telemetry');
        const {TelemetryEvents} = await import('../services/telemetryEvents');
        track(TelemetryEvents.LOGOUT);
        identifyUser(null);
      } catch {
        // ignore
      }
      set({
        accessToken: null,
        refreshToken: null,
        email: null,
        emailVerified: true,
        accessTokenExpiresAtMs: null,
      });
      await clearPersistedAuth().catch(() => undefined);
    })().finally(() => {
      logoutInFlight = null;
    });
    return logoutInFlight;
  },
}));
