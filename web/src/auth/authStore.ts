import {create} from 'zustand';
import type {ParentProfile} from '../types/api';
import {clearMobileSessionEndedMark, wasMobileSessionEnded} from './mobileSession';

const ACCESS_KEY = 'pcp_web_access';
const REFRESH_KEY = 'pcp_web_refresh';
const EMAIL_KEY = 'pcp_web_email';
const PROFILE_KEY = 'pcp_web_profile';

function readSession(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeSession(key: string, value: string | null) {
  try {
    if (value == null) {
      sessionStorage.removeItem(key);
    } else {
      sessionStorage.setItem(key, value);
    }
  } catch {
    /* ignore quota / private mode */
  }
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  email: string | null;
  profile: ParentProfile | null;
  hydrated: boolean;
  setTokens: (
    access: string,
    refresh: string,
    email?: string,
    profile?: ParentProfile | null,
  ) => void;
  hydrate: () => void;
  clear: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  email: null,
  profile: null,
  hydrated: false,
  setTokens: (access, refresh, email, profile) => {
    clearMobileSessionEndedMark();
    writeSession(ACCESS_KEY, access);
    writeSession(REFRESH_KEY, refresh);
    if (email != null) {
      writeSession(EMAIL_KEY, email);
    }
    if (profile !== undefined) {
      writeSession(PROFILE_KEY, profile ? JSON.stringify(profile) : null);
    }
    set(state => ({
      accessToken: access,
      refreshToken: refresh,
      email: email ?? state.email,
      profile: profile === undefined ? state.profile : profile,
    }));
  },
  hydrate: () => {
    if (wasMobileSessionEnded()) {
      writeSession(ACCESS_KEY, null);
      writeSession(REFRESH_KEY, null);
      writeSession(EMAIL_KEY, null);
      writeSession(PROFILE_KEY, null);
      set({
        accessToken: null,
        refreshToken: null,
        email: null,
        profile: null,
        hydrated: true,
      });
      return;
    }
    const accessToken = readSession(ACCESS_KEY);
    const refreshToken = readSession(REFRESH_KEY);
    const email = readSession(EMAIL_KEY);
    let profile: ParentProfile | null = null;
    const rawProfile = readSession(PROFILE_KEY);
    if (rawProfile) {
      try {
        profile = JSON.parse(rawProfile) as ParentProfile;
      } catch {
        profile = null;
      }
    }
    set({accessToken, refreshToken, email, profile, hydrated: true});
  },
  clear: () => {
    writeSession(ACCESS_KEY, null);
    writeSession(REFRESH_KEY, null);
    writeSession(EMAIL_KEY, null);
    writeSession(PROFILE_KEY, null);
    set({
      accessToken: null,
      refreshToken: null,
      email: null,
      profile: null,
    });
  },
  isAuthenticated: () => Boolean(get().accessToken && get().refreshToken),
}));
