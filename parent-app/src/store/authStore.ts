import {create} from 'zustand';
import {clearPersistedAuth, savePersistedAuth} from '../services/authStorage';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  email: string | null;
  setTokens: (access: string, refresh: string, email?: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  email: null,
  setTokens: (accessToken, refreshToken, email) => {
    const resolvedEmail = email ?? get().email ?? null;
    set({accessToken, refreshToken, email: resolvedEmail});
    savePersistedAuth({
      accessToken,
      refreshToken,
      email: resolvedEmail,
    }).catch(() => undefined);
  },
  logout: () => {
    set({accessToken: null, refreshToken: null, email: null});
    clearPersistedAuth().catch(() => undefined);
  },
}));
