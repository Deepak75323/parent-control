import {create} from 'zustand';
import {clearPersistedAuth, savePersistedAuth} from '../services/authStorage';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  email: string | null;
  emailVerified: boolean;
  setTokens: (
    access: string,
    refresh: string,
    email?: string,
    emailVerified?: boolean,
  ) => void;
  setEmailVerified: (verified: boolean) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  email: null,
  emailVerified: true,
  setTokens: (accessToken, refreshToken, email, emailVerified) => {
    const resolvedEmail = email ?? get().email ?? null;
    const resolvedVerified = emailVerified ?? get().emailVerified ?? true;
    set({
      accessToken,
      refreshToken,
      email: resolvedEmail,
      emailVerified: resolvedVerified,
    });
    savePersistedAuth({
      accessToken,
      refreshToken,
      email: resolvedEmail,
      emailVerified: resolvedVerified,
    }).catch(() => undefined);
  },
  setEmailVerified: verified => {
    set({emailVerified: verified});
    const {accessToken, refreshToken, email} = get();
    if (accessToken && refreshToken) {
      savePersistedAuth({
        accessToken,
        refreshToken,
        email,
        emailVerified: verified,
      }).catch(() => undefined);
    }
  },
  logout: () => {
    set({accessToken: null, refreshToken: null, email: null, emailVerified: true});
    clearPersistedAuth().catch(() => undefined);
  },
}));
