import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  PanResponder,
  useWindowDimensions,
  ActivityIndicator,
  LayoutChangeEvent,
  Platform,
  Pressable,
} from 'react-native';
import {RTCView, MediaStream} from 'react-native-webrtc';
import {
  clampPipPosition,
  defaultPipPosition,
  getStreamLayout,
  STREAM_COLORS,
} from './layout';
import {StreamTopBar} from './StreamTopBar';
import {StreamControlBar} from './StreamControlBar';
import {flowLog} from '../../services/streamDebug';
import {mapContainTouchToNorm, videoAspectFromStream} from './remoteControlCoords';

type StreamType = 'SCREEN' | 'CAMERA' | 'AUDIO';

type Props = {
  deviceName: string;
  streamType: StreamType;
  statusLabel: string;
  isLive: boolean;
  isPending: boolean;
  isFailed: boolean;
  primaryStream: MediaStream | null;
  pipStream: MediaStream | null;
  pipVisible: boolean;
  onBack: () => void;
  onStop: () => void;
  onSwitchCamera?: () => void;
  onTogglePip?: () => void;
  remoteControlEnabled?: boolean;
  onToggleRemoteControl?: () => void;
  onRemoteTap?: (xNorm: number, yNorm: number) => void;
  onRemoteSwipe?: (x1Norm: number, y1Norm: number, x2Norm: number, y2Norm: number) => void;
  onRemoteBack?: () => void;
  onRemoteHome?: () => void;
  onRemoteTypeText?: () => void;
  onRemoteLaunchApp?: () => void;
  onReconnect?: () => void;
  stopping: boolean;
  topInset: number;
  bottomInset: number;
  /** Whether the incoming audio track is muted on the parent side (camera streams only) */
  audioMuted?: boolean;
  /** Called when the parent presses the mute/unmute audio button in camera view */
  onToggleAudioMute?: () => void;
};

const RemoteRtcView = React.memo(function RemoteRtcView({
  stream,
  style,
  objectFit,
  zOrder,
}: {
  stream: MediaStream;
  style: object;
  objectFit: 'contain' | 'cover';
  zOrder?: number;
}) {
  useEffect(() => {
    const video = stream.getVideoTracks()[0];
    flowLog('WEBRTC_FLOW', 'RTCVIEW_STREAM_ATTACHED', {
      streamURL: stream.toURL(),
      trackId: video?.id,
      enabled: video?.enabled,
      readyState: video?.readyState,
      muted: video?.muted,
    });
  }, [stream]);

  return (
    <RTCView
      streamURL={stream.toURL()}
      style={style}
      objectFit={objectFit}
      mirror={false}
      zOrder={zOrder}
    />
  );
});

