jest.mock('../../nativeModules', () => ({
  nativeModules: {
    isCameraGranted: jest.fn(),
    isMicGranted: jest.fn(),
    requestCameraPermission: jest.fn(),
    requestMicrophonePermission: jest.fn(),
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
  requestMicrophonePermission: jest.Mock;
  requestCameraAndMicPermissions: jest.Mock;
};

describe('ensurePermissionsForStream CAMERA', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('allows camera when microphone is denied', async () => {
    mockNative.isCameraGranted.mockResolvedValue(true);
    mockNative.isMicGranted.mockResolvedValue(false);
    await expect(ensurePermissionsForStream('CAMERA')).resolves.toBeUndefined();
    expect(mockNative.requestCameraAndMicPermissions).not.toHaveBeenCalled();
    expect(mockNative.requestCameraPermission).not.toHaveBeenCalled();
  });

  it('requests camera only when camera is missing', async () => {
    mockNative.isCameraGranted.mockResolvedValueOnce(false).mockResolvedValue(true);
    mockNative.isMicGranted.mockResolvedValue(false);
    mockNative.requestCameraPermission.mockResolvedValue(true);
    await expect(ensurePermissionsForStream('CAMERA')).resolves.toBeUndefined();
    expect(mockNative.requestCameraPermission).toHaveBeenCalled();
    expect(mockNative.requestCameraAndMicPermissions).not.toHaveBeenCalled();
  });

  it('fails when camera permission is denied', async () => {
    jest.useFakeTimers();
    mockNative.isCameraGranted.mockResolvedValue(false);
    mockNative.requestCameraPermission.mockResolvedValue(false);
    const pending = expect(ensurePermissionsForStream('CAMERA')).rejects.toThrow(
      /Camera permission required/,
    );
    await jest.runAllTimersAsync();
    await pending;
    jest.useRealTimers();
  });
});

describe('ensurePermissionsForStream AUDIO', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('requests microphone only — not camera', async () => {
    mockNative.isMicGranted.mockResolvedValueOnce(false).mockResolvedValue(true);
    mockNative.requestMicrophonePermission.mockResolvedValue(true);
    await expect(ensurePermissionsForStream('AUDIO')).resolves.toBeUndefined();
    expect(mockNative.requestMicrophonePermission).toHaveBeenCalled();
    expect(mockNative.requestCameraAndMicPermissions).not.toHaveBeenCalled();
    expect(mockNative.requestCameraPermission).not.toHaveBeenCalled();
  });

  it('skips prompts when microphone already granted', async () => {
    mockNative.isMicGranted.mockResolvedValue(true);
    await expect(ensurePermissionsForStream('AUDIO')).resolves.toBeUndefined();
    expect(mockNative.requestMicrophonePermission).not.toHaveBeenCalled();
    expect(mockNative.requestCameraAndMicPermissions).not.toHaveBeenCalled();
  });
});


