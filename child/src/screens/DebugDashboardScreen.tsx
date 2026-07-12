import React, {useCallback, useEffect, useState} from 'react';
import {BackHandler, ScrollView, Text, StyleSheet, Pressable, View, RefreshControl} from 'react-native';
import {useFocusEffect} from '@react-navigation/native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {nativeModules} from '../nativeModules';
import {getStreamingStatus} from '../services/streamingService';
import {flowLog} from '../services/flowLogger';
import {
  getNavStackSnapshot,
  navTrace,
  navTraceNavCall,
  navTracePostNav,
  navTraceTransition,
} from '../navigation/navTrace';
import {goToMonitoringHome} from '../navigation/navigationRef';
import {logNavToolbarBack, useNavTraceLifecycle} from '../navigation/useNavTraceLifecycle';
import {Colors, Radii} from '../theme/design';

type Props = NativeStackScreenProps<ChildStackParamList, 'DebugDashboard'>;

type Diag = Record<string, string | number | boolean | null | undefined>;

function Row({label, value, ok}: {label: string; value: string; ok?: boolean}) {
  return (
    <View style={s.row}>
      <Text style={s.label}>{label}</Text>
      <Text style={[s.value, ok === true && s.ok, ok === false && s.bad]}>{value}</Text>
    </View>
  );
}

