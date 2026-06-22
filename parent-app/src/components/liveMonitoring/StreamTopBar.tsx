import React from 'react';
import {View, Text, StyleSheet, Pressable} from 'react-native';
import {STREAM_COLORS} from './layout';

type Props = {
  deviceName: string;
  statusLabel: string;
  isLive: boolean;
  isPending: boolean;
  isFailed: boolean;
  streamLabel: string;
  onBack: () => void;
  topInset: number;
};

export function StreamTopBar({
  deviceName,
  statusLabel,
  isLive,
  isPending,
  isFailed,
  streamLabel,
  onBack,
  topInset,
}: Props) {
  const dotColor = isLive
    ? STREAM_COLORS.live
    : isFailed
      ? STREAM_COLORS.failed
      : STREAM_COLORS.pending;

  return (
    <View style={[styles.bar, {paddingTop: topInset + 6}]}>
      <Pressable style={styles.backBtn} onPress={onBack} hitSlop={12}>
        <Text style={styles.backIcon}>‹</Text>
      </Pressable>
      <View style={styles.center}>
        <Text style={styles.deviceName} numberOfLines={1}>
          {deviceName}
        </Text>
        <View style={styles.statusRow}>
          <View style={[styles.dot, {backgroundColor: dotColor}]} />
          <Text style={styles.statusText} numberOfLines={1}>
            {statusLabel}
          </Text>
          <Text style={styles.streamType}>· {streamLabel}</Text>
        </View>
      </View>
      <View style={styles.backBtn} />
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 20,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingBottom: 8,
    backgroundColor: STREAM_COLORS.overlayLight,
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
    fontWeight: '300',
  },
  center: {
    flex: 1,
    alignItems: 'center',
    paddingHorizontal: 4,
  },
  deviceName: {
    color: STREAM_COLORS.text,
    fontSize: 15,
    fontWeight: '700',
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 2,
    maxWidth: '100%',
  },
  dot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    marginRight: 6,
  },
  statusText: {
    color: STREAM_COLORS.textMuted,
    fontSize: 12,
    flexShrink: 1,
  },
  streamType: {
    color: STREAM_COLORS.textMuted,
    fontSize: 12,
  },
});
