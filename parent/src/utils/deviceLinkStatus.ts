import type {DeviceSummary} from '../services/api';

export function isRevokedStatus(status?: string | null): boolean {
  return (status ?? '').toUpperCase() === 'REVOKED';
}

export type DeviceLinkPartitions<T extends {id: string; status: string}> = {
  active: T[];
  previouslyLinked: T[];
};

/**
 * Split devices into active plan seats vs revoked (previously linked).
 * Prefer live status when present so revoke updates move rows immediately.
 */
export function partitionDevicesByLinkStatus<T extends {id: string; status: string}>(
  devices: T[],
  liveStatuses?: Record<string, {status?: string} | undefined>,
): DeviceLinkPartitions<T> {
  const active: T[] = [];
  const previouslyLinked: T[] = [];
  for (const device of devices) {
    const status = liveStatuses?.[device.id]?.status ?? device.status;
    if (isRevokedStatus(status)) {
      previouslyLinked.push(device);
    } else {
      active.push(device);
    }
  }
  return {active, previouslyLinked};
}

/** Active (non-revoked) count for plan / profile stats. */
export function countActiveDevices(
  devices: DeviceSummary[],
  liveStatuses?: Record<string, {status?: string} | undefined>,
): number {
  return partitionDevicesByLinkStatus(devices, liveStatuses).active.length;
}
