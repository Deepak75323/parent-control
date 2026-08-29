import {
  applyReadinessFailureTags,
  buildRemoteCameraReadinessRows,
  hasUnknownReadinessRows,
  hasScoreReadinessMismatch,
  hasUntrustedReadinessSnapshot,
  inferReadinessFromFailureTags,
  isReadinessFullyGranted,
  resolveAccessibilityReadiness,
  resolveMonitoringReadiness,
} from '../utils/deviceReadiness';
import type {DeviceSummary} from '../services/api';

describe('resolveAccessibilityReadiness', () => {
  it('returns false when child reported ACCESSIBILITY_DISABLED', () => {
    const device = {
      readinessFailureTags: ['ACCESSIBILITY_DISABLED'],
      permAccessibility: true,
      accessibilityEnabled: true,
      accessibilityServiceConnected: true,
    } as DeviceSummary;
    expect(resolveAccessibilityReadiness(device)).toBe(false);
  });

  it('returns false when permAccessibility is false', () => {
    const device = {permAccessibility: false} as DeviceSummary;
    expect(resolveAccessibilityReadiness(device)).toBe(false);
  });

  it('returns true when enabled in settings even if connected flag is stale false', () => {
    const device = {
      accessibilityEnabled: true,
      accessibilityServiceConnected: false,
      readinessFailureTags: [],
    } as DeviceSummary;
    expect(resolveAccessibilityReadiness(device)).toBe(true);
  });
});

describe('resolveMonitoringReadiness', () => {
  it('returns true for conservative mode without failure tag', () => {
    const device = {
      monitoringDegraded: true,
      monitoringMode: 'CONSERVATIVE',
      readinessFailureTags: [],
    } as DeviceSummary;
    expect(resolveMonitoringReadiness(device)).toBe(true);
  });

  it('returns false for workmanager-only mode', () => {
    const device = {
      monitoringDegraded: true,
      monitoringMode: 'WORKMANAGER_ONLY',
      readinessFailureTags: [],
    } as DeviceSummary;
    expect(resolveMonitoringReadiness(device)).toBe(false);
  });
});

describe('applyReadinessFailureTags', () => {
  const rows = [
    {key: 'a11y', label: 'Accessibility', ok: true, fixHint: ''},
    {key: 'battery', label: 'Battery', ok: true, fixHint: ''},
  ];

  it('marks accessibility false when tag is present', () => {
    const out = applyReadinessFailureTags(rows, ['ACCESSIBILITY_DISABLED']);
    expect(out.find(r => r.key === 'a11y')?.ok).toBe(false);
    expect(out.find(r => r.key === 'battery')?.ok).toBe(true);
  });
});

describe('hasUntrustedReadinessSnapshot', () => {
  it('is true when a11y row is granted but failure tag says disabled', () => {
    expect(
      hasUntrustedReadinessSnapshot(
        [{key: 'a11y', ok: true}],
        ['ACCESSIBILITY_DISABLED'],
      ),
    ).toBe(true);
  });
});

describe('hasScoreReadinessMismatch', () => {
  it('is true when all rows granted but score is below full readiness', () => {
    const device = {deviceReadinessScore: 70} as DeviceSummary;
    const items = [
      {key: 'online', ok: true},
      {key: 'a11y', ok: true},
      {key: 'battery', ok: true},
    ];
    expect(hasScoreReadinessMismatch(device, items)).toBe(true);
  });
});

describe('inferReadinessFromFailureTags', () => {
  const rows = [
    {key: 'battery', label: 'Battery', ok: null, fixHint: ''},
    {key: 'camera', label: 'Camera', ok: null, fixHint: ''},
  ];

  it('marks rows granted when the child score is present and the failure tag is absent', () => {
    const device = {deviceReadinessScore: 70, readinessFailureTags: []} as DeviceSummary;
    const out = inferReadinessFromFailureTags(rows, device, true);
    expect(out.find(r => r.key === 'battery')?.ok).toBe(true);
    expect(out.find(r => r.key === 'camera')?.ok).toBe(true);
  });

  it('marks rows denied when the matching failure tag is present', () => {
    const device = {
      deviceReadinessScore: 55,
      readinessFailureTags: ['CAMERA_NOT_GRANTED'],
    } as DeviceSummary;
    const out = inferReadinessFromFailureTags(rows, device, true);
    expect(out.find(r => r.key === 'battery')?.ok).toBe(true);
    expect(out.find(r => r.key === 'camera')?.ok).toBe(false);
  });
});

describe('buildRemoteCameraReadinessRows', () => {
  it('shows checkmarks for granted permissions when only the readiness score is synced', () => {
    const device = {
      deviceReadinessScore: 70,
      readinessFailureTags: [],
      monitoringDegraded: false,
    } as DeviceSummary;
    const rows = buildRemoteCameraReadinessRows(device, true);
    expect(rows.find(r => r.key === 'battery')?.ok).toBe(true);
    expect(rows.find(r => r.key === 'camera')?.ok).toBe(true);
    expect(isReadinessFullyGranted(rows)).toBe(true);
    expect(hasUnknownReadinessRows(rows)).toBe(false);
  });
});

describe('isReadinessFullyGranted', () => {
  it('is false when any permission row is still unknown', () => {
    expect(
      isReadinessFullyGranted([
        {key: 'online', ok: true},
        {key: 'battery', ok: null},
      ]),
    ).toBe(false);
  });
});
