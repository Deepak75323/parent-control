import React, {useCallback, useEffect, useRef, useState} from 'react';
import {Alert, AlertButton, StatusBar, StyleSheet, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {MediaStream} from 'react-native-webrtc';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {RootStackParamList} from '../navigation/RootNavigator';
import {deviceApi, monitoringApi, parentPinApi, ScreenShareSession} from '../services/api';
import {
  connectParentViewer,
  disconnectViewer,
  reconnectParentViewer,
  registerStreamListeners,
} from '../services/webrtcService';
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
import {
  isScreenShareCapable,
  isSilentScreenCapable,
  mergeScreenShareCapability,
  screenShareBlockedMessage,
} from '../utils/screenShareCapability';

type Props = NativeStackScreenProps<RootStackParamList, 'LiveMonitoring'>;
type StreamType = 'SCREEN' | 'CAMERA' | 'AUDIO';

function sessionStatusLabel(
  streamType: StreamType,
  connecting: boolean,
  iceState: string | null,
  hasStream: boolean,
  session: ScreenShareSession | null,
): string {
  if (hasStream) return 'Live';
  if (connecting) {
    if (streamType === 'AUDIO') return 'Connecting mic…';
    if (streamType === 'CAMERA') return 'Connecting camera…';
    if (streamType === 'SCREEN' && session?.captureMode === 'RN_DISPLAY_MEDIA') {
      return 'Starting screen capture…';
    }
    return 'Connecting…';
  }
  if (iceState === 'failed' || iceState === 'disconnected') return 'Connection failed';
  if (streamType === 'AUDIO') return 'Waiting for audio…';
  if (streamType === 'CAMERA') return 'Waiting for camera…';
  if (streamType === 'SCREEN') return 'Waiting for screen…';
  return 'Waiting…';
}

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
      slog('UI', 'LiveMonitoringScreen unmount — disconnect viewer');
      disconnectViewer();
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

  useEffect(() => {
    if (streamType !== 'SCREEN' || mediaProjectionGranted) return;
    setConnecting(false);
    setLoading(false);
  }, [streamType, mediaProjectionGranted]);

  useEffect(() => {
    if (remoteStream) setConnecting(false);
    if (iceState === 'failed' || iceState === 'disconnected') setConnecting(false);
  }, [remoteStream, iceState]);

  useEffect(() => {
    if (!streamingState || streamingState.deviceId !== deviceId) return;
    if (streamingState.status !== 'FAILED') return;
    setConnecting(false);
    setRemoteStream(null);
    Alert.alert('Stream could not start', streamFailureMessage(streamingState.failureReason));
  }, [streamingState, deviceId]);

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
      if (type === 'SCREEN' && !screenShareCapable) {
        traceFailure({
          component: 'Screen',
          fn: 'start',
          reason: 'Screen share not capable',
          actual: screenShareBlockedReason,
        });
        Alert.alert('Screen capture unavailable', screenShareBlockedReason ?? 'Screen capture is unavailable.');
        return;
      }
      if (type === 'SCREEN' && needsPinForScreen && !pinInput) {
        traceLog('Parent', 'start', 'Parent PIN required for silent screen');
        Alert.alert('Parent PIN required', 'Enter your PIN to start silent screen capture.');
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
        if (type === 'SCREEN' && !isScreenShareCapable(device)) {
          traceFailure({
            component: 'Screen',
            fn: 'start',
            reason: 'Device not screen-share capable after refresh',
          });
          Alert.alert('Screen capture unavailable', screenShareBlockedMessage(device));
          return;
        }
        const lastSeenAt = device.lastSeenAt;
        const staleMs = lastSeenAt ? Date.now() - Date.parse(lastSeenAt) : Number.POSITIVE_INFINITY;
        if (staleMs > 3 * 60 * 1000) {
          traceFailure({
            component: 'Parent',
            fn: 'start',
            reason: 'Child device offline',
            actual: `staleMs=${staleMs}`,
            nextCauses: ['Child app not running', 'Network disconnected', 'Wrong device selected'],
          });
          Alert.alert(
            'Child device offline',
            'This phone has not checked in recently. Open Device Monitor on the child phone, keep it on the same Wi‑Fi as your Mac (or plug in USB), then try again.\n\nIf you re-paired recently, use the newer device in the list — not an old duplicate.',
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
        if (s.captureMode === 'NATIVE') setPinUnlocked(true);
        setTraceState('WEBRTC_CONNECT');
        traceLog('WebRTC', 'connectParentViewer', 'Connecting parent viewer', {
          roomId: s.signalingRoomId,
          sessionId: s.sessionId,
        });
        await connectParentViewer(s.signalingRoomId, s.sessionId);
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
          (e instanceof Error ? e.message : 'Could not start stream session');
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
        Alert.alert('Error', msg);
      } finally {
        setLoading(false);
      }
    },
    [deviceId, needsPinForScreen, pinInput, silentScreenCapable, screenShareCapable, liveStatus, screenShareBlockedReason, mediaProjectionGranted, mediaProjectionConsentState],
  );

  useEffect(() => {
    if (!initialStream || session || autoStartDone.current || loading) return;
    if (initialStream === 'SCREEN' && !screenShareCapable) return;
    if (initialStream === 'SCREEN' && needsPinForScreen && !pinInput) return;
    autoStartDone.current = true;
    start(initialStream);
  }, [initialStream, session, loading, needsPinForScreen, pinInput, start, screenShareCapable]);

  const stop = useCallback(async () => {
    if (!session) return;
    streamStateTransition('STOPPING', 'stop', {sessionId: session.sessionId});
    setStopping(true);
    try {
      await monitoringApi.stopScreenShare(session.sessionId);
      await disconnectViewer();
      setSession(null);
      setRemoteStream(null);
      setConnecting(false);
      streamStateTransition('STOPPED', 'stop');
      resetStreamState();
    } catch {
      Alert.alert('Error', 'Could not stop session');
    } finally {
      setStopping(false);
    }
  }, [session]);

  const reconnect = useCallback(async () => {
    if (!session) return;
    streamStateTransition('WEBRTC_CONNECTING', 'reconnect', {sessionId: session.sessionId});
    setConnecting(true);
    setIceState(null);
    setRemoteStream(null);
    try {
      await reconnectParentViewer();
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Could not reconnect';
      Alert.alert('Reconnect failed', msg);
      setConnecting(false);
    }
  }, [session]);

  const switchCamera = useCallback(async () => {
    if (!session) return;
    try {
      await monitoringApi.switchCamera(session.sessionId);
    } catch {
      Alert.alert('Error', 'Could not switch camera');
    }
  }, [session]);

  const sendRemoteCommand = useCallback(
    async (
      commandType: 'TAP' | 'SWIPE' | 'GLOBAL_BACK' | 'GLOBAL_HOME' | 'TYPE_TEXT' | 'LAUNCH_APP',
      payload?: Record<string, unknown>,
    ) => {
      if (!accessibilityEnabled) {
        Alert.alert(
          'Remote control unavailable',
          'Accessibility is turned off on the child phone. ' +
            'Open the child app → Permissions → turn on Accessibility, then try again.',
        );
        return;
      }
      try {
        await deviceApi.sendCommand(deviceId, commandType, payload);
      } catch (e: unknown) {
        const status = (e as {response?: {status?: number}})?.response?.status;
        const msg =
          (e as {response?: {data?: {message?: string}}})?.response?.data?.message ??
          'Could not send command to child device.';
        if (status === 403 && /pin/i.test(msg)) {
          const buttons: AlertButton[] = [
            {text: 'Cancel', style: 'cancel'},
            {
              text: 'Unlock with PIN',
              onPress: () => navigation.navigate('ParentPin'),
            },
          ];
          Alert.alert('Parent PIN required', msg, buttons);
          return;
        }
        Alert.alert('Remote control failed', msg);
      }
    },
    [deviceId, accessibilityEnabled, navigation],
  );

  const toggleRemoteControl = useCallback(() => {
    if (!remoteControlEnabled && !accessibilityEnabled) {
      Alert.alert(
        'Remote control unavailable',
        'Accessibility is turned off on the child phone. ' +
          'Open the child app → Permissions → turn on Accessibility to tap and swipe on the child screen.',
      );
      return;
    }
    setRemoteControlEnabled(v => !v);
  }, [remoteControlEnabled, accessibilityEnabled]);

  const handleBack = useCallback(() => {
    if (session) {
      Alert.alert('Leave monitoring?', 'Stop the live session and go back?', [
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

  const isLive =
    !!remoteStream &&
    (iceState === 'connected' || iceState === 'completed');
  const isFailed =
    iceState === 'failed' ||
    iceState === 'disconnected' ||
    (remoteStream &&
      streamType !== 'AUDIO' &&
      remoteStream.getVideoTracks()[0]?.readyState === 'ended');
  const isPending = !isLive && !isFailed && (connecting || !!session);
  const statusLabel = sessionStatusLabel(streamType, connecting, iceState, isLive, session);

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
          screenShareBlockedReason={screenShareBlockedReason}
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
