import {useEffect, useRef} from 'react';
import {deviceApi, DeviceSummary} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {navigateToDeviceList} from '../navigation/navigationRef';
import {plog} from '../services/pairingLog';
import {
  detectPairedDevice,
  detectRePairedDevice,
  shouldDismissPairingCodeFromStatus,
} from '../services/pairingDetection';

const PAIR_POLL_MS = 3000;

function toIdSet(value: Set<string> | Iterable<string> | undefined): Set<string> {
  if (value instanceof Set) {
    return value;
  }
  return new Set(value ?? []);
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
        if (shouldDismissPairingCodeFromStatus(status)) {
          if (status?.used) {
            plog('app poll detected pairing via code status', status);
            store.notifyPairingComplete({
              deviceId: status.deviceId != null ? String(status.deviceId) : 'unknown',
              deviceName: status.deviceName?.trim() || 'Child device',
            });
            navigateToDeviceList();
          } else {
            plog('app poll clearing expired pairing code', status);
            store.clearPendingPairingCode();
          }
          return;
        }

        const {data} = await deviceApi.list();
        const list: DeviceSummary[] = data.data ?? [];
        store.mergeDevices(list);

        const baselineIds = toIdSet(store.pairingBaselineIds);
        const matched = detectPairedDevice(list, baselineIds, store.pairingStartedAt);
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
          baselineIds,
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
