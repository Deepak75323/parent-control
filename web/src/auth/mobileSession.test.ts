import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {
  MOBILE_SESSION_END_KEY,
  clearMobileSessionEndedMark,
  isMobileWebClient,
  markMobileSessionEnded,
  wasMobileSessionEnded,
} from './mobileSession';

function installStorage() {
  const storage = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => {
      storage.set(key, value);
    },
    removeItem: (key: string) => {
      storage.delete(key);
    },
    clear: () => {
      storage.clear();
    },
  });
}

function installMatchMedia(matches: boolean) {
  vi.stubGlobal('window', {
    matchMedia: (query: string) => ({
      matches: matches && query.includes('900px'),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  });
}

describe('mobileSession', () => {
  beforeEach(() => {
    installStorage();
    installMatchMedia(true);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('detects mobile viewport', () => {
    expect(isMobileWebClient()).toBe(true);
  });

  it('marks and detects ended mobile sessions', () => {
    expect(wasMobileSessionEnded()).toBe(false);
    markMobileSessionEnded();
    expect(localStorage.getItem(MOBILE_SESSION_END_KEY)).not.toBeNull();
    expect(wasMobileSessionEnded()).toBe(true);
    clearMobileSessionEndedMark();
    expect(wasMobileSessionEnded()).toBe(false);
  });

  it('does not mark ended sessions on desktop viewport', () => {
    installMatchMedia(false);
    markMobileSessionEnded();
    expect(localStorage.getItem(MOBILE_SESSION_END_KEY)).toBeNull();
    expect(wasMobileSessionEnded()).toBe(false);
  });
});
