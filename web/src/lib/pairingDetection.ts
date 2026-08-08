export type PairingCodeStatusLike = {
  used?: boolean;
  expired?: boolean;
  deviceId?: string | null;
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