export function LiveStreamViewport({
  deviceName,
  streamType,
  statusLabel,
  isLive,
  isPending,
  isFailed,
  primaryStream,
  pipStream,
  pipVisible,
  onBack,
  onStop,
  onSwitchCamera,
  onTogglePip,
  remoteControlEnabled = false,
  onToggleRemoteControl,
  onRemoteTap,
  onRemoteSwipe,
  onRemoteBack,
  onRemoteHome,
  onRemoteTypeText,
  onRemoteLaunchApp,
  onReconnect,
  stopping,
  topInset,
  bottomInset,
  audioMuted,
  onToggleAudioMute,
}: Props) {
  const window = useWindowDimensions();
  const layout = useMemo(() => getStreamLayout(window), [window]);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [viewportSize, setViewportSize] = useState({width: window.width, height: window.height});
  const [pipPos, setPipPos] = useState(() =>
    defaultPipPosition(layout, window.width, window.height),
  );

  const pipPosRef = useRef(pipPos);
  pipPosRef.current = pipPos;

  useEffect(() => {
    setPipPos(defaultPipPosition(layout, viewportSize.width, viewportSize.height));
  }, [layout, viewportSize.width, viewportSize.height]);

  const onViewportLayout = useCallback((e: LayoutChangeEvent) => {
    const {width, height} = e.nativeEvent.layout;
    setViewportSize({width, height});
  }, []);

  const swipeStartRef = useRef<{x: number; y: number} | null>(null);
  const videoAspect = useMemo(
    () => videoAspectFromStream(primaryStream),
    [primaryStream],
  );

  const pipPanResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: () => true,
        onPanResponderGrant: () => {
          pipPosRef.current = pipPos;
        },
        onPanResponderMove: (_, gesture) => {
          const next = clampPipPosition(
            pipPosRef.current.x + gesture.dx,
            pipPosRef.current.y + gesture.dy,
            layout.pipWidth,
            layout.pipHeight,
            viewportSize.width,
            viewportSize.height,
          );
          setPipPos(next);
        },
        onPanResponderRelease: (_, gesture) => {
          const next = clampPipPosition(
            pipPosRef.current.x + gesture.dx,
            pipPosRef.current.y + gesture.dy,
            layout.pipWidth,
            layout.pipHeight,
            viewportSize.width,
            viewportSize.height,
          );
          setPipPos(next);
          pipPosRef.current = next;
        },
      }),
    [layout.pipWidth, layout.pipHeight, viewportSize.width, viewportSize.height, pipPos],
  );

  const remotePanResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => remoteControlEnabled && isLive && streamType === 'SCREEN',
        onMoveShouldSetPanResponder: (_, gesture) =>
          remoteControlEnabled && isLive && streamType === 'SCREEN' && Math.hypot(gesture.dx, gesture.dy) > 8,
        onPanResponderGrant: (_, gesture) => {
          swipeStartRef.current = {x: gesture.x0, y: gesture.y0};
        },
        onPanResponderRelease: (_, gesture) => {
          const start = swipeStartRef.current;
          swipeStartRef.current = null;
          if (!start) return;
          const moved = Math.hypot(gesture.dx, gesture.dy);
          const endNorm = mapContainTouchToNorm(
            gesture.moveX,
            gesture.moveY,
            viewportSize.width,
            viewportSize.height,
            videoAspect,
          );
          if (!endNorm) return;
          if (moved < 12) {
            onRemoteTap?.(endNorm.xNorm, endNorm.yNorm);
            return;
          }
          const startNorm = mapContainTouchToNorm(
            start.x,
            start.y,
            viewportSize.width,
            viewportSize.height,
            videoAspect,
          );
          if (startNorm && onRemoteSwipe) {
            onRemoteSwipe(
              startNorm.xNorm,
              startNorm.yNorm,
              endNorm.xNorm,
              endNorm.yNorm,
            );
          }
        },
      }),
    [
      remoteControlEnabled,
      isLive,
      streamType,
      viewportSize.width,
      viewportSize.height,
      videoAspect,
      onRemoteTap,
      onRemoteSwipe,
    ],
  );

  const streamLabel =
    streamType === 'SCREEN' ? 'Screen' : streamType === 'CAMERA' ? 'Camera' : 'Audio';

  const showPip = pipVisible && pipStream && streamType !== 'AUDIO';
  const showSwitchCamera = isLive && streamType === 'CAMERA' && !!onSwitchCamera;
  const showPipToggle = streamType === 'SCREEN' && !!pipStream && !!onTogglePip;

  const topBarVisible = !isFullscreen;
  const controlBarVisible = !isFullscreen;

  return (
    <View style={styles.root} onLayout={onViewportLayout}>
      <View style={styles.primaryStage}>
        {primaryStream && streamType === 'AUDIO' && (Platform.OS === 'ios' || isLive) ? (
          <RemoteRtcView stream={primaryStream} style={styles.audioSink} objectFit="cover" />
        ) : null}
        {streamType === 'AUDIO' && isLive ? (
          <View style={styles.audioLivePanel}>
            <Text style={styles.audioLiveIcon}>🎙️</Text>
            <Text style={styles.audioLiveTitle}>Listening</Text>
            <Text style={styles.audioLiveHint}>Audio plays through your device speaker</Text>
          </View>
        ) : null}
        {primaryStream && streamType !== 'AUDIO' && isLive ? (
          <View style={styles.primaryVideo} {...(remoteControlEnabled ? remotePanResponder.panHandlers : {})}>
            <RemoteRtcView
              stream={primaryStream}
              style={StyleSheet.absoluteFillObject}
              objectFit="contain"
            />
          </View>
        ) : !primaryStream || !isLive ? (
          <View style={styles.placeholder}>
            {isPending ? <ActivityIndicator size="large" color={STREAM_COLORS.accent} /> : null}
            <Text style={styles.placeholderIcon}>
              {streamType === 'AUDIO' ? '🎙️' : streamType === 'CAMERA' ? '📷' : '📺'}
            </Text>
            <Text style={styles.placeholderText}>{statusLabel}</Text>
            {isFailed && onReconnect ? (
              <Pressable style={styles.reconnectBtn} onPress={onReconnect}>
                <Text style={styles.reconnectBtnText}>Reconnect</Text>
              </Pressable>
            ) : null}
          </View>
        ) : null}
      </View>

      {showPip ? (
        <View
          {...pipPanResponder.panHandlers}
          style={[
            styles.pip,
            {
              width: layout.pipWidth,
              height: layout.pipHeight,
              left: pipPos.x,
              top: pipPos.y,
            },
          ]}>
          <RemoteRtcView
            stream={pipStream}
            style={styles.pipVideo}
            objectFit="cover"
            zOrder={2}
          />
          <View style={styles.pipBadge}>
            <Text style={styles.pipBadgeText}>Camera</Text>
          </View>
        </View>
      ) : null}

      {topBarVisible ? (
        <StreamTopBar
          deviceName={deviceName}
          statusLabel={statusLabel}
          isLive={isLive}
          isPending={isPending}
          isFailed={isFailed}
          streamLabel={streamLabel}
          onBack={onBack}
          topInset={topInset}
        />
      ) : null}

      {controlBarVisible ? (
        <StreamControlBar
          onStop={onStop}
          onSwitchCamera={onSwitchCamera}
          onToggleFullscreen={() => setIsFullscreen(v => !v)}
          onTogglePip={onTogglePip}
          remoteControlEnabled={remoteControlEnabled}
          onToggleRemoteControl={onToggleRemoteControl}
          onRemoteBack={onRemoteBack}
          onRemoteHome={onRemoteHome}
          onRemoteTypeText={onRemoteTypeText}
          onRemoteLaunchApp={onRemoteLaunchApp}
          showRemoteControl={isLive && streamType === 'SCREEN'}
          stopping={stopping}
          showSwitchCamera={showSwitchCamera}
          showPipToggle={showPipToggle}
          pipVisible={pipVisible}
          isFullscreen={isFullscreen}
          bottomInset={bottomInset}
          audioMuted={streamType === 'CAMERA' ? audioMuted : undefined}
          onToggleAudioMute={streamType === 'CAMERA' ? onToggleAudioMute : undefined}
        />
      ) : isFullscreen ? (
        <View style={[styles.fullscreenExit, {top: topInset + 8}]}>
          <Text style={styles.fullscreenHint} onPress={() => setIsFullscreen(false)}>
            Tap to show controls
          </Text>
        </View>
      ) : null}

    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: STREAM_COLORS.bg,
    overflow: 'hidden',
  },
  primaryStage: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
  },
  primaryVideo: {
    width: '100%',
    height: '100%',
  },
  audioSink: {
    width: 1,
    height: 1,
    position: 'absolute',
    left: 0,
    bottom: 0,
  },
  audioLivePanel: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 8,
  },
  audioLiveIcon: {
    fontSize: 56,
  },
  audioLiveTitle: {
    color: STREAM_COLORS.text,
    fontSize: 22,
    fontWeight: '700',
  },
  audioLiveHint: {
    color: STREAM_COLORS.textMuted,
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
  },
  placeholder: {
    flex: 1,
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 12,
  },
  placeholderIcon: {
    fontSize: 48,
  },
  placeholderText: {
    color: STREAM_COLORS.textMuted,
    fontSize: 15,
    textAlign: 'center',
    lineHeight: 22,
  },
  reconnectBtn: {
    marginTop: 16,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: STREAM_COLORS.accent,
  },
  reconnectBtnText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '600',
  },
  pip: {
    position: 'absolute',
    zIndex: 15,
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.35)',
    backgroundColor: '#0f172a',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.35,
    shadowRadius: 8,
  },
  pipVideo: {
    width: '100%',
    height: '100%',
  },
  pipBadge: {
    position: 'absolute',
    left: 6,
    top: 6,
    backgroundColor: STREAM_COLORS.overlay,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  pipBadgeText: {
    color: STREAM_COLORS.text,
    fontSize: 10,
    fontWeight: '700',
  },
  fullscreenExit: {
    position: 'absolute',
    right: 12,
    zIndex: 25,
    backgroundColor: STREAM_COLORS.overlay,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
  },
  fullscreenHint: {
    color: STREAM_COLORS.text,
    fontSize: 12,
  },
});
