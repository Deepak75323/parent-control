import {Platform} from 'react-native';
import {
  ALL_PERMISSION_ORDER,
  OPTIONAL_PERMISSION_IDS,
  RUNTIME_PERMISSION_ORDER,
  SCREEN_CAPTURE_ORDER,
  SPECIAL_PERMISSION_ORDER,
  buildPermissionGroups,
} from '../catalog';

describe('camera-with-sound permission catalog', () => {
  it('requires camera and microphone (and notifications on Android 13+)', () => {
    expect(RUNTIME_PERMISSION_ORDER).toContain('camera');
    expect(RUNTIME_PERMISSION_ORDER).toContain('microphone');
    expect(RUNTIME_PERMISSION_ORDER).not.toContain('location');
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      expect(RUNTIME_PERMISSION_ORDER).toContain('postNotifications');
    }
  });

  it('requires accessibility, battery, and device admin (optional auto-start)', () => {
    expect(SPECIAL_PERMISSION_ORDER).toEqual(
      expect.arrayContaining(['accessibility', 'battery', 'deviceAdmin']),
    );
    expect(SPECIAL_PERMISSION_ORDER).not.toContain('overlay');
    expect(SPECIAL_PERMISSION_ORDER).not.toContain('usageStats');
    expect(SPECIAL_PERMISSION_ORDER).not.toContain('notificationAccess');
    expect(SPECIAL_PERMISSION_ORDER).not.toContain('fullScreenIntent');
  });

  it('does not require screen capture in setup', () => {
    expect(SCREEN_CAPTURE_ORDER).toEqual([]);
    expect(ALL_PERMISSION_ORDER).not.toContain('screenCapture');
    expect(OPTIONAL_PERMISSION_IDS).toEqual(
      expect.arrayContaining([
        'location',
        'usageStats',
        'notificationAccess',
        'overlay',
        'fullScreenIntent',
        'screenCapture',
      ]),
    );
    expect(OPTIONAL_PERMISSION_IDS).not.toContain('microphone');
  });

  it('builds dashboard groups with camera and microphone', () => {
    const groups = buildPermissionGroups(true);
    const ids = groups.flatMap(g => g.permissionIds);
    expect(ids).toEqual(
      expect.arrayContaining([
        'camera',
        'microphone',
        'accessibility',
        'battery',
        'autoStart',
        'deviceAdmin',
      ]),
    );
    expect(ids).not.toContain('location');
    expect(ids).not.toContain('screenCapture');
    expect(ids).not.toContain('overlay');
    expect(ids).not.toContain('usageStats');
    expect(ids).not.toContain('notificationAccess');
  });
});
