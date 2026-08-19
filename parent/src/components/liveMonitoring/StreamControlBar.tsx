import React from 'react';
import {View, Text, StyleSheet, Pressable, ActivityIndicator} from 'react-native';
import {STREAM_COLORS} from './layout';

type Props = {
  onStop: () => void;
  onSwitchCamera?: () => void;
  onToggleFullscreen?: () => void;
  onTogglePip?: () => void;
  remoteControlEnabled?: boolean;
  onToggleRemoteControl?: () => void;
  onRemoteBack?: () => void;
  onRemoteHome?: () => void;
  onRemoteTypeText?: () => void;
  onRemoteLaunchApp?: () => void;
  showRemoteControl?: boolean;
  stopping: boolean;
  showSwitchCamera: boolean;
  showPipToggle: boolean;
  pipVisible: boolean;
  isFullscreen: boolean;
  bottomInset: number;
  audioMuted?: boolean;
  onToggleAudioMute?: () => void;
  recording?: boolean;
  recordingSupported?: boolean;
  onToggleRecording?: () => void;
  /** Viewer-only rotation (does not move child camera). */
  showViewRotate?: boolean;
  viewRotateDeg?: number;
  onRotateView?: () => void;
  onResetViewRotate?: () => void;
  /** Viewer-only zoom (pinch or +/- buttons). */
  showViewZoom?: boolean;
  viewZoomScale?: number;
  onZoomIn?: () => void;
  onZoomOut?: () => void;
  onResetViewZoom?: () => void;
};

