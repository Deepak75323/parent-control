import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {View, Text, FlatList, StyleSheet, RefreshControl, Alert} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {alertsApi, AlertItem} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {EmptyState, HeroBlock, ScreenShell} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'Alerts'>;

function severityStyle(severity: string) {
  const s = severity.toUpperCase();
  if (s === 'CRITICAL' || s === 'HIGH') return styles.severityHigh;
  if (s === 'MEDIUM' || s === 'WARNING') return styles.severityMed;
  return styles.severityLow;
}

export function AlertsScreen({navigation}: Props) {
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const liveAlerts = useRealtimeStore(s => s.liveAlerts);

  const load = useCallback(async () => {
    try {
      const {data} = await alertsApi.list();
      setAlerts(data.data.content);
    } catch {
      Alert.alert('Error', 'Could not load alerts');
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const merged = useMemo(() => {
    const map = new Map<string, AlertItem>();
    [...liveAlerts, ...alerts].forEach(a => map.set(a.id, a));
    return Array.from(map.values()).sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }, [alerts, liveAlerts]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  return (
    <ScreenShell backLabel="← Back" onBack={() => navigation.goBack()} scroll={false}>
      <View style={styles.header}>
        <HeroBlock
          eyebrow="Safety"
          title="Alerts"
          subtitle="Important events from your children's devices."
        />
      </View>
      <FlatList
        style={styles.list}
        contentContainerStyle={merged.length === 0 ? styles.listEmpty : styles.listContent}
        data={merged}
        keyExtractor={item => item.id}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        renderItem={({item}) => (
          <View style={styles.card}>
            <Text style={[styles.severity, severityStyle(item.severity)]}>{item.severity}</Text>
            <Text style={styles.cardTitle}>{item.title}</Text>
            {item.message ? <Text style={styles.body}>{item.message}</Text> : null}
            <Text style={styles.meta}>
              {item.alertType} · {new Date(item.createdAt).toLocaleString()}
            </Text>
          </View>
        )}
        ListEmptyComponent={
          <EmptyState
            icon="🔔"
            title="No alerts yet"
            body="When something needs your attention, it will show up here."
          />
        }
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  header: {paddingHorizontal: 16, paddingTop: 8},
  list: {flex: 1},
  listContent: {paddingHorizontal: 16, paddingBottom: 24},
  listEmpty: {flexGrow: 1, paddingHorizontal: 16, paddingBottom: 24},
  card: {
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  severity: {fontSize: 11, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.4},
  severityHigh: {color: '#dc2626'},
  severityMed: {color: '#ea580c'},
  severityLow: {color: '#2563eb'},
  cardTitle: {fontSize: 16, fontWeight: '600', marginTop: 4, color: '#0f172a'},
  body: {marginTop: 4, color: '#475569', lineHeight: 20},
  meta: {marginTop: 8, fontSize: 12, color: '#94a3b8'},
});
