import {verifyParentUnlockPassword} from '../utils/verifyParentUnlockPassword';

describe('verifyParentUnlockPassword', () => {
  it('rejects a blank password without calling APIs', async () => {
    const pinStatus = jest.fn();
    const ok = await verifyParentUnlockPassword('   ', 'a@b.com', {
      pinStatus,
      verifyPin: jest.fn(),
      login: jest.fn(),
    });
    expect(ok).toBe(false);
    expect(pinStatus).not.toHaveBeenCalled();
  });

  it('uses the parent PIN when one is configured', async () => {
    const verifyPin = jest.fn().mockResolvedValue({verified: true});
    const login = jest.fn();
    const ok = await verifyParentUnlockPassword('Secret1x', 'a@b.com', {
      pinStatus: async () => ({pinConfigured: true}),
      verifyPin,
      login,
    });
    expect(ok).toBe(true);
    expect(verifyPin).toHaveBeenCalledWith('Secret1x');
    expect(login).not.toHaveBeenCalled();
  });

  it('falls back to the account password when no PIN is set', async () => {
    const login = jest.fn().mockResolvedValue({});
    const ok = await verifyParentUnlockPassword('account-pass', 'a@b.com', {
      pinStatus: async () => ({pinConfigured: false}),
      verifyPin: jest.fn(),
      login,
    });
    expect(ok).toBe(true);
    expect(login).toHaveBeenCalledWith('a@b.com', 'account-pass');
  });

  it('does not unlock when fingerprint passed but there is no email or PIN', async () => {
    const ok = await verifyParentUnlockPassword('x', null, {
      pinStatus: async () => ({pinConfigured: false}),
      verifyPin: jest.fn(),
      login: jest.fn(),
    });
    expect(ok).toBe(false);
  });
});
