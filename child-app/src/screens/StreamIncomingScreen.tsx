import React, {useEffect, useState} from 'react';
import {View, Text, StyleSheet, ActivityIndicator, Pressable} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {
  getPendingStreamRequest,
  subscribePendingStream,
  type IncomingStreamRequest,
} from '../services/streamIncomingStore';
import {getStreamingStatus} from '../services/streamingService';
import {flowLog} from '../services/flowLogger';

type Props = NativeStackScreenProps<ChildStackParamList, 'StreamIncoming'>;

function streamLabel(type: string): string {
  if (type === 'AUDIO') return 'listen to your microphone';
  if (type === 'CAMERA') return 'view your camera';
  return 'view your screen';
}

export function StreamIncomingScreen({navigation}: Props) {
  const [pending, setPending] = useState<IncomingStreamRequest | null>(getPendingStreamRequest());
  const [status, setStatus] = useState('Waiting for your approval…');

  useEffect(() => {
    flowLog('UI', 'StreamIncomingScreen mounted', {sessionId: pending?.sessionId});
    return subscribePendingStream(setPending);
  }, []);

  useEffect(() => {
    const interval = setInterval(() => {
      const {activeRoom, iceState} = getStreamingStatus();
      if (activeRoom && (iceState === 'connected' || iceState === 'completed')) {
        setStatus('Connected — returning to home…');
        setTimeout(() => navigation.replace('ServiceStatus'), 1200);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [navigation]);

  useEffect(() => {
    if (!pending) {
      navigation.replace('ServiceStatus');
    }
  }, [pending, navigation]);

  if (!pending) {
    return (
      <View style={s.center}>
        <ActivityIndicator size="large" color="#4F46E5" />
      </View>
    );
  }

  return (
    <View style={s.root}>
      <View style={s.card}>
        <Text style={s.emoji}>👀</Text>
        <Text style={s.title}>Live view requested</Text>
        <Text style={s.sub}>
          Your parent wants to {streamLabel(pending.streamType)}.
          {pending.streamType === 'SCREEN'
            ? ' If screen capture was not granted during setup, Android may ask you to approve — tap Start now or Allow.'
            : ' Starting live view…'}
        </Text>
        <View style={s.statusBox}>
          <ActivityIndicator color="#4F46E5" />
          <Text style={s.status}>{status}</Text>
        </View>
        <Text style={s.hint}>Session: {pending.sessionId.slice(0, 8)}…</Text>
      </View>
      <Pressable style={s.backBtn} onPress={() => navigation.replace('ServiceStatus')}>
        <Text style={s.backText}>← Back to home</Text>
      </Pressable>
    </View>
  );
}

const s = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#0F172A', padding: 24, justifyContent: 'center'},
  center: {flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#0F172A'},
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 24,
    padding: 28,
    borderWidth: 1,
    borderColor: '#334155',
  },
  emoji: {fontSize: 48, textAlign: 'center', marginBottom: 12},
  title: {fontSize: 24, fontWeight: '900', color: '#F8FAFC', textAlign: 'center', marginBottom: 12},
  sub: {fontSize: 15, color: '#94A3B8', lineHeight: 22, textAlign: 'center', marginBottom: 20},
  bold: {fontWeight: '800', color: '#E2E8F0'},
  statusBox: {flexDirection: 'row', alignItems: 'center', gap: 12, justifyContent: 'center', marginBottom: 12},
  status: {color: '#CBD5E1', fontSize: 14, fontWeight: '600'},
  hint: {fontSize: 11, color: '#64748B', textAlign: 'center'},
  backBtn: {marginTop: 24, alignItems: 'center', padding: 12},
  backText: {color: '#818CF8', fontWeight: '700', fontSize: 15},
});
