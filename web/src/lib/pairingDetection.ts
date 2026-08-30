export type PairingCodeStatusLike = {
  used?: boolean;
  expired?: boolean;
  deviceId?: string | null;
  deviceName?: string | null;
};

export type DeviceSummaryLike = {
  id: string;
  status?: string | null;
  lastSeenAt?: string | null;
  deviceName?: string | null;
};

/** Dismiss pairing card only when the code is claimed or expired. */
export function shouldDismissPairingCodeFromStatus(
  status: PairingCodeStatusLike | null | undefined,
): boolean {
  if (!status) {
    return false;
  }
  return Boolean(status.used || status.expired);
}

/**
 * New row in the device list since the code was generated.
 * Requires a genuinely new device id — never treat an already-known device as a new pairing.
 */
export function detectPairedDevice(
  list: DeviceSummaryLike[],
  baselineIds: Set<string> | ReadonlySet<string>,
  pairingStartedAt: number | null,
): DeviceSummaryLike | undefined {
  if (baselineIds.size === 0 && list.length === 0) {
    return undefined;
  }
  if (baselineIds.size === 0 && list.length > 0 && pairingStartedAt) {
    const graceMs = 3000;
    if (Date.now() - pairingStartedAt < graceMs) {
      return undefined;
    }
  }
  return list.find(device => !baselineIds.has(device.id));
}

/** Re-pair of the same physical device (e.g. after revoke) — id already in baseline but status improved. */
export function detectRePairedDevice(
  list: DeviceSummaryLike[],
  baselineIds: Set<string> | ReadonlySet<string>,
  baselineStatuses: Record<string, string>,
): DeviceSummaryLike | undefined {
  return list.find(device => {
    if (!baselineIds.has(device.id)) {
      return false;
    }
    const was = (baselineStatuses[device.id] ?? '').toUpperCase();
    const now = (device.status ?? '').toUpperCase();
    return was === 'REVOKED' && now === 'ONLINE';
  });
}

/** True when pairing completed via status poll, device list, or re-pair detection. */
export function shouldDismissPairingSession(
  status: PairingCodeStatusLike | null | undefined,
  list: DeviceSummaryLike[],
  baselineIds: Set<string>,
  baselineStatuses: Record<string, string>,
  pairingStartedAt: number | null,
): boolean {
  if (shouldDismissPairingCodeFromStatus(status)) {
    return true;
  }
  return (
    detectPairedDevice(list, baselineIds, pairingStartedAt) != null ||
    detectRePairedDevice(list, baselineIds, baselineStatuses) != null
  );
}
