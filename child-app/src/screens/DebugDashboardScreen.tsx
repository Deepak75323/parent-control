import React, {useCallback, useEffect, useState} from 'react';
import {ScrollView, Text, StyleSheet, Pressable, View, RefreshControl} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {nativeModules} from '../nativeModules';
import {getStreamingStatus} from '../services/streamingService';
import {flowLog} from '../services/flowLogger';

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
  const [diag, setDiag] = useState<Diag>({});
  const [stream, setStream] = useState(getStreamingStatus());
  const [refreshing, setRefreshing] = useState(false);

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

  return (
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <Pressable onPress={() => navigation.goBack()} style={s.back}>
        <Text style={s.backText}>← Back</Text>
      </Pressable>
      <Text style={s.title}>Diagnostics</Text>
      <Text style={s.sub}>Filter Logcat with tag <Text style={s.mono}>PCP_FLOW</Text> or Metro with <Text style={s.mono}>[PCP:</Text></Text>

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

      <Pressable style={s.btn} onPress={refresh}>
        <Text style={s.btnText}>Refresh diagnostics</Text>
      </Pressable>
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#0f172a'},
  content: {padding: 20, paddingBottom: 40},
  back: {marginBottom: 8},
  backText: {color: '#818CF8', fontWeight: '700', fontSize: 16},
  title: {fontSize: 26, fontWeight: '900', color: '#f8fafc', marginBottom: 6},
  sub: {fontSize: 13, color: '#94a3b8', marginBottom: 20, lineHeight: 18},
  mono: {fontFamily: 'monospace', color: '#cbd5e1'},
  section: {fontSize: 12, fontWeight: '800', color: '#64748b', marginTop: 16, marginBottom: 8, letterSpacing: 0.5},
  row: {flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#1e293b'},
  label: {color: '#94a3b8', fontSize: 14, flex: 1},
  value: {color: '#e2e8f0', fontSize: 14, fontWeight: '600', maxWidth: '55%', textAlign: 'right'},
  ok: {color: '#34d399'},
  bad: {color: '#f87171'},
  btn: {marginTop: 24, backgroundColor: '#4F46E5', borderRadius: 14, padding: 16, alignItems: 'center'},
  btnText: {color: '#fff', fontWeight: '800', fontSize: 15},
});
