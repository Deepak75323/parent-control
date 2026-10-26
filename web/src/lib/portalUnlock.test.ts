import {describe, expect, it, vi} from 'vitest';
import {
  portalUnlockErrorMessage,
  unlockPortalWebsite,
  validatePortalUnlockInput,
} from './portalUnlock';

describe('validatePortalUnlockInput', () => {
  it('requires email and password', () => {
    expect(validatePortalUnlockInput('', 'secret')).toBe('Enter your email');
    expect(validatePortalUnlockInput('a@b.com', '')).toBe('Enter your account password');
    expect(validatePortalUnlockInput('  ', '  ')).toBe('Enter your email');
    expect(validatePortalUnlockInput('a@b.com', 'secret')).toBeNull();
  });
});

describe('unlockPortalWebsite', () => {
  it('logs in then enables the site when the account can control', async () => {
    const login = vi.fn().mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      email: 'anilkumar@gmail.com',
      profile: {email: 'anilkumar@gmail.com'},
    });
    const setDisabled = vi.fn().mockResolvedValue({disabled: false, canControl: true});
    const setTokens = vi.fn();

    const result = await unlockPortalWebsite({
      email: 'anilkumar@gmail.com',
      password: 'pw',
      login,
      setDisabled,
      setTokens,
    });

    expect(result).toEqual({ok: true});
    expect(login).toHaveBeenCalledWith('anilkumar@gmail.com', 'pw');
    expect(setTokens).toHaveBeenCalled();
    expect(setDisabled).toHaveBeenCalledWith(false, 'pw');
  });

  it('rejects when login succeeds but the account cannot enable the site', async () => {
    const login = vi.fn().mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      email: 'other@example.com',
      profile: null,
    });
    const setDisabled = vi.fn().mockRejectedValue({response: {status: 403}});
    const setTokens = vi.fn();

    const result = await unlockPortalWebsite({
      email: 'other@example.com',
      password: 'pw',
      login,
      setDisabled,
      setTokens,
    });

    expect(result).toEqual({ok: false, error: portalUnlockErrorMessage('forbidden')});
  });

  it('rejects invalid credentials without calling setDisabled', async () => {
    const login = vi.fn().mockRejectedValue(new Error('bad'));
    const setDisabled = vi.fn();
    const setTokens = vi.fn();

    const result = await unlockPortalWebsite({
      email: 'a@b.com',
      password: 'wrong',
      login,
      setDisabled,
      setTokens,
    });

    expect(result).toEqual({ok: false, error: portalUnlockErrorMessage('credentials')});
    expect(setDisabled).not.toHaveBeenCalled();
  });
});
