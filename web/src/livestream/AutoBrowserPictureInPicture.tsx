import {useEffect, useRef} from 'react';
import {isChildVideoSession, shouldReleaseMeetPipForCamera} from './browserPip';
import {useLiveStreamStore} from './liveStreamStore';
import {closeMeetStylePip, documentPipApi, isChatPipWithoutCamera, isMeetPipOpen, openMeetStylePip} from './meetPipWindow';
import {isLivePipCueRunning, startLivePipCue, stopLivePipCue} from './pipAudible';
import {startBrowserCaptureForPip, stopBrowserCaptureForPip} from './pipBrowserCapture';
import {isMobilePipViewport} from './pipLayout';

type PipVideo = HTMLVideoElement & {autoPictureInPicture?: boolean};

function supportsBrowserPip(): boolean {
  return typeof document !== 'undefined' && document.pictureInPictureEnabled === true;
}

function requestVideoPip(video: HTMLVideoElement | null) {
  if (!video || !supportsBrowserPip() || document.pictureInPictureElement === video) {
    return;
  }
  void video.requestPictureInPicture().then(
    () => useLiveStreamStore.getState().setBrowserPipActive(true),
    () => undefined,
  );
}

/**
 * Desktop: the in-page floating video stays while this site is on screen.
 * Chrome opens the Meet-style window only when the parent switches to another tab.
 * Phones keep the in-page floating window.
 */
export function AutoBrowserPictureInPicture() {
  const videoRef = useRef<PipVideo>(null);
  const sessionId = useLiveStreamStore(s => s.sessionId);
  const streamType = useLiveStreamStore(s => s.streamType);
  const remoteStream = useLiveStreamStore(s => s.remoteStream);
  const muted = useLiveStreamStore(s => s.muted);
  const deviceName = useLiveStreamStore(s => s.deviceName);
  const setBrowserPipActive = useLiveStreamStore(s => s.setBrowserPipActive);

  const videoSession = isChildVideoSession(sessionId, streamType);
  const desktop = !isMobilePipViewport();
  const active = videoSession && desktop && Boolean(remoteStream) && supportsBrowserPip();

  useEffect(() => {
    const el = videoRef.current;
    if (!el || !active || !remoteStream) {
      return;
    }
    el.srcObject = remoteStream;
    el.autoPictureInPicture = false;
    el.muted = true;
    void el.play().catch(() => undefined);
  }, [active, remoteStream, streamType, muted]);

  useEffect(() => {
    if (!active || !('mediaSession' in navigator)) {
      return;
    }
    const metadata = new MediaMetadata({
      title: deviceName ? `${deviceName} live view` : 'Child live view',
      artist: 'Parent Control',
    });
    navigator.mediaSession.metadata = metadata;
    navigator.mediaSession.playbackState = 'playing';

    const enterFromBrowser = () => {
      if (isMeetPipOpen()) {
        setBrowserPipActive(true);
        return;
      }
      if (documentPipApi()) {
        void openMeetStylePip().catch(() => requestVideoPip(videoRef.current));
        return;
      }
      requestVideoPip(videoRef.current);
    };

    let registered = false;
    try {
      navigator.mediaSession.setActionHandler(
        'enterpictureinpicture' as MediaSessionAction,
        enterFromBrowser,
      );
      registered = true;
    } catch {
      registered = false;
    }

    return () => {
      if (registered) {
        try {
          navigator.mediaSession.setActionHandler(
            'enterpictureinpicture' as MediaSessionAction,
            null,
          );
        } catch {
          /* action not supported */
        }
      }
      if (navigator.mediaSession.metadata === metadata) {
        navigator.mediaSession.metadata = null;
        navigator.mediaSession.playbackState = 'none';
      }
    };
  }, [active, deviceName, setBrowserPipActive]);

  useEffect(() => {
    if (!active) {
      return;
    }
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        return;
      }
      if (isMeetPipOpen()) {
        closeMeetStylePip();
      }
      if (document.pictureInPictureElement) {
        void document.exitPictureInPicture().then(
          () => setBrowserPipActive(false),
          () => undefined,
        );
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [active, setBrowserPipActive]);

  useEffect(() => {
    const el = videoRef.current;
    if (!el) {
      return;
    }
    const onLeave = () => setBrowserPipActive(false);
    const onEnter = () => setBrowserPipActive(true);
    el.addEventListener('leavepictureinpicture', onLeave);
    el.addEventListener('enterpictureinpicture', onEnter);
    return () => {
      el.removeEventListener('leavepictureinpicture', onLeave);
      el.removeEventListener('enterpictureinpicture', onEnter);
    };
  }, [active, setBrowserPipActive]);

  useEffect(() => {
    if (!videoSession || !desktop) {
      stopBrowserCaptureForPip();
      if (!videoSession && isLivePipCueRunning()) {
        stopLivePipCue();
      }
      if (videoSession) {
        return;
      }
      if (!shouldReleaseMeetPipForCamera(false, isChatPipWithoutCamera())) {
        return;
      }
      closeMeetStylePip();
      if (document.pictureInPictureElement) {
        void document.exitPictureInPicture().catch(() => undefined);
      }
      setBrowserPipActive(false);
      return;
    }
    let cancelled = false;
    void startBrowserCaptureForPip().then(capturing => {
      if (cancelled) {
        return;
      }
      stopLivePipCue();
      if (capturing || streamType === 'CAMERA') {
        return;
      }
      startLivePipCue();
    });
    return () => {
      cancelled = true;
    };
  }, [videoSession, desktop, setBrowserPipActive, streamType]);

  if (!active) {
    return null;
  }

  return (
    <video
      ref={videoRef}
      className="stream-browser-pip-source"
      playsInline
      autoPlay
      muted={streamType === 'SCREEN' ? muted : true}
    />
  );
}
