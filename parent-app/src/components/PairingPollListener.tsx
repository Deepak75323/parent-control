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
        const {data} = await deviceApi.list();
        const list = data.data ?? [];
        useRealtimeStore.getState().mergeDevices(list);

        const matched = detectPairedDevice(list, pairingBaselineIds, pairingStartedAt);
        if (matched) {
          plog('app poll detected pairing', {
            deviceId: matched.id,
            deviceName: matched.deviceName,
            lastSeenAt: matched.lastSeenAt,
          });
          useRealtimeStore.getState().notifyPairingComplete({
            deviceId: matched.id,
            deviceName: matched.deviceName,
          });
          navigateToDeviceList();
        }
      } catch {
        // ignore transient network errors during poll
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
