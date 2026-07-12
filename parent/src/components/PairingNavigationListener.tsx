import {useEffect, useRef} from 'react';
import {useRealtimeStore} from '../store/realtimeStore';
import {navigateToDeviceList} from '../navigation/navigationRef';
import {plog} from '../services/pairingLog';

/**
 * When child pairing succeeds, backend pushes WS events → notifyPairingComplete →
 * parent navigates to DeviceList automatically.
 */
export function PairingNavigationListener() {
  const pairingCompleteTick = useRealtimeStore(s => s.pairingCompleteTick);
  const lastPairedDevice = useRealtimeStore(s => s.lastPairedDevice);
  const lastHandledTick = useRef(0);

  useEffect(() => {
    if (pairingCompleteTick > lastHandledTick.current && lastPairedDevice) {
      lastHandledTick.current = pairingCompleteTick;
      plog('navigate to DeviceList', lastPairedDevice);
      navigateToDeviceList();
    }
  }, [pairingCompleteTick, lastPairedDevice]);

  return null;
}
