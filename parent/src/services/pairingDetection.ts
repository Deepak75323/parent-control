import type {DeviceSummary} from '../services/api';

/**
 * New row in the device list since the code was generated.
 * Requires a genuinely new device id — never treat an already-known device as a new pairing.
 */
export function detectPairedDevice(
  list: DeviceSummary[],
  baselineIds: Set<string> | ReadonlySet<string>,
  pairingStartedAt: number | null,
): DeviceSummary | undefined {
  if (baselineIds.size === 0 && list.length === 0) {
    return undefined;
  }
  // When baseline was empty but devices already exist, only accept a device that
  // appeared after the code was generated (guards against empty-baseline false positives).
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
  list: DeviceSummary[],
  baselineIds: Set<string> | ReadonlySet<string>,
  baselineStatuses: Record<string, string>,
): DeviceSummary | undefined {
  return list.find(device => {
    if (!baselineIds.has(device.id)) {
      return false;
    }
    const was = (baselineStatuses[device.id] ?? '').toUpperCase();
    const now = (device.status ?? '').toUpperCase();
    return was === 'REVOKED' && now === 'ONLINE';
  });
}

export type PairingCodeStatusLike = {
  used?: boolean;
  expired?: boolean;
  deviceId?: string | null;
  deviceName?: string | null;
};

/**
 * Whether the parent should dismiss the active pairing-code card from a status poll.
 * Claimed codes clear even when deviceId is missing; expired codes must not keep "Waiting…".
 * Do not dismiss for any other reason (existing devices, WS flicker, etc.).
 */
export function shouldDismissPairingCodeFromStatus(
  status: PairingCodeStatusLike | null | undefined,
): boolean {
  if (!status) {
    return false;
  }
  return Boolean(status.used || status.expired);
}

/**
 * Parent B waiting for a cross-parent re-pair: keep showing the code until the
 * backend marks this specific code used/expired. Device-list heuristics are secondary.
 */
export function shouldKeepPairingCodeVisible(
  pendingCode: string | null | undefined,
  status: PairingCodeStatusLike | null | undefined,
): boolean {
  if (!pendingCode) {
    return false;
  }
  return !shouldDismissPairingCodeFromStatus(status);
}

/** True when the parent already had devices when the code was created (copy only — still wait for claim). */
export function isAdditionalDevicePairing(baselineSize: number): boolean {
  return baselineSize > 0;
}
