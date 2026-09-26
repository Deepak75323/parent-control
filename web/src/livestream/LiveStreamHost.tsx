import {useEffect} from 'react';
import {streamFailureMessage} from '../lib/streamFailureMessages';
import {shouldReleaseViewerOnStreamingEnded} from '../lib/streamTakeover';
import {AutoBrowserPictureInPicture} from './AutoBrowserPictureInPicture';
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

  useEffect(() => {
    if (!streamingState || busy) {
      return;
    }
    if (streamingState.deviceId && streamingState.deviceId !== deviceId) {
      return;
    }
    if (streamingState.status !== 'FAILED') {
      return;
    }
    const activeRoom = getLiveStreamRoomId();
    if (streamingState.roomId && activeRoom && streamingState.roomId !== activeRoom) {
      return;
    }
    useLiveStreamStore.setState({
      error: streamFailureMessage(streamingState.failureReason),
      status: 'Failed',
      busy: false,
    });
  }, [streamingState, deviceId, busy]);

  return (
    <>
      <AutoBrowserPictureInPicture />
      <FloatingStreamPip />
    </>
  );
}
