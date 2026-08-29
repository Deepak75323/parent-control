import type {DeviceSummary} from '../services/api';

export type ReadinessRow = {
  key: string;
  label: string;
  ok: boolean | null;
  fixHint: string;
};

/** Child heartbeat failureClassifications → readiness checklist keys. */
const FAILURE_TAG_TO_READINESS_KEY: Record<string, string> = {
  ACCESSIBILITY_DISABLED: 'a11y',
  BATTERY_NOT_EXEMPT: 'battery',
  AUTOSTART_NOT_CONFIRMED: 'autostart',
  CAMERA_NOT_GRANTED: 'camera',
  OVERLAY_NOT_GRANTED: 'overlay',
  MONITORING_DEGRADED: 'monitoring',
};

/** Checklist keys we can mark granted when the child snapshot omits an explicit failure tag. */
const READINESS_KEY_TO_FAILURE_TAG: Record<string, string> = {
  battery: 'BATTERY_NOT_EXEMPT',
  autostart: 'AUTOSTART_NOT_CONFIRMED',
  a11y: 'ACCESSIBILITY_DISABLED',
  camera: 'CAMERA_NOT_GRANTED',
  overlay: 'OVERLAY_NOT_GRANTED',
  monitoring: 'MONITORING_DEGRADED',
};

export function resolveAccessibilityReadiness(device: DeviceSummary): boolean | null {
  if ((device.readinessFailureTags ?? []).includes('ACCESSIBILITY_DISABLED')) {
    return false;
  }
  if (device.accessibilityEnabled === false) {
    return false;
  }
  if (device.permAccessibility === false) return false;
  if (device.permAccessibility === true) return true;
  if (device.accessibilityEnabled === true) {
    return true;
  }
  if (device.accessibilityServiceConnected === false) {
    return false;
  }
  if (device.accessibilityServiceConnected === true) {
    return true;
  }
  return null;
}

/** Remote camera only needs healthy bursts; CONSERVATIVE/STANDBY are soft warnings. */
export function resolveMonitoringReadiness(device: DeviceSummary): boolean | null {
  if ((device.readinessFailureTags ?? []).includes('MONITORING_DEGRADED')) {
    return false;
  }
  if (device.monitoringMode === 'WORKMANAGER_ONLY') {
    return false;
  }
  if (
    device.monitoringMode === 'CONSERVATIVE' ||
    device.monitoringMode === 'STANDBY_DEGRADED'
  ) {
    return true;
  }
  if (device.monitoringDegraded === true) {
    return false;
  }
  if (device.monitoringDegraded === false) {
    return true;
  }
  return null;
}

/** Force checklist rows false when child-reported failure tags contradict stored grants. */
export function applyReadinessFailureTags(
  items: ReadinessRow[],
  tags?: string[] | null,
): ReadinessRow[] {
  if (!tags?.length) return items;
  const failedKeys = new Set(
    tags.map(tag => FAILURE_TAG_TO_READINESS_KEY[tag]).filter((key): key is string => !!key),
  );
  if (failedKeys.size === 0) return items;
  return items.map(item =>
    failedKeys.has(item.key) ? {...item, ok: false} : item,
  );
}

/**
 * When the child has checked in (readiness score present) but explicit perm* fields are
 * missing from the API, infer granted/denied from failureClassifications: absent tag ⇒ granted.
 */
export function inferReadinessFromFailureTags(
  items: ReadinessRow[],
  device: DeviceSummary,
  online: boolean,
): ReadinessRow[] {
  if (!online || device.deviceReadinessScore == null) {
    return items;
  }
  const tagSet = new Set(device.readinessFailureTags ?? []);
  return items.map(item => {
    if (item.ok !== null || item.key === 'online') {
      return item;
    }
    const failureTag = READINESS_KEY_TO_FAILURE_TAG[item.key];
    if (failureTag) {
      return {...item, ok: !tagSet.has(failureTag)};
    }
    return item;
  });
}

