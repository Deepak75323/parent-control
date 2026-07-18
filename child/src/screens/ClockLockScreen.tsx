import React, {useEffect, useMemo, useState} from 'react';
import {Pressable, StatusBar, StyleSheet, Text, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {SafeAreaView} from 'react-native-safe-area-context';
import {ChildStackParamList} from './RootNavigator';
import {useNavTraceLifecycle} from '../navigation/useNavTraceLifecycle';
import {navTraceNavCall, navTraceTransition} from '../navigation/navTrace';
import {restoreDeviceCredentials} from '../services/deviceStorage';

type Props = NativeStackScreenProps<ChildStackParamList, 'ClockLock'>;

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/**
 * Quiet lock face — always the app entry (paired or not).
 * Open parent-password entry via the invisible hit target in the top-right corner.
 */
export function ClockLockScreen({navigation, route}: Props) {
  useNavTraceLifecycle('ClockLock', () => ({
    returnTo: route.params?.returnTo ?? null,
    revokedByParent: route.params?.revokedByParent ?? false,
  }));

  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  const timeLabel = useMemo(
    () => `${pad2(now.getHours())}:${pad2(now.getMinutes())}`,
    [now],
  );
  const dateLabel = useMemo(
    () =>
      now.toLocaleDateString(undefined, {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
      }),
    [now],
  );

  const openPassword = () => {
    navTraceTransition('ClockLock', 'ChildLogin', 'hidden unlock corner', 'ClockLockScreen');
    navTraceNavCall('navigate', 'ClockLockScreen', 'ChildLogin', 'before');
    void (async () => {
      const token = await restoreDeviceCredentials();
      // Only force ServiceStatus return when already paired; unpaired login starts setup.
      navigation.navigate('ChildLogin', {
        ...(token
          ? {returnTo: route.params?.returnTo ?? 'ServiceStatus'}
          : {}),
        revokedByParent: route.params?.revokedByParent,
      });
      navTraceNavCall('navigate', 'ClockLockScreen', 'ChildLogin', 'after');
    })();
  };

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="light-content" backgroundColor="#0B1220" />
      <View style={styles.glow} />
      {/* Invisible unlock — parent taps top-right corner to enter password */}
      <Pressable
        accessibilityLabel="Parent unlock"
        onPress={openPassword}
        style={styles.hiddenHit}
        hitSlop={8}
      />
      <View style={styles.center}>
        <Text style={styles.time}>{timeLabel}</Text>
        <Text style={styles.date}>{dateLabel}</Text>
        {route.params?.revokedByParent ? (
          <Text style={styles.revoked}>Monitoring was revoked by parent</Text>
        ) : (
          <Text style={styles.hint}>Clock Time</Text>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#0B1220',
  },
  glow: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#1E3A5F',
    opacity: 0.35,
  },
  hiddenHit: {
    position: 'absolute',
    top: 8,
    right: 8,
    width: 56,
    height: 56,
    zIndex: 10,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  time: {
    color: '#F8FAFC',
    fontSize: 72,
    fontWeight: '200',
    letterSpacing: 2,
    fontVariant: ['tabular-nums'],
  },
  date: {
    marginTop: 12,
    color: 'rgba(248,250,252,0.72)',
    fontSize: 18,
    fontWeight: '500',
  },
  hint: {
    marginTop: 28,
    color: 'rgba(248,250,252,0.4)',
    fontSize: 13,
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  revoked: {
    marginTop: 28,
    color: '#FCA5A5',
    fontSize: 14,
    textAlign: 'center',
  },
});
