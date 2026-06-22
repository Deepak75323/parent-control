import React from 'react';
import {View, Text, StyleSheet, Pressable, ActivityIndicator, TextInput} from 'react-native';
import {STREAM_COLORS} from './layout';

type Props = {
  deviceName: string;
  loading: boolean;
  needsPin: boolean;
  pinInput: string;
  onPinChange: (v: string) => void;
  onStart: (type: 'SCREEN' | 'CAMERA' | 'AUDIO') => void;
  onBack: () => void;
  topInset: number;
  bottomInset: number;
  screenShareCapable: boolean;
  screenShareBlockedReason?: string;
};

export function PreSessionLauncher({
  deviceName,
  loading,
  needsPin,
  pinInput,
  onPinChange,
  onStart,
  onBack,
  topInset,
  bottomInset,
  screenShareCapable,
  screenShareBlockedReason,
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
        <Text style={styles.hint}>Choose what to monitor</Text>
        {!screenShareCapable && screenShareBlockedReason ? (
          <Text style={styles.blockedHint}>{screenShareBlockedReason}</Text>
        ) : null}
        {needsPin ? (
          <View style={styles.pinRow}>
            <TextInput
              style={styles.pinInput}
              value={pinInput}
              onChangeText={onPinChange}
              placeholder="Parent PIN"
              placeholderTextColor={STREAM_COLORS.textMuted}
              secureTextEntry
              keyboardType="number-pad"
              maxLength={8}
            />
          </View>
        ) : null}
      </View>

      <View style={[styles.actions, {paddingBottom: bottomInset + 16}]}>
        <LauncherBtn
          icon="📺"
          label="Screen"
          onPress={() => onStart('SCREEN')}
          disabled={loading || !screenShareCapable}
          subtitle={!screenShareCapable ? 'Needs setup on child phone' : undefined}
        />
        <LauncherBtn
          icon="📷"
          label="Camera"
          onPress={() => onStart('CAMERA')}
          disabled={loading}
        />
        <LauncherBtn
          icon="🎙️"
          label="Audio"
          onPress={() => onStart('AUDIO')}
          disabled={loading}
        />
        {loading ? <ActivityIndicator color={STREAM_COLORS.accent} style={{marginTop: 12}} /> : null}
      </View>
    </View>
  );
}

function LauncherBtn({
  icon,
  label,
  onPress,
  disabled,
  subtitle,
}: {
  icon: string;
  label: string;
  onPress: () => void;
  disabled: boolean;
  subtitle?: string;
}) {
  return (
    <Pressable
      style={[styles.launchBtn, disabled && styles.launchBtnDisabled]}
      onPress={onPress}
      disabled={disabled}>
      <Text style={styles.launchIcon}>{icon}</Text>
      <View style={styles.launchTextCol}>
        <Text style={[styles.launchLabel, disabled && styles.launchLabelDisabled]}>{label}</Text>
        {subtitle ? <Text style={styles.launchSubtitle}>{subtitle}</Text> : null}
      </View>
    </Pressable>
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
    paddingHorizontal: 24,
  },
  hint: {
    color: STREAM_COLORS.textMuted,
    fontSize: 15,
  },
  blockedHint: {
    marginTop: 12,
    color: '#FCA5A5',
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
  },
  pinRow: {
    marginTop: 16,
    width: '100%',
    maxWidth: 220,
  },
  pinInput: {
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: STREAM_COLORS.text,
    fontSize: 16,
    textAlign: 'center',
  },
  actions: {
    paddingHorizontal: 20,
    gap: 10,
  },
  launchBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderRadius: 14,
    paddingVertical: 16,
    paddingHorizontal: 16,
  },
  launchBtnDisabled: {
    opacity: 0.5,
  },
  launchIcon: {
    fontSize: 22,
  },
  launchLabel: {
    color: STREAM_COLORS.text,
    fontSize: 16,
    fontWeight: '600',
  },
  launchLabelDisabled: {
    color: STREAM_COLORS.textMuted,
  },
  launchTextCol: {
    alignItems: 'flex-start',
  },
  launchSubtitle: {
    color: STREAM_COLORS.textMuted,
    fontSize: 12,
    marginTop: 2,
  },
});
