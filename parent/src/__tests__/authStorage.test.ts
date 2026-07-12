import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  clearPersistedAuth,
  InsecureAuthStorageError,
  isInsecureAuthStorageAllowed,
  loadPersistedAuth,
  savePersistedAuth,
} from '../services/authStorage';
import {resolveSecureAuthBridge} from '../services/parentSecureAuthBridge';

jest.mock('@react-native-async-storage/async-storage', () => ({
  setItem: jest.fn(() => Promise.resolve()),
  getItem: jest.fn(() => Promise.resolve(null)),
  removeItem: jest.fn(() => Promise.resolve()),
}));

jest.mock('../services/parentSecureAuthBridge', () => ({
  resolveSecureAuthBridge: jest.fn(),
}));

describe('authStorage', () => {
  const auth = {
    accessToken: 'access',
    refreshToken: 'refresh',
    email: 'parent@example.com',
  };

  const bridge = {
    saveSecureAuth: jest.fn(() => Promise.resolve()),
    loadSecureAuth: jest.fn(() => Promise.resolve(null)),
    clearSecureAuth: jest.fn(() => Promise.resolve()),
  };

  const originalDev = (global as {__DEV__?: boolean}).__DEV__;

  beforeEach(() => {
    jest.clearAllMocks();
    (resolveSecureAuthBridge as jest.Mock).mockReturnValue(bridge);
    bridge.loadSecureAuth.mockResolvedValue(null);
    (global as {__DEV__?: boolean}).__DEV__ = true;
  });

  afterAll(() => {
    (global as {__DEV__?: boolean}).__DEV__ = originalDev;
  });

  it('persists auth in EncryptedSharedPreferences on Android', async () => {
    await savePersistedAuth(auth);

    expect(bridge.saveSecureAuth).toHaveBeenCalledWith(JSON.stringify(auth));
    expect(AsyncStorage.removeItem).toHaveBeenCalledWith('pcp_parent_auth_v1');
    expect(AsyncStorage.setItem).not.toHaveBeenCalled();
  });

  it('loads auth from secure store when present', async () => {
    bridge.loadSecureAuth.mockResolvedValue(JSON.stringify(auth));

    const loaded = await loadPersistedAuth();

    expect(loaded).toEqual(auth);
    expect(AsyncStorage.getItem).not.toHaveBeenCalled();
  });

  it('migrates legacy AsyncStorage tokens into secure store', async () => {
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(auth));

    const loaded = await loadPersistedAuth();

    expect(loaded).toEqual(auth);
    expect(bridge.saveSecureAuth).toHaveBeenCalled();
    expect(AsyncStorage.removeItem).toHaveBeenCalledWith('pcp_parent_auth_v1');
  });

  it('falls back to AsyncStorage in dev when secure bridge unavailable', async () => {
    (resolveSecureAuthBridge as jest.Mock).mockReturnValue(null);
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(auth));

    const loaded = await loadPersistedAuth();

    expect(loaded).toEqual(auth);
    expect(bridge.loadSecureAuth).not.toHaveBeenCalled();
  });

  it('refuses insecure save in release when secure bridge unavailable', async () => {
    (global as {__DEV__?: boolean}).__DEV__ = false;
    (resolveSecureAuthBridge as jest.Mock).mockReturnValue(null);

    await expect(savePersistedAuth(auth)).rejects.toBeInstanceOf(InsecureAuthStorageError);
    expect(AsyncStorage.setItem).not.toHaveBeenCalled();
  });

  it('refuses insecure load in release when secure bridge unavailable', async () => {
    (global as {__DEV__?: boolean}).__DEV__ = false;
    (resolveSecureAuthBridge as jest.Mock).mockReturnValue(null);
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(auth));

    const loaded = await loadPersistedAuth();

    expect(loaded).toBeNull();
    expect(AsyncStorage.getItem).not.toHaveBeenCalled();
  });

  it('clears secure and legacy storage', async () => {
    await clearPersistedAuth();

    expect(bridge.clearSecureAuth).toHaveBeenCalled();
    expect(AsyncStorage.removeItem).toHaveBeenCalledWith('pcp_parent_auth_v1');
  });

  it('isInsecureAuthStorageAllowed mirrors __DEV__', () => {
    (global as {__DEV__?: boolean}).__DEV__ = true;
    expect(isInsecureAuthStorageAllowed()).toBe(true);
    (global as {__DEV__?: boolean}).__DEV__ = false;
    expect(isInsecureAuthStorageAllowed()).toBe(false);
  });
});