export function StreamControlBar({
  onStop,
  onSwitchCamera,
  onToggleFullscreen,
  onTogglePip,
  remoteControlEnabled,
  onToggleRemoteControl,
  onRemoteBack,
  onRemoteHome,
  onRemoteTypeText,
  onRemoteLaunchApp,
  showRemoteControl,
  stopping,
  showSwitchCamera,
  showPipToggle,
  pipVisible,
  isFullscreen,
  bottomInset,
  audioMuted,
  onToggleAudioMute,
  recording,
  recordingSupported,
  onToggleRecording,
  showViewRotate,
  viewRotateDeg = 0,
  onRotateView,
  onResetViewRotate,
  showViewZoom,
  viewZoomScale = 1,
  onZoomIn,
  onZoomOut,
  onResetViewZoom,
}: Props) {
  return (
    <View style={[styles.bar, {paddingBottom: bottomInset + 10}]}>
      <View style={styles.row}>
        {showRemoteControl && onToggleRemoteControl ? (
          <Pressable
            style={[styles.secondaryBtn, remoteControlEnabled && styles.remoteActiveBtn]}
            onPress={onToggleRemoteControl}>
            <Text style={styles.secondaryBtnText}>
              {remoteControlEnabled ? 'Control on' : 'Control'}
            </Text>
          </Pressable>
        ) : null}
        {showRemoteControl && remoteControlEnabled && onRemoteBack ? (
          <Pressable style={styles.secondaryBtn} onPress={onRemoteBack}>
            <Text style={styles.secondaryBtnText}>Back</Text>
          </Pressable>
        ) : null}
        {showRemoteControl && remoteControlEnabled && onRemoteHome ? (
          <Pressable style={styles.secondaryBtn} onPress={onRemoteHome}>
            <Text style={styles.secondaryBtnText}>Home</Text>
          </Pressable>
        ) : null}
        {showRemoteControl && remoteControlEnabled && onRemoteTypeText ? (
          <Pressable style={styles.secondaryBtn} onPress={onRemoteTypeText}>
            <Text style={styles.secondaryBtnText}>Type</Text>
          </Pressable>
        ) : null}
        {showRemoteControl && remoteControlEnabled && onRemoteLaunchApp ? (
          <Pressable style={styles.secondaryBtn} onPress={onRemoteLaunchApp}>
            <Text style={styles.secondaryBtnText}>App</Text>
          </Pressable>
        ) : null}
        {showPipToggle && onTogglePip ? (
          <Pressable style={styles.secondaryBtn} onPress={onTogglePip}>
            <Text style={styles.secondaryBtnText}>{pipVisible ? 'Hide cam' : 'Show cam'}</Text>
          </Pressable>
        ) : null}
        {showSwitchCamera && onSwitchCamera ? (
          <Pressable style={styles.secondaryBtn} onPress={onSwitchCamera}>
            <Text style={styles.secondaryBtnText}>Flip</Text>
          </Pressable>
        ) : null}
        {showViewRotate && onRotateView ? (
          <Pressable style={styles.secondaryBtn} onPress={onRotateView}>
            <Text style={styles.secondaryBtnText}>Rotate {viewRotateDeg}°</Text>
          </Pressable>
        ) : null}
        {showViewZoom && onZoomOut ? (
          <Pressable style={styles.secondaryBtn} onPress={onZoomOut}>
            <Text style={styles.secondaryBtnText}>Zoom −</Text>
          </Pressable>
        ) : null}
        {showViewZoom && onZoomIn ? (
          <Pressable style={styles.secondaryBtn} onPress={onZoomIn}>
            <Text style={styles.secondaryBtnText}>
              Zoom +{viewZoomScale > 1 ? ` ${viewZoomScale.toFixed(1)}×` : ''}
            </Text>
          </Pressable>
        ) : null}
        {(showViewRotate && viewRotateDeg !== 0 && onResetViewRotate) ||
        (showViewZoom && viewZoomScale > 1 && onResetViewZoom) ? (
          <Pressable
            style={styles.secondaryBtn}
            onPress={() => {
              onResetViewRotate?.();
              onResetViewZoom?.();
            }}>
            <Text style={styles.secondaryBtnText}>Reset view</Text>
          </Pressable>
        ) : null}
        {onToggleAudioMute ? (
          <Pressable
            style={[styles.secondaryBtn, !audioMuted && styles.audioActiveBtn]}
            onPress={onToggleAudioMute}>
            <Text style={styles.secondaryBtnText}>
              {audioMuted ? '🔇 Muted' : '🔊 Sound'}
            </Text>
          </Pressable>
        ) : null}
        {onToggleFullscreen ? (
          <Pressable style={styles.secondaryBtn} onPress={onToggleFullscreen}>
            <Text style={styles.secondaryBtnText}>{isFullscreen ? 'Exit' : 'Full'}</Text>
          </Pressable>
        ) : null}
        {recordingSupported && onToggleRecording ? (
          <Pressable
            style={[styles.secondaryBtn, recording && styles.recordActiveBtn]}
            onPress={onToggleRecording}
            disabled={stopping}>
            <Text style={styles.secondaryBtnText}>{recording ? '⏹ Save' : '⏺ Rec'}</Text>
          </Pressable>
        ) : null}
        <Pressable
          style={[styles.stopBtn, stopping && styles.stopBtnDisabled]}
          onPress={onStop}
          disabled={stopping}>
          {stopping ? (
            <ActivityIndicator color="#fff" size="small" />
          ) : (
            <Text style={styles.stopBtnText}>Stop</Text>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 20,
    paddingHorizontal: 12,
    paddingTop: 10,
    backgroundColor: STREAM_COLORS.overlay,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    flexWrap: 'wrap',
    gap: 8,
  },
  secondaryBtn: {
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  secondaryBtnText: {
    color: STREAM_COLORS.text,
    fontSize: 13,
    fontWeight: '600',
  },
  remoteActiveBtn: {
    backgroundColor: 'rgba(56, 189, 248, 0.35)',
    borderWidth: 1,
    borderColor: 'rgba(56, 189, 248, 0.6)',
  },
  audioActiveBtn: {
    backgroundColor: 'rgba(34, 197, 94, 0.25)',
    borderWidth: 1,
    borderColor: 'rgba(34, 197, 94, 0.5)',
  },
  recordActiveBtn: {
    backgroundColor: 'rgba(239, 68, 68, 0.3)',
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.55)',
  },
  stopBtn: {
    minWidth: 88,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: STREAM_COLORS.danger,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stopBtnDisabled: {
    opacity: 0.7,
  },
  stopBtnText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
  },
});