export function DebugDashboardScreen({navigation}: Props) {
  useEffect(() => {
    if (!__DEV__) {
      navigation.replace('ServiceStatus');
    }
  }, [navigation]);

  useNavTraceLifecycle('DebugDashboard', () => ({
    openedVia: 'see_onResume_stack',
    stackOnMount: getNavStackSnapshot().stack,
  }));

  const [diag, setDiag] = useState<Diag>({});
  const [stream, setStream] = useState(getStreamingStatus());
  const [refreshing, setRefreshing] = useState(false);

  const goHome = useCallback(() => {
    const snap = getNavStackSnapshot();
    navTrace('Back to Home button clicked', {
      screen: 'DebugDashboard',
      expectedDestination: 'ServiceStatus',
      currentScreen: 'DebugDashboard',
      stackBefore: snap.stack,
      userInitiated: true,
    });
    logNavToolbarBack('DebugDashboard', 'ServiceStatus');
    navTraceNavCall('reset', 'DebugDashboardScreen.goHome', 'ServiceStatus', 'before', {
      stackBefore: snap.stack,
    });
    goToMonitoringHome();
    navTraceNavCall('reset', 'DebugDashboardScreen.goHome', 'ServiceStatus', 'after', {
      stackAfter: getNavStackSnapshot().stack,
    });
    navTracePostNav('DebugDashboardScreen.goHome');
  }, []);

  useFocusEffect(
    useCallback(() => {
      const snap = getNavStackSnapshot();
      navTrace('Diagnostics screen opened / focused', {
        screen: 'DebugDashboard',
        stack: snap.stack,
        active: snap.active,
        routes: snap.routes,
        userInitiated: snap.routes[snap.routes.length - 1] === 'DebugDashboard',
        autoOpened: false,
        opener: 'check_stack_for_ServiceStatus_before_DebugDashboard',
      });
      const sub = BackHandler.addEventListener('hardwareBackPress', () => {
        navTrace('Hardware back on Diagnostics', {
          screen: 'DebugDashboard',
          expectedDestination: 'ServiceStatus',
        });
        goHome();
        return true;
      });
      return () => sub.remove();
    }, [goHome]),
  );

  const refresh = useCallback(async () => {
    setRefreshing(true);
    flowLog('DIAG', 'debug dashboard refresh');
    try {
      const d = await nativeModules.getStreamDiagnostics();
      setDiag(d as Diag);
      setStream(getStreamingStatus());
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const t = setInterval(() => setStream(getStreamingStatus()), 2000);
    return () => clearInterval(t);
  }, [refresh]);

  const overlayMissing = diag.overlayGranted === false;
  const batteryMissing = diag.batteryOptimizationExempt === false;

  return (
    <SafeAreaView style={s.safe} edges={['top', 'bottom']}>
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <Pressable onPress={goHome} style={s.back} hitSlop={12}>
        <Text style={s.backText}>← Back to home</Text>
      </Pressable>
      <Text style={s.title}>Diagnostics</Text>
      <Text style={s.sub}>
        Read-only debug view. To grant missing permissions, use Grant Permissions below.
      </Text>
      <Text style={s.subMono}>Logcat: <Text style={s.mono}>PCP_FLOW</Text> · Metro: <Text style={s.mono}>[PCP:</Text></Text>

      <Text style={s.section}>Device</Text>
      <Row label="Paired" value={String(diag.isPaired ?? '—')} ok={diag.isPaired === true} />
      <Row label="Manufacturer" value={String(diag.manufacturer ?? '—')} />
      <Row label="Android" value={String(diag.androidVersion ?? '—')} />
      <Row label="SDK" value={String(diag.sdkInt ?? '—')} />

      <Text style={s.section}>Permissions & capture</Text>
      <Row label="Screen consent" value={String(diag.mediaProjectionGranted ?? '—')} ok={diag.mediaProjectionGranted === true} />
      <Row label="Camera" value={String(diag.cameraGranted ?? '—')} ok={diag.cameraGranted === true} />
      <Row label="Mic" value={String(diag.micGranted ?? '—')} ok={diag.micGranted === true} />
      <Row label="Overlay" value={String(diag.overlayGranted ?? '—')} ok={diag.overlayGranted === true} />
      <Row label="Battery exempt" value={String(diag.batteryOptimizationExempt ?? '—')} ok={diag.batteryOptimizationExempt === true} />
      <Row label="App in foreground" value={String(diag.hasActivity ?? '—')} ok={diag.hasActivity === true} />
      <Row label="Device owner" value={String(diag.deviceOwner ?? '—')} />

      <Text style={s.section}>Streaming</Text>
      <Row label="JS listener" value={String(diag.jsListenerActive ?? '—')} />
      <Row label="Native screen running" value={String(diag.nativeScreenRunning ?? '—')} />
      <Row label="Active room" value={stream.activeRoom ?? 'none'} />
      <Row label="ICE state" value={stream.iceState ?? 'none'} ok={stream.iceState === 'connected' || stream.iceState === 'completed'} />
      <Row label="API base" value={String(diag.apiBaseUrl ?? '—')} />

      {(overlayMissing || batteryMissing) ? (
        <View style={s.warnBox}>
          <Text style={s.warnTitle}>Missing permissions</Text>
          <Text style={s.warnBody}>
            Overlay and battery exemption cannot be granted from this screen. Open Grant Permissions to fix them.
          </Text>
          {overlayMissing ? (
            <Pressable
              style={s.warnBtn}
              onPress={() => {
                navTraceTransition('DebugDashboard', 'CheckPermissions', 'overlay missing warn btn', 'DebugDashboardScreen');
                navigation.navigate('CheckPermissions');
              }}>
              <Text style={s.warnBtnText}>Grant overlay permission</Text>
            </Pressable>
          ) : null}
          {batteryMissing ? (
            <Pressable
              style={s.warnBtn}
              onPress={() => {
                navTraceTransition('DebugDashboard', 'CheckPermissions', 'overlay missing warn btn', 'DebugDashboardScreen');
                navigation.navigate('CheckPermissions');
              }}>
              <Text style={s.warnBtnText}>Grant battery exemption</Text>
            </Pressable>
          ) : null}
        </View>
      ) : null}

      <Pressable
        style={s.btn}
        onPress={() => {
          navTraceTransition('DebugDashboard', 'CheckPermissions', 'grant permissions btn', 'DebugDashboardScreen');
          navigation.navigate('CheckPermissions');
        }}>
        <Text style={s.btnText}>Grant permissions</Text>
      </Pressable>
      <Pressable style={[s.btn, s.btnSecondary]} onPress={refresh}>
        <Text style={s.btnText}>Refresh diagnostics</Text>
      </Pressable>
    </ScrollView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  safe: {flex: 1, backgroundColor: Colors.textPrimary},
  root: {flex: 1, backgroundColor: Colors.textPrimary},
  content: {padding: 20, paddingBottom: 40},
  back: {marginBottom: 8, alignSelf: 'flex-start'},
  backText: {color: Colors.primaryLight, fontWeight: '700', fontSize: 16},
  title: {fontSize: 26, fontWeight: '900', color: Colors.textOnDark, marginBottom: 6},
  sub: {fontSize: 13, color: Colors.textTertiary, marginBottom: 8, lineHeight: 18},
  subMono: {fontSize: 12, color: Colors.textTertiary, marginBottom: 20, lineHeight: 18},
  mono: {fontFamily: 'monospace', color: Colors.surfaceElevated},
  section: {fontSize: 12, fontWeight: '800', color: Colors.textTertiary, marginTop: 16, marginBottom: 8, letterSpacing: 0.5},
  row: {flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: Colors.textSecondary},
  label: {color: Colors.textTertiary, fontSize: 14, flex: 1},
  value: {color: Colors.surfaceElevated, fontSize: 14, fontWeight: '600', maxWidth: '55%', textAlign: 'right'},
  ok: {color: Colors.success},
  bad: {color: Colors.danger},
  warnBox: {
    marginTop: 16,
    backgroundColor: Colors.textSecondary,
    borderRadius: Radii.md,
    padding: 14,
    borderWidth: 1,
    borderColor: Colors.danger,
  },
  warnTitle: {color: Colors.dangerLight, fontWeight: '800', fontSize: 14, marginBottom: 6},
  warnBody: {color: Colors.surfaceElevated, fontSize: 13, lineHeight: 18, marginBottom: 10},
  warnBtn: {
    marginTop: 8,
    backgroundColor: Colors.accent,
    borderRadius: Radii.md,
    paddingVertical: 12,
    alignItems: 'center',
  },
  warnBtnText: {color: Colors.textOnDark, fontWeight: '700', fontSize: 14},
  btn: {marginTop: 16, backgroundColor: Colors.primaryContainer, borderRadius: Radii.md, padding: 16, alignItems: 'center'},
  btnSecondary: {backgroundColor: Colors.outline, marginTop: 10},
  btnText: {color: Colors.textOnDark, fontWeight: '800', fontSize: 15},
});
