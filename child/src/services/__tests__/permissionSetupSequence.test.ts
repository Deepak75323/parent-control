jest.mock('../../nativeModules', () => ({
  nativeModules: {
    requiresOemBackgroundSetup: jest.fn(),
    isCameraGranted: jest.fn(),
    isMicGranted: jest.fn(),
    isPostNotificationsGranted: jest.fn(),
    isAccessibilityEnabled: jest.fn(),
    isAccessibilityServiceConnected: jest.fn(),
    isOverlayGranted: jest.fn(),
    isBatteryOptimizationExempt: jest.fn(),
    isOemAutostartPromptDone: jest.fn(),
    isDeviceAdminActive: jest.fn(),
  },
}));

import {nativeModules} from '../../nativeModules';
import {isSetupPermissionsComplete} from '../permissionSetupSequence';

const mockNative = nativeModules as unknown as {
  requiresOemBackgroundSetup: jest.Mock;
  isCameraGranted: jest.Mock;
  isMicGranted: jest.Mock;
  isPostNotificationsGranted: jest.Mock;
  isAccessibilityEnabled: jest.Mock;
  isAccessibilityServiceConnected: jest.Mock;
  isOverlayGranted: jest.Mock;
  isBatteryOptimizationExempt: jest.Mock;
  isOemAutostartPromptDone: jest.Mock;
  isDeviceAdminActive: jest.Mock;
};

describe('isSetupPermissionsComplete (camera-only, no mic)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockNative.requiresOemBackgroundSetup.mockResolvedValue(false);
    mockNative.isCameraGranted.mockResolvedValue(true);
    mockNative.isMicGranted.mockResolvedValue(false);
    mockNative.isPostNotificationsGranted.mockResolvedValue(true);
    mockNative.isAccessibilityEnabled.mockResolvedValue(true);
    mockNative.isAccessibilityServiceConnected.mockResolvedValue(true);
    mockNative.isOverlayGranted.mockResolvedValue(true);
    mockNative.isBatteryOptimizationExempt.mockResolvedValue(true);
    mockNative.isOemAutostartPromptDone.mockResolvedValue(true);
    mockNative.isDeviceAdminActive.mockResolvedValue(true);
  });

  it('is complete without microphone', async () => {
    await expect(isSetupPermissionsComplete()).resolves.toBe(true);
    expect(mockNative.isMicGranted).not.toHaveBeenCalled();
  });

  it('is complete without notification permission', async () => {
    mockNative.isPostNotificationsGranted.mockResolvedValue(false);
    await expect(isSetupPermissionsComplete()).resolves.toBe(true);
    expect(mockNative.isPostNotificationsGranted).not.toHaveBeenCalled();
  });

  it('is incomplete without device admin', async () => {
    mockNative.isDeviceAdminActive.mockResolvedValue(false);
    await expect(isSetupPermissionsComplete()).resolves.toBe(false);
  });

  it('is incomplete without display-over-other-apps', async () => {
    mockNative.isOverlayGranted.mockResolvedValue(false);
    await expect(isSetupPermissionsComplete()).resolves.toBe(false);
  });
});
