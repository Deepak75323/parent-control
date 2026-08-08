import React, {useCallback, useEffect, useRef, useState} from 'react';
import {AlertButton, StatusBar, StyleSheet, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {MediaStream} from 'react-native-webrtc';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {RootStackParamList} from '../navigation/RootNavigator';
import {deviceApi, monitoringApi, parentPinApi, ScreenShareSession} from '../services/api';
import {
  connectParentViewer,
  disconnectViewer,
  getVideoTrackRecordingInfo,
  isRemoteAudioEnabledForRecording,
  reconnectParentViewer,
  registerStreamListeners,
  setRemoteAudioEnabled,
} from '../services/webrtcService';
import {stopBackendStreamSession} from '../services/streamAuthTeardown';
import {
  clearStickySessionId,
  getSessionIdForBackendStop,
  getSessionSnapshot,
} from '../services/streamSessionOwner';
import {shouldReleaseViewerOnStreamingEnded} from '../services/streamTakeover';
import {slog, slogReset} from '../services/streamDebug';
import {
  generateTraceId,
  setTraceId,
  setTraceState,
  traceLog,
  traceError,
  traceFailure,
} from '../utils/traceLogger';
import {streamStateTransition, resetStreamState} from '../utils/streamStateMachine';
import {getWebSocketClient} from '../services/websocketService';
import {getIceConnectionState} from '../services/webrtcService';
import {LiveStreamViewport} from '../components/liveMonitoring/LiveStreamViewport';
import {RemoteControlActionModals} from '../components/liveMonitoring/RemoteControlActionModals';
import {PreSessionLauncher} from '../components/liveMonitoring/PreSessionLauncher';
import {STREAM_COLORS} from '../components/liveMonitoring/layout';
import {useRealtimeStore} from '../store/realtimeStore';
import {streamFailureMessage} from '../utils/streamFailureMessages';
import {isDevicePresentOnline} from '../utils/devicePresence';
import {liveSessionUiState, sessionStatusLabel} from '../utils/liveSessionStatus';
import {
  isStreamRecordingSupported,
  showRecordingSavedAlert,
  startStreamRecording,
  stopStreamRecording,
} from '../services/streamRecording';
import {
  isScreenShareCapable,
  isSilentScreenCapable,
  mergeScreenShareCapability,
  screenShareBlockedMessage,
} from '../utils/screenShareCapability';
import {track} from '../services/telemetry';
import {TelemetryEvents} from '../services/telemetryEvents';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'LiveMonitoring'>;
type StreamType = 'SCREEN' | 'CAMERA' | 'AUDIO';

export function LiveMonitoringScreen({navigation, route}: Props) {
  const {deviceId, initialStream} = route.params;
  const insets = useSafeAreaInsets();

  const [deviceName, setDeviceName] = useState('Child device');
  const [silentScreenCapable, setSilentScreenCapable] = useState(false);
  const [mediaProjectionGranted, setMediaProjectionGranted] = useState(false);
  const [mediaProjectionConsentState, setMediaProjectionConsentState] = useState<string | undefined>();
  const [accessibilityEnabled, setAccessibilityEnabled] = useState(true);
  const streamingState = useRealtimeStore(s => s.streamingState);
  const liveStatus = useRealtimeStore(s => s.deviceStatuses[deviceId]);
  const [session, setSession] = useState<ScreenShareSession | null>(null);
  const [loading, setLoading] = useState(false);
  const [stopping, setStopping] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [iceState, setIceState] = useState<string | null>(null);
  const [streamType, setStreamType] = useState<StreamType>(initialStream ?? 'SCREEN');
  const [pinUnlocked, setPinUnlocked] = useState(false);
  const [pinInput, setPinInput] = useState('');
  const [pipVisible, setPipVisible] = useState(true);
  const [remoteControlEnabled, setRemoteControlEnabled] = useState(false);
  const [remoteActionModal, setRemoteActionModal] = useState<'type' | 'launch' | null>(null);
  const [recording, setRecording] = useState(false);
  const [recordingIncludedAudio, setRecordingIncludedAudio] = useState(false);
  const recordingSupported = isStreamRecordingSupported();
  const autoStartDone = useRef(false);

  useEffect(() => {
    slog('UI', 'LiveMonitoringScreen mounted', {deviceId, initialStream});
    const unregister = registerStreamListeners({
      onRemoteStream: stream => {
        slog('UI', 'remote stream received', {url: stream.toURL()});
        setRemoteStream(stream);
      },
      onConnectionState: state => {
        slog('UI', 'ICE state changed', state);
        setIceState(state);
      },
    }, `live-${deviceId}`);

    deviceApi
      .get(deviceId)
      .then(r => {
        const d = r.data.data;
        setDeviceName(d.deviceName?.trim() || 'Child device');
        setSilentScreenCapable(isSilentScreenCapable(d));
        setMediaProjectionGranted(d.mediaProjectionGranted === true);
        setMediaProjectionConsentState(d.mediaProjectionConsentState);
        setAccessibilityEnabled(d.accessibilityEnabled !== false);
        slog('UI', 'device loaded', d);
      })
      .catch(err => slog('UI', 'device load failed', String(err)));

    parentPinApi.status().then(r => setPinUnlocked(r.data.data.silentStreamUnlocked)).catch(() => undefined);

    return () => {
      unregister();
      slog('UI', 'LiveMonitoringScreen unmount — stop backend session + disconnect');
      // Must stopScreenShare before disconnectViewer clears local ids — otherwise the
      // child keeps camera/mic (green privacy indicators) while parent is on login.
      const sessionId = getSessionIdForBackendStop();
      void (async () => {
        if (sessionId) {
          await stopBackendStreamSession(sessionId);
        }
        await disconnectViewer().catch(() => undefined);
      })();
    };
  }, [deviceId, initialStream]);

  useEffect(() => {
    if (!liveStatus) return;
    const merged = mergeScreenShareCapability(liveStatus, {
      mediaProjectionGranted,
      mediaProjectionConsentState,
    });
    setMediaProjectionGranted(merged.mediaProjectionGranted === true);
    setMediaProjectionConsentState(merged.mediaProjectionConsentState);
    setSilentScreenCapable(
      isSilentScreenCapable({
        silentScreenCapable: liveStatus.silentScreenCapable,
        mediaProjectionGranted: merged.mediaProjectionGranted,
        mediaProjectionConsentState: merged.mediaProjectionConsentState,
      }),
    );
  }, [liveStatus, mediaProjectionGranted, mediaProjectionConsentState]);

  // Intentionally NO effect that clears connecting when mediaProjectionGranted is false.
  // Consent is often still "pending" while the child shows Cast / auto-approves; clearing
  // the spinner mid-start made the parent look broken even though connectParentViewer was
  // waiting for the offer. Connecting is cleared by remoteStream, ICE failure, streaming-state
  // FAILED, or the start() finally/catch path.

  useEffect(() => {
    if (remoteStream) setConnecting(false);
    if (iceState === 'failed' || iceState === 'disconnected') setConnecting(false);
  }, [remoteStream, iceState]);

  useEffect(() => {
    if (!streamingState) return;
    if (streamingState.deviceId && streamingState.deviceId !== deviceId) return;
    if (streamingState.status !== 'FAILED') return;
    setConnecting(false);
    setRemoteStream(null);
    appAlert('Live view could not start', streamFailureMessage(streamingState.failureReason));
  }, [streamingState, deviceId]);

  // Another parent client (e.g. web) took over this live session — release local viewer.
  useEffect(() => {
    if (!streamingState) return;
    if (streamingState.deviceId && streamingState.deviceId !== deviceId) return;
    const activeRoom =
      session?.signalingRoomId ?? getSessionSnapshot().roomId ?? undefined;
    if (!shouldReleaseViewerOnStreamingEnded(streamingState, activeRoom)) return;

    slog('UI', 'stream taken over by another client — releasing viewer', {
      roomId: streamingState.roomId,
      streamType: streamingState.streamType,
    });
    clearStickySessionId();
    void disconnectViewer().catch(() => undefined);
    setSession(null);
    setRemoteStream(null);
    setConnecting(false);
    setIceState(null);
    setLoading(false);
    resetStreamState();
    appAlert(
      'Live view moved',
      'This stream is now open on another parent device or the web portal.',
    );
  }, [streamingState, deviceId, session?.signalingRoomId]);

  const needsPinForScreen = silentScreenCapable && !pinUnlocked;
  const screenShareCapable = isScreenShareCapable({
    mediaProjectionGranted,
    mediaProjectionConsentState,
  });
  const screenShareBlockedReason = screenShareCapable
    ? undefined
    : screenShareBlockedMessage({
        mediaProjectionGranted,
        mediaProjectionConsentState,
      });

  const start = useCallback(
    async (type: StreamType) => {
      setStreamType(type);
      const traceId = generateTraceId();
      streamStateTransition('REQUEST_SENT', 'start', {deviceId, type, traceId});
      setTraceState('BUTTON_CLICK');
      const wsClient = getWebSocketClient();
      traceLog('Parent', 'start', 'Button clicked — stream start requested', {
        deviceId,
        type,
        traceId,
        selectedChild: deviceId,
        connectionState: liveStatus?.status ?? 'unknown',
        socketState: wsClient?.connected ? 'connected' : 'disconnected',
        webrtcState: getIceConnectionState() ?? 'none',
        screenShareCapable,
        mediaProjectionGranted,
        mediaProjectionConsentState,
      });
      if (type === 'SCREEN' && needsPinForScreen && !pinInput) {
        traceLog('Parent', 'start', 'Parent PIN required for silent screen');
        appAlert('Password required', 'Enter your parent password to start screen sharing.');
        return;
      }
      setLoading(true);
      slogReset();
      slog('UI', 'start stream clicked', {deviceId, type, traceId});
      setTraceState('API_REQUEST');
      try {
        traceLog('Parent', 'start', 'Fetching device before stream start', {deviceId});
        const deviceRes = await deviceApi.get(deviceId);
        const device = deviceRes.data.data;
        traceLog('Parent', 'start', 'Device fetched', {
          lastSeenAt: device.lastSeenAt,
          mediaProjectionGranted: device.mediaProjectionGranted,
          mediaProjectionConsentState: device.mediaProjectionConsentState,
        });
        setMediaProjectionGranted(device.mediaProjectionGranted === true);
        setMediaProjectionConsentState(device.mediaProjectionConsentState);
        setSilentScreenCapable(isSilentScreenCapable(device));
        setAccessibilityEnabled(device.accessibilityEnabled !== false);
        // Align with shared presence helper (trust fresh lastSeen even if status lags).
        const lastSeenAt = device.lastSeenAt;
        if (!isDevicePresentOnline(device.status, lastSeenAt)) {
          const staleMs = lastSeenAt ? Date.now() - Date.parse(lastSeenAt) : Number.POSITIVE_INFINITY;
          traceFailure({
            component: 'Parent',
            fn: 'start',
            reason: 'Child device offline',
            actual: `staleMs=${staleMs} status=${device.status}`,
            nextCauses: ['Child app not running', 'Network disconnected', 'Wrong device selected'],
          });
          appAlert(
            'Child device offline',
            'This phone has not checked in recently. Open Clock Time on the child phone, keep it on the same Wi‑Fi as your Mac (or plug in USB), then try again.\n\nIf you re-paired recently, use the newer device in the list — not an old duplicate.',
          );
          return;
        }

        traceLog('Parent', 'start', 'Sending start-stream API request', {deviceId, type, traceId});
        const {data} = await monitoringApi.startStream(
          deviceId,
          type,
          type === 'SCREEN' && silentScreenCapable ? pinInput || undefined : undefined,
        );
        const s = data.data;
        if (s.traceId) setTraceId(s.traceId);
        traceLog(
          type === 'CAMERA' ? 'Camera' : type === 'AUDIO' ? 'Audio' : 'Screen',
          'start',
          'Stream session created',
          {
            sessionId: s.sessionId,
            roomId: s.signalingRoomId,
            captureMode: s.captureMode,
            traceId: s.traceId ?? traceId,
          },
        );
        slog('FLOW', 'parent stream request sent', {
          deviceId,
          type,
          sessionId: s.sessionId,
          roomId: s.signalingRoomId,
          captureMode: s.captureMode,
          traceId: s.traceId ?? traceId,
        });
        setSession(s);
        setIceState(null);
        setRemoteStream(null);
        setConnecting(true);
        track(TelemetryEvents.STREAM_START, {stream_type: type});
        // Camera starts unmuted so parents hear the child mic immediately; Mute still works.
        setAudioMuted(false);
        setRemoteAudioEnabled(true);
        if (s.captureMode === 'NATIVE') setPinUnlocked(true);
        setTraceState('WEBRTC_CONNECT');
        traceLog('WebRTC', 'connectParentViewer', 'Connecting parent viewer', {
          roomId: s.signalingRoomId,
          sessionId: s.sessionId,
        });
        await connectParentViewer(s.signalingRoomId, s.sessionId, type);
        setSession(prev => (prev ? {...prev, status: 'ACTIVE'} : prev));
        streamStateTransition('STREAMING', 'start', {roomId: s.signalingRoomId, sessionId: s.sessionId});
        setTraceState('ACTIVE');
        traceLog('Parent', 'start', 'Stream start complete', {
          roomId: s.signalingRoomId,
          sessionId: s.sessionId,
        });
      } catch (e: unknown) {
        const msg =
          (e as {response?: {data?: {message?: string}}})?.response?.data?.message ??
          (e instanceof Error ? e.message : 'Couldn’t start live view. Please try again.');
        traceError('Parent', 'start', 'Stream start failed', e);
        streamStateTransition('FAILED', 'start', {reason: msg});
        traceFailure({
          component: 'Parent',
          fn: 'start',
          reason: msg,
          nextCauses: ['Backend rejected request', 'Child offline', 'Permissions missing on child'],
        }, e);
        setConnecting(false);
        setRemoteStream(null);
        setSession(null);
        appAlert('Error', msg);
      } finally {
        setLoading(false);
      }
    },
    [deviceId, needsPinForScreen, pinInput, silentScreenCapable, liveStatus, mediaProjectionGranted, mediaProjectionConsentState],
  );

  useEffect(() => {
    if (session || autoStartDone.current || loading) return;
    const target = initialStream ?? 'SCREEN';
    if (target === 'SCREEN' && needsPinForScreen && !pinInput) return;
    autoStartDone.current = true;
    start(target);
  }, [initialStream, session, loading, needsPinForScreen, pinInput, start]);

  const teardownSession = useCallback(async () => {
    if (!session) return;
    const sessionId = session.sessionId;
    track(TelemetryEvents.STREAM_STOP, {stream_type: streamType});
    try {
      await stopBackendStreamSession(sessionId);
    } catch {
      // Still clear local viewer state so Listening / LIVE UI cannot stick.
    } finally {
      await disconnectViewer().catch(() => undefined);
      setSession(null);
      setRemoteStream(null);
      setConnecting(false);
      setIceState(null);
      resetStreamState();
    }
  }, [session, streamType]);

  const stop = useCallback(async () => {
    if (!session) return;
    if (recording) {
      try {
        await stopStreamRecording(streamType);
      } catch {
        // Ignore stop errors while ending session.
      }
      setRecording(false);
    }
    streamStateTransition('STOPPING', 'stop', {sessionId: session.sessionId});
    setStopping(true);
    try {
      await teardownSession();
      streamStateTransition('STOPPED', 'stop');
    } catch {
      appAlert('Error', 'Couldn’t stop live view. Please try again.');
    } finally {
      setStopping(false);
    }
  }, [session, recording, streamType, teardownSession]);

  const reconnect = useCallback(async () => {
    if (!session) return;
    streamStateTransition('WEBRTC_CONNECTING', 'reconnect', {sessionId: session.sessionId});
    setConnecting(true);
    setIceState(null);
    setRemoteStream(null);
    try {
      await reconnectParentViewer();
    } catch (e: unknown) {
      const msg =
        e instanceof Error ? e.message : 'Check your connection and try again.';
      appAlert('Couldn’t reconnect', msg);
      setConnecting(false);
    }
  }, [session]);

  const switchCamera = useCallback(async () => {
    if (!session) return;
    try {
      await monitoringApi.switchCamera(session.sessionId);
    } catch {
      appAlert('Error', 'Couldn’t switch camera. Please try again.');
    }
  }, [session]);

  const [audioMuted, setAudioMuted] = useState(true);

  const toggleAudioMute = useCallback(() => {
    const next = !audioMuted;
    setAudioMuted(next);
    setRemoteAudioEnabled(!next);
  }, [audioMuted]);

  const toggleRecording = useCallback(async () => {
    if (!recordingSupported) {
      appAlert('Recording unavailable', 'Recording requires Android 10 or newer.');
      return;
    }
    const liveNow =
      !!remoteStream && (iceState === 'connected' || iceState === 'completed');
    if (!liveNow) {
      appAlert('Not live yet', 'Wait until the stream is live before recording.');
      return;
    }
    if (recording) {
      try {
        await stopStreamRecording(streamType);
        setRecording(false);
        showRecordingSavedAlert(streamType, {
          includedRemoteAudio: recordingIncludedAudio,
        });
        setRecordingIncludedAudio(false);
      } catch (e: unknown) {
        const msg =
          e instanceof Error ? e.message : 'Couldn’t save the recording. Please try again.';
        appAlert('Recording', msg);
      }
      return;
    }
    try {
      const videoTrack =
        streamType === 'AUDIO' ? null : getVideoTrackRecordingInfo();
      if (streamType !== 'AUDIO' && !videoTrack) {
        appAlert('Recording', 'Wait for live video before recording.');
        return;
      }
      const includeRemoteAudio =
        streamType !== 'AUDIO' && isRemoteAudioEnabledForRecording();
      await startStreamRecording(streamType, videoTrack, includeRemoteAudio);
      setRecordingIncludedAudio(includeRemoteAudio);
      setRecording(true);
    } catch (e: unknown) {
      const msg =
        e instanceof Error ? e.message : 'Couldn’t start recording. Please try again.';
      appAlert('Recording', msg);
    }
  }, [recordingSupported, remoteStream, iceState, recording, recordingIncludedAudio, streamType]);

  const sendRemoteCommand = useCallback(
    async (
      commandType: 'TAP' | 'SWIPE' | 'GLOBAL_BACK' | 'GLOBAL_HOME' | 'TYPE_TEXT' | 'LAUNCH_APP',
      payload?: Record<string, unknown>,
    ) => {
      if (!accessibilityEnabled) {
        appAlert(
          'Remote control unavailable',
          'Required permissions aren’t finished on the child phone yet. ' +
            'Open Clock Time → Permissions and complete the remaining steps, then try again.',
        );
        return;
      }
      try {
        await deviceApi.sendCommand(deviceId, commandType, payload);
      } catch (e: unknown) {
        const status = (e as {response?: {status?: number}})?.response?.status;
        const msg =
          (e as {response?: {data?: {message?: string}}})?.response?.data?.message ??
          'Couldn’t send the command to the child phone. Check your connection and try again.';
        if (status === 403 && /pin/i.test(msg)) {
          const buttons: AlertButton[] = [
            {text: 'Cancel', style: 'cancel'},
            {
              text: 'Unlock with password',
              onPress: () => navigation.navigate('ParentPin'),
            },
          ];
          appAlert(
            'Password required',
            'Enter your parent password to use remote control.',
            buttons,
          );
          return;
        }
        appAlert('Remote control failed', msg);
      }
    },
    [deviceId, accessibilityEnabled, navigation],
  );

  const toggleRemoteControl = useCallback(() => {
    if (!remoteControlEnabled && !accessibilityEnabled) {
      appAlert(
        'Remote control unavailable',
        'Required permissions aren’t finished on the child phone yet. ' +
          'Open Clock Time → Permissions and complete the remaining steps, then try again.',
      );
      return;
    }
    setRemoteControlEnabled(v => !v);
  }, [remoteControlEnabled, accessibilityEnabled]);

  const handleBack = useCallback(() => {
    if (session) {
      appAlert('Leave live view?', 'Stop live view and go back?', [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Stop & leave',
          style: 'destructive',
          onPress: () => {
            stop().finally(() => navigation.goBack());
          },
        },
      ]);
      return;
    }
    navigation.goBack();
  }, [navigation, session, stop]);

  const videoTrackEnded =
    !!remoteStream &&
    streamType !== 'AUDIO' &&
    remoteStream.getVideoTracks()[0]?.readyState === 'ended';
  const {isLive, isFailed, isPending} = liveSessionUiState({
    iceState,
    hasRemoteStream: !!remoteStream,
    videoTrackEnded,
    connecting,
    hasSession: !!session,
  });
  const statusLabel = sessionStatusLabel(
    streamType,
    connecting,
    iceState,
    isLive,
    session?.captureMode,
  );
  const effectiveAudioMuted = !isLive || audioMuted;

  useEffect(() => {
    if (!isLive && !audioMuted) {
      setAudioMuted(true);
      setRemoteAudioEnabled(false);
    }
  }, [isLive, audioMuted]);

  const guardedToggleAudioMute = useCallback(() => {
    if (!isLive) return;
    toggleAudioMute();
  }, [isLive, toggleAudioMute]);

  // PiP camera during screen share needs a second concurrent session (not yet supported server-side).
  const pipStream: MediaStream | null = null;

  return (
    <View style={styles.root}>
      <StatusBar barStyle="light-content" backgroundColor={STREAM_COLORS.bg} />
      {session ? (
        <LiveStreamViewport
          deviceName={deviceName}
          streamType={streamType}
          statusLabel={statusLabel}
          isLive={isLive}
          isPending={isPending}
          isFailed={isFailed}
          primaryStream={remoteStream}
          pipStream={pipStream}
          pipVisible={pipVisible}
          onBack={handleBack}
          onStop={stop}
          onSwitchCamera={streamType === 'CAMERA' ? switchCamera : undefined}
          audioMuted={streamType !== 'AUDIO' ? effectiveAudioMuted : undefined}
          onToggleAudioMute={streamType !== 'AUDIO' ? guardedToggleAudioMute : undefined}
          onTogglePip={() => setPipVisible(v => !v)}
          remoteControlEnabled={remoteControlEnabled}
          onToggleRemoteControl={toggleRemoteControl}
          onRemoteTap={(xNorm, yNorm) => sendRemoteCommand('TAP', {xNorm, yNorm})}
          onRemoteSwipe={(x1Norm, y1Norm, x2Norm, y2Norm) =>
            sendRemoteCommand('SWIPE', {x1Norm, y1Norm, x2Norm, y2Norm, durationMs: 300})
          }
          onRemoteBack={() => sendRemoteCommand('GLOBAL_BACK')}
          onRemoteHome={() => sendRemoteCommand('GLOBAL_HOME')}
          onRemoteTypeText={() => setRemoteActionModal('type')}
          onRemoteLaunchApp={() => setRemoteActionModal('launch')}
          onReconnect={reconnect}
          stopping={stopping}
          recording={recording}
          recordingSupported={recordingSupported}
          onToggleRecording={toggleRecording}
          topInset={insets.top}
          bottomInset={insets.bottom}
        />
      ) : (
        <PreSessionLauncher
          deviceName={deviceName}
          loading={loading}
          needsPin={needsPinForScreen}
          pinInput={pinInput}
          onPinChange={setPinInput}
          onStart={start}
          onBack={() => navigation.goBack()}
          topInset={insets.top}
          bottomInset={insets.bottom}
          screenShareCapable={screenShareCapable}
          accessibilityEnabled={accessibilityEnabled}
          screenShareBlockedReason={
            accessibilityEnabled
              ? undefined
              : 'Finish the remaining Clock Time permission steps on the child phone first. Until then, the child may be asked to allow screen sharing when you start.'
          }
        />
      )}
      <RemoteControlActionModals
        deviceId={deviceId}
        visible={remoteActionModal}
        onClose={() => setRemoteActionModal(null)}
        onSendTypeText={text => sendRemoteCommand('TYPE_TEXT', {text})}
        onSendLaunchApp={packageName => sendRemoteCommand('LAUNCH_APP', {packageName})}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: STREAM_COLORS.bg,
  },
});
