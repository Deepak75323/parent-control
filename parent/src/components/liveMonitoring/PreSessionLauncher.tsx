import React from 'react';
import {View, Text, StyleSheet, Pressable, ActivityIndicator} from 'react-native';
import {STREAM_COLORS} from './layout';

type Props = {
  deviceName: string;
  loading: boolean;
  /** Set when auto-start / retry failed so user can try again */
  errorMessage?: string | null;
  needsPin: boolean;
  pinInput: string;
  onPinChange: (v: string) => void;
  onStart: (type: 'SCREEN' | 'CAMERA' | 'AUDIO') => void;
  onBack: () => void;
  topInset: number;
  bottomInset: number;
  screenShareCapable: boolean;
  screenShareBlockedReason?: string;
  /** Show child-permission hint only when required permissions aren’t finished */
  accessibilityEnabled?: boolean;
};

/**
 * Camera-only branch: no Screen/Audio chooser.
 * Spinner while connecting; Start/Retry when idle or after failure.
 */
export function PreSessionLauncher({
  deviceName,
  loading,
  errorMessage,
  onStart,
  onBack,
  topInset,
  bottomInset,
}: Props) {
  return (
    <View style={styles.root}>
      <View style={[styles.top, {paddingTop: topInset + 8}]}>
        <Pressable onPress={onBack} hitSlop={12} style={styles.backBtn}>
          <Text style={styles.backIcon}>‹</Text>
        </Pressable>
        <Text style={styles.title} numberOfLines={1}>
          {deviceName}
        </Text>
        <View style={styles.backBtn} />
      </View>

      <View style={styles.center}>
        {loading ? (
          <>
            <ActivityIndicator size="large" color={STREAM_COLORS.accent} />
            <Text style={styles.hint}>Connecting to camera…</Text>
            <Text style={styles.subHint}>Keep Clock Time open on the child phone</Text>
          </>
        ) : errorMessage ? (
          <>
            <Text style={styles.errorTitle}>Couldn’t start camera</Text>
            <Text style={styles.errorBody}>{errorMessage}</Text>
          </>
        ) : (
          <>
            <Text style={styles.hint}>Live Camera</Text>
            <Text style={styles.subHint}>Tap below to view the child phone camera</Text>
          </>
        )}
      </View>

      <View style={[styles.actions, {paddingBottom: bottomInset + 16}]}>
        {!loading ? (
          <Pressable style={styles.retryBtn} onPress={() => onStart('CAMERA')}>
            <Text style={styles.retryLabel}>{errorMessage ? 'Try again' : 'Start camera'}</Text>
          </Pressable>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: STREAM_COLORS.bg,
  },
  top: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingBottom: 8,
  },
  backBtn: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  backIcon: {
    color: STREAM_COLORS.text,
    fontSize: 28,
    lineHeight: 30,
  },
  title: {
    flex: 1,
    textAlign: 'center',
    color: STREAM_COLORS.text,
    fontSize: 17,
    fontWeight: '700',
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 28,
    gap: 12,
  },
  hint: {
    color: STREAM_COLORS.text,
    fontSize: 16,
    fontWeight: '600',
    marginTop: 8,
  },
  subHint: {
    color: STREAM_COLORS.textMuted,
    fontSize: 13,
    textAlign: 'center',
  },
  errorTitle: {
    color: STREAM_COLORS.text,
    fontSize: 17,
    fontWeight: '700',
    textAlign: 'center',
  },
  errorBody: {
    color: STREAM_COLORS.textMuted,
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
  },
  actions: {
    paddingHorizontal: 20,
    gap: 10,
  },
  retryBtn: {
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(56, 189, 248, 0.28)',
    borderRadius: 14,
    paddingVertical: 16,
    borderWidth: 1,
    borderColor: 'rgba(56, 189, 248, 0.55)',
  },
  retryLabel: {
    color: STREAM_COLORS.text,
    fontSize: 16,
    fontWeight: '600',
  },
});
