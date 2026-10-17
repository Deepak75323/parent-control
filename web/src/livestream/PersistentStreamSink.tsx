import {useEffect, useRef} from 'react';
import {useLiveStreamStore} from './liveStreamStore';

/**
 * Keeps a hidden media element attached while a session is active.
 * Mobile browsers often suspend WebRTC when the visible viewer unmounts during route changes.
 */
export function PersistentStreamSink() {
  const sessionId = useLiveStreamStore(s => s.sessionId);
  const remoteStream = useLiveStreamStore(s => s.remoteStream);
  const streamType = useLiveStreamStore(s => s.streamType);
  const muted = useLiveStreamStore(s => s.muted);
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const el = videoRef.current;
    if (!el || !sessionId || !remoteStream || streamType === 'AUDIO') {
      return;
    }
    el.srcObject = remoteStream;
    // The camera picture is a canvas, so this hidden element is what plays the room mic.
    // Screen share already plays through the visible video, so keep this copy silent.
    el.muted = streamType === 'CAMERA' ? muted : true;
    void el.play().catch(() => undefined);
  }, [sessionId, remoteStream, streamType, muted]);

  if (!sessionId || streamType === 'AUDIO') {
    return null;
  }

  return (
    <video
      ref={videoRef}
      playsInline
      autoPlay
      muted={streamType === 'CAMERA' ? muted : true}
      aria-hidden
      tabIndex={-1}
      className="stream-persistent-sink"
    />
  );
}
