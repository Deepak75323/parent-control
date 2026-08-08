import {ALL_PERMISSION_ORDER, OPTIONAL_PERMISSION_IDS} from '../catalog';
import {isPermissionStatusComplete} from '../permissionStatus';
import type {PermissionId, PermissionStatusMap} from '../types';

function statusWith(
  overrides: Partial<Record<PermissionId, boolean>>,
): PermissionStatusMap {
  const allFalse = Object.fromEntries(
    [...ALL_PERMISSION_ORDER, ...OPTIONAL_PERMISSION_IDS].map(id => [id, false]),
  ) as PermissionStatusMap;
  return {...allFalse, ...overrides};
}

describe('isPermissionStatusComplete', () => {
  it('is complete when only the keep-list permissions are granted', () => {
    const keepGranted = Object.fromEntries(
      ALL_PERMISSION_ORDER.map(id => [id, true]),
    ) as Partial<Record<PermissionId, boolean>>;
    const status = statusWith({
      ...keepGranted,
      location: false,
      screenCapture: false,
      overlay: false,
      usageStats: false,
      notificationAccess: false,
    });
    expect(isPermissionStatusComplete(status)).toBe(true);
  });

  it('is incomplete when camera is missing', () => {
    const keepGranted = Object.fromEntries(
      ALL_PERMISSION_ORDER.map(id => [id, true]),
    ) as Partial<Record<PermissionId, boolean>>;
    const status = statusWith({...keepGranted, camera: false});
    expect(isPermissionStatusComplete(status)).toBe(false);
  });
});
