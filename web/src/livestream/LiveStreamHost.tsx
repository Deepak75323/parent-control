import {useEffect} from 'react';
import {shouldReleaseViewerOnStreamingEnded} from '../lib/streamTakeover';
import {FloatingStreamPip} from './FloatingStreamPip';
import {getLiveStreamRoomId, useLiveStreamStore} from './liveStreamStore';
import {useRealtimeStore} from '../realtime/realtimeStore';

/** Keeps live WebRTC alive across route changes; renders floating PiP when away from device page. */
export function LiveStreamHost() {
  const streamingState = useRealtimeStore(s => s.streamingState);
  const deviceId = useLiveStreamStore(s => s.deviceId);
  const busy = useLiveStreamStore(s => s.busy);
  const hydratePinStatus = useLiveStreamStore(s => s.hydratePinStatus);
  const releaseFromTakeover = useLiveStreamStore(s => s.releaseFromTakeover);

  useEffect(() => {
    void hydratePinStatus();
  }, [hydratePinStatus]);

  useEffect(() => {
    if (!streamingState || busy) {
      return;
    }
    if (streamingState.deviceId && streamingState.deviceId !== deviceId) {
      return;
    }
    const activeRoom = getLiveStreamRoomId();
    if (!shouldReleaseViewerOnStreamingEnded(streamingState, activeRoom, busy)) {
      return;
    }
    releaseFromTakeover();
  }, [streamingState, deviceId, busy, releaseFromTakeover]);

  return <FloatingStreamPip />;
}
