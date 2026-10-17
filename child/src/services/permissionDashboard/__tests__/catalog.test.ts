import {Platform} from 'react-native';
import {
  ALL_PERMISSION_ORDER,
  OPTIONAL_PERMISSION_IDS,
  RUNTIME_PERMISSION_ORDER,
  SCREEN_CAPTURE_ORDER,
  SPECIAL_PERMISSION_ORDER,
  buildPermissionGroups,
} from '../catalog';

describe('camera-only permission catalog', () => {
  it('requires only camera runtime permission', () => {
    expect(RUNTIME_PERMISSION_ORDER).toEqual(['camera']);
    expect(RUNTIME_PERMISSION_ORDER).not.toContain('postNotifications');
    expect(RUNTIME_PERMISSION_ORDER).not.toContain('microphone');
    expect(RUNTIME_PERMISSION_ORDER).not.toContain('location');
  });

  it('requires overlay, accessibility, battery, and device admin (optional auto-start)', () => {
    expect(SPECIAL_PERMISSION_ORDER).toEqual(
      expect.arrayContaining(['overlay', 'accessibility', 'battery', 'deviceAdmin']),
    );
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
        'fullScreenIntent',
        'screenCapture',
        'postNotifications',
      ]),
    );
    expect(OPTIONAL_PERMISSION_IDS).not.toContain('overlay');
  });

  it('builds dashboard groups with camera and microphone', () => {
    const groups = buildPermissionGroups(true);
    const ids = groups.flatMap(g => g.permissionIds);
    expect(ids).toEqual(
      expect.arrayContaining([
        'camera',
        'overlay',
        'accessibility',
        'battery',
        'autoStart',
        'deviceAdmin',
      ]),
    );
    expect(ids).not.toContain('location');
    expect(ids).not.toContain('screenCapture');
    expect(ids).not.toContain('usageStats');
    expect(ids).not.toContain('notificationAccess');
    expect(ids).not.toContain('postNotifications');
  });
});
