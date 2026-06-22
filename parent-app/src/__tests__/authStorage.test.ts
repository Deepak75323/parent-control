import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  clearPersistedAuth,
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

  beforeEach(() => {
    jest.clearAllMocks();
    (resolveSecureAuthBridge as jest.Mock).mockReturnValue(bridge);
    bridge.loadSecureAuth.mockResolvedValue(null);
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

  it('falls back to AsyncStorage when secure bridge unavailable', async () => {
    (resolveSecureAuthBridge as jest.Mock).mockReturnValue(null);
    (AsyncStorage.getItem as jest.Mock).mockResolvedValue(JSON.stringify(auth));

    const loaded = await loadPersistedAuth();

    expect(loaded).toEqual(auth);
    expect(bridge.loadSecureAuth).not.toHaveBeenCalled();
  });

  it('clears secure and legacy storage', async () => {
    await clearPersistedAuth();

    expect(bridge.clearSecureAuth).toHaveBeenCalled();
    expect(AsyncStorage.removeItem).toHaveBeenCalledWith('pcp_parent_auth_v1');
  });
});
