jest.mock('../../nativeModules', () => ({
  nativeModules: {
    isCameraGranted: jest.fn(),
    isMicGranted: jest.fn(),
    requestCameraPermission: jest.fn(),
    requestCameraAndMicPermissions: jest.fn(),
    isMediaProjectionGranted: jest.fn(),
    getMediaProjectionDebugState: jest.fn(),
    isProjectionLostAfterProcessRestart: jest.fn(),
    ensureMediaProjectionSessionReady: jest.fn(),
  },
}));

jest.mock('../grantScreenCapture', () => ({
  grantScreenCapture: jest.fn(),
}));

jest.mock('../flowLogger', () => ({
  flowLog: jest.fn(),
}));

import {nativeModules} from '../../nativeModules';
import {ensurePermissionsForStream} from '../streamingPermissions';

const mockNative = nativeModules as unknown as {
  isCameraGranted: jest.Mock;
  isMicGranted: jest.Mock;
  requestCameraPermission: jest.Mock;
  requestCameraAndMicPermissions: jest.Mock;
};

describe('ensurePermissionsForStream CAMERA', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('requires camera only — not microphone', async () => {
    mockNative.isCameraGranted.mockResolvedValue(true);
    mockNative.isMicGranted.mockResolvedValue(false);
    await expect(ensurePermissionsForStream('CAMERA')).resolves.toBeUndefined();
    expect(mockNative.isMicGranted).not.toHaveBeenCalled();
    expect(mockNative.requestCameraAndMicPermissions).not.toHaveBeenCalled();
  });

  it('requests camera permission when missing', async () => {
    mockNative.isCameraGranted
      .mockResolvedValueOnce(false)
      .mockResolvedValue(true);
    mockNative.requestCameraPermission.mockResolvedValue(true);
    await expect(ensurePermissionsForStream('CAMERA')).resolves.toBeUndefined();
    expect(mockNative.requestCameraPermission).toHaveBeenCalled();
  });
});