/** True when every non-online checklist row is explicitly granted. */
export function isReadinessFullyGranted(
  items: ReadonlyArray<{key: string; ok: boolean | null}>,
): boolean {
  const checkable = items.filter(item => item.key !== 'online');
  return checkable.length > 0 && checkable.every(item => item.ok === true);
}

/** True when any non-online row is still unknown (no explicit grant/deny yet). */
export function hasUnknownReadinessRows(
  items: ReadonlyArray<{key: string; ok: boolean | null}>,
): boolean {
  return items.some(item => item.key !== 'online' && item.ok === null);
}

export function buildRemoteCameraReadinessRows(
  device: DeviceSummary,
  online: boolean,
): ReadinessRow[] {
  const a11yOk = resolveAccessibilityReadiness(device);
  const rows: ReadinessRow[] = [
    {
      key: 'online',
      label: 'Device online',
      ok: online,
      fixHint:
        'Child phone must reach the server. Open Clock Time or wait for the next heartbeat.',
    },
    {
      key: 'battery',
      label: 'Battery unrestricted',
      ok: device.permBattery ?? null,
      fixHint: 'On the child phone: turn off battery optimization for Clock Time.',
    },
    {
      key: 'autostart',
      label: 'Autostart allowed',
      ok: device.permAutostart ?? null,
      fixHint:
        'On Xiaomi and similar phones: enable Autostart for Clock Time in Security settings.',
    },
    {
      key: 'a11y',
      label: 'Accessibility service',
      ok: a11yOk,
      fixHint: 'On the child phone: keep the Clock Time accessibility service enabled.',
    },
    {
      key: 'overlay',
      label: 'Display over other apps',
      ok: device.permOverlay ?? null,
      fixHint:
        'On the child phone: allow Clock Time to display over other apps for reliable remote camera startup.',
    },
    {
      key: 'camera',
      label: 'Camera permission',
      ok: device.permCamera ?? null,
      fixHint: 'On the child phone: grant camera permission to Clock Time.',
    },
    {
      key: 'monitoring',
      label: 'Background monitoring active',
      ok: resolveMonitoringReadiness(device),
      fixHint:
        'Android limited background work. Open Clock Time on the child phone to restore monitoring.',
    },
  ];

  return inferReadinessFromFailureTags(
    applyReadinessFailureTags(rows, device.readinessFailureTags),
    device,
    online,
  );
}

/** True when every checklist row is granted but the child score says otherwise. */
export function hasScoreReadinessMismatch(
  device: DeviceSummary,
  readinessItems: ReadonlyArray<{key: string; ok: boolean | null}>,
): boolean {
  const score = device.deviceReadinessScore;
  if (score == null) return false;
  const checkable = readinessItems.filter(item => item.key !== 'online' && item.ok !== null);
  if (checkable.length === 0) return false;
  const allGranted = checkable.every(item => item.ok === true);
  return allGranted && score < 85;
}

/** Downgrade granted rows to unknown when the child score contradicts the checklist. */
export function distrustGrantedWhenScoreMismatch(
  items: ReadinessRow[],
  device: DeviceSummary,
): ReadinessRow[] {
  if (!hasScoreReadinessMismatch(device, items)) return items;
  return items.map(item =>
    item.key !== 'online' && item.ok === true ? {...item, ok: null} : item,
  );
}

/** True when granted checklist rows conflict with child failure tags (even while online). */
export function hasUntrustedReadinessSnapshot(
  readinessItems: ReadonlyArray<{key: string; ok: boolean | null}>,
  failureTags?: string[] | null,
): boolean {
  if (!failureTags?.length) return false;
  for (const tag of failureTags) {
    const key = FAILURE_TAG_TO_READINESS_KEY[tag];
    if (!key) continue;
    const row = readinessItems.find(item => item.key === key);
    if (row?.ok === true) return true;
  }
  return false;
}
