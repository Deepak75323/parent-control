import {useEffect, useRef} from 'react';
import {deviceApi, DeviceSummary} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {navigateToDeviceList} from '../navigation/navigationRef';
import {plog} from '../services/pairingLog';

const PAIR_POLL_MS = 3000;

/**
 * New row in the device list since the code was generated.
 * Requires a genuinely new device id — never treat an already-known device as a new pairing.
 */
function detectPairedDevice(
  list: DeviceSummary[],
  baselineIds: Set<string>,
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
function detectRePairedDevice(
  list: DeviceSummary[],
  baselineIds: Set<string>,
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

/** Polls device list while a pairing code is active — works even if WS is down or device re-pairs. */
export function PairingPollListener() {
  const pendingPairingCode = useRealtimeStore(s => s.pendingPairingCode);
  const pairingStartedAt = useRealtimeStore(s => s.pairingStartedAt);
  const pairingBaselineIds = useRealtimeStore(s => s.pairingBaselineIds);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
    if (!pendingPairingCode || !pairingStartedAt) {
      return;
    }

    const poll = async () => {
      try {
        const store = useRealtimeStore.getState();
        const code = store.pendingPairingCode;
        if (!code) {
          return;
        }
        const {data: statusResp} = await deviceApi.getPairingCodeStatus(code);
        const status = statusResp.data;
        if (status?.used && status.deviceId) {
          plog('app poll detected pairing via code status', status);
          store.notifyPairingComplete({
            deviceId: status.deviceId,
            deviceName: status.deviceName?.trim() || 'Child device',
          });
          navigateToDeviceList();
          return;
        }

        const {data} = await deviceApi.list();
        const list = data.data ?? [];
        store.mergeDevices(list);

        const matched = detectPairedDevice(
          list,
          store.pairingBaselineIds,
          store.pairingStartedAt,
        );
        if (matched) {
          plog('app poll detected pairing', {
            deviceId: matched.id,
            deviceName: matched.deviceName,
            lastSeenAt: matched.lastSeenAt,
          });
          store.notifyPairingComplete({
            deviceId: matched.id,
            deviceName: matched.deviceName,
          });
          navigateToDeviceList();
          return;
        }

        const rePaired = detectRePairedDevice(
          list,
          store.pairingBaselineIds,
          store.pairingBaselineStatuses,
        );
        if (rePaired) {
          plog('app poll detected re-pair', {
            deviceId: rePaired.id,
            deviceName: rePaired.deviceName,
          });
          store.notifyPairingComplete({
            deviceId: rePaired.id,
            deviceName: rePaired.deviceName,
          });
          navigateToDeviceList();
        }
      } catch (err) {
        plog('app poll error', String(err));
      }
    };

    poll().catch(() => undefined);
    pollRef.current = setInterval(() => {
      poll().catch(() => undefined);
    }, PAIR_POLL_MS);

    return () => {
      if (pollRef.current) {
        clearInterval(pollRef.current);
      }
    };
  }, [pendingPairingCode, pairingStartedAt, pairingBaselineIds]);

  return null;
}
