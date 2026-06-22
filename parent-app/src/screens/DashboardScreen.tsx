import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  RefreshControl,
  Pressable,
  ActivityIndicator,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {SafeAreaView} from 'react-native-safe-area-context';
import {RootStackParamList} from '../navigation/RootNavigator';
import {deviceApi, DeviceSummary} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {useAuthStore} from '../store/authStore';

type Props = NativeStackScreenProps<RootStackParamList, 'Dashboard'>;

function isOnline(status?: string): boolean {
  return (status ?? '').toUpperCase() === 'ONLINE';
}

function DeviceCard({
  device,
  liveStatus,
  liveBattery,
  onPress,
}: {
  device: DeviceSummary;
  liveStatus?: string;
  liveBattery?: number;
  onPress: () => void;
}) {
  const status = liveStatus ?? device.status;
  const online = isOnline(status);
  const battery = liveBattery ?? device.batteryLevel;

  return (
    <Pressable
      style={({pressed}) => [styles.deviceCard, pressed && styles.deviceCardPressed]}
      onPress={onPress}>
      <View style={[styles.statusDot, online ? styles.statusOnline : styles.statusOffline]} />
      <View style={styles.deviceInfo}>
        <Text style={styles.deviceName} numberOfLines={1}>
          {device.deviceName}
        </Text>
        <Text style={styles.deviceMeta}>
          {online ? 'Online' : status ?? 'Offline'}
          {battery != null ? ` · ${battery}% battery` : ''}
        </Text>
      </View>
      <Text style={styles.chevron}>›</Text>
    </Pressable>
  );
}

function QuickAction({
  icon,
  label,
  onPress,
  accent,
}: {
  icon: string;
  label: string;
  onPress: () => void;
  accent?: boolean;
}) {
  return (
    <Pressable
      style={({pressed}) => [
        styles.quickAction,
        accent && styles.quickActionAccent,
        pressed && styles.quickActionPressed,
      ]}
      onPress={onPress}>
      <Text style={styles.quickIcon}>{icon}</Text>
      <Text style={[styles.quickLabel, accent && styles.quickLabelAccent]}>{label}</Text>
    </Pressable>
  );
}

export function DashboardScreen({navigation}: Props) {
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [loading, setLoading] = useState(true);
  const email = useAuthStore(s => s.email);
  const wsConnected = useRealtimeStore(s => s.wsConnected);
  const deviceStatuses = useRealtimeStore(s => s.deviceStatuses);
  const liveAlerts = useRealtimeStore(s => s.liveAlerts);
  const mergeDevices = useRealtimeStore(s => s.mergeDevices);
  const deviceListVersion = useRealtimeStore(s => s.deviceListVersion);

  const load = useCallback(async () => {
    const {data} = await deviceApi.list();
    const list = data.data ?? [];
    setDevices(list);
    mergeDevices(list);
  }, [mergeDevices]);

  useEffect(() => {
    load()
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, [load, deviceListVersion]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load().catch(() => undefined);
    setRefreshing(false);
  };

  const onlineCount = useMemo(
    () =>
      devices.filter(d => {
        const live = deviceStatuses[d.id];
        return isOnline(live?.status ?? d.status);
      }).length,
    [devices, deviceStatuses],
  );

  const greeting = email ? email.split('@')[0] : 'Parent';

  return (
    <SafeAreaView style={styles.root} edges={['bottom']}>
      <FlatList
        data={devices}
        keyExtractor={d => d.id}
        contentContainerStyle={styles.listContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        showsVerticalScrollIndicator={false}
        ListHeaderComponent={
          <>
            <View style={styles.hero}>
              <Text style={styles.heroEyebrow}>Parent dashboard</Text>
              <Text style={styles.heroTitle}>Hi, {greeting}</Text>
              <View style={styles.connectionRow}>
                <View
                  style={[
                    styles.connectionDot,
                    {backgroundColor: wsConnected ? '#22c55e' : '#f59e0b'},
                  ]}
                />
                <Text style={styles.connectionText}>
                  {wsConnected ? 'Live updates connected' : 'Connecting to live updates…'}
                </Text>
              </View>
            </View>

            <View style={styles.statsRow}>
              <View style={styles.statCard}>
                <Text style={styles.statValue}>{devices.length}</Text>
                <Text style={styles.statLabel}>Devices</Text>
              </View>
              <View style={styles.statCard}>
                <Text style={[styles.statValue, styles.statValueGreen]}>{onlineCount}</Text>
                <Text style={styles.statLabel}>Online</Text>
              </View>
              <View style={styles.statCard}>
                <Text style={[styles.statValue, liveAlerts.length > 0 && styles.statValueAlert]}>
                  {liveAlerts.length}
                </Text>
                <Text style={styles.statLabel}>Alerts</Text>
              </View>
            </View>

            {liveAlerts.length > 0 ? (
              <Pressable
                style={styles.alertBanner}
                onPress={() => navigation.navigate('Alerts')}>
                <Text style={styles.alertIcon}>⚠️</Text>
                <View style={styles.alertText}>
                  <Text style={styles.alertTitle}>Latest alert</Text>
                  <Text style={styles.alertBody} numberOfLines={1}>
                    {liveAlerts[0].title}
                  </Text>
                </View>
                <Text style={styles.chevron}>›</Text>
              </Pressable>
            ) : null}

            <View style={styles.sectionHeader}>
              <Text style={styles.sectionTitle}>Your devices</Text>
              <Pressable onPress={() => navigation.navigate('DeviceList')}>
                <Text style={styles.sectionLink}>Manage</Text>
              </Pressable>
            </View>
          </>
        }
        renderItem={({item}) => {
          const live = deviceStatuses[item.id];
          return (
            <DeviceCard
              device={item}
              liveStatus={live?.status}
              liveBattery={live?.batteryLevel}
              onPress={() => navigation.navigate('DeviceDetail', {deviceId: item.id})}
            />
          );
        }}
        ListEmptyComponent={
          loading ? (
            <ActivityIndicator style={styles.loader} color="#2563eb" />
          ) : (
            <View style={styles.emptyCard}>
              <Text style={styles.emptyIcon}>📱</Text>
              <Text style={styles.emptyTitle}>No devices yet</Text>
              <Text style={styles.emptyBody}>
                Generate a pairing code and enter it on your child&apos;s phone.
              </Text>
              <Pressable
                style={styles.emptyBtn}
                onPress={() => navigation.navigate('DeviceList')}>
                <Text style={styles.emptyBtnText}>Pair a device</Text>
              </Pressable>
            </View>
          )
        }
        ListFooterComponent={
          <View style={styles.quickGrid}>
            <Text style={styles.quickHeading}>Quick actions</Text>
            <View style={styles.quickRow}>
              <QuickAction
                icon="📋"
                label="All devices"
                onPress={() => navigation.navigate('DeviceList')}
              />
              <QuickAction
                icon="🔗"
                label="Pair device"
                accent
                onPress={() => navigation.navigate('DeviceList')}
              />
            </View>
            <View style={styles.quickRow}>
              <QuickAction
                icon="🔔"
                label="Alerts"
                onPress={() => navigation.navigate('Alerts')}
              />
              <QuickAction
                icon="📨"
                label="Requests"
                onPress={() => navigation.navigate('Requests')}
              />
            </View>
            <View style={styles.quickRow}>
              <QuickAction
                icon="⚙️"
                label="Settings"
                onPress={() => navigation.navigate('Settings')}
              />
            </View>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#f1f5f9'},
  listContent: {padding: 16, paddingBottom: 32},
  hero: {
    backgroundColor: '#0f172a',
    borderRadius: 20,
    padding: 20,
    marginBottom: 16,
  },
  heroEyebrow: {
    color: '#64748b',
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginBottom: 4,
  },
  heroTitle: {
    color: '#fff',
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 12,
  },
  connectionRow: {flexDirection: 'row', alignItems: 'center'},
  connectionDot: {width: 8, height: 8, borderRadius: 4, marginRight: 8},
  connectionText: {color: '#94a3b8', fontSize: 13},
  statsRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 16,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  statValue: {fontSize: 22, fontWeight: '700', color: '#0f172a'},
  statValueGreen: {color: '#16a34a'},
  statValueAlert: {color: '#dc2626'},
  statLabel: {fontSize: 11, color: '#64748b', marginTop: 2, fontWeight: '600'},
  alertBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff7ed',
    borderRadius: 12,
    padding: 14,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#fed7aa',
  },
  alertIcon: {fontSize: 20, marginRight: 10},
  alertText: {flex: 1},
  alertTitle: {fontSize: 12, fontWeight: '700', color: '#9a3412'},
  alertBody: {fontSize: 14, color: '#c2410c', marginTop: 2},
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  sectionTitle: {fontSize: 16, fontWeight: '700', color: '#0f172a'},
  sectionLink: {fontSize: 14, fontWeight: '600', color: '#2563eb'},
  deviceCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  deviceCardPressed: {backgroundColor: '#f8fafc'},
  statusDot: {width: 10, height: 10, borderRadius: 5, marginRight: 12},
  statusOnline: {backgroundColor: '#22c55e'},
  statusOffline: {backgroundColor: '#cbd5e1'},
  deviceInfo: {flex: 1},
  deviceName: {fontSize: 15, fontWeight: '600', color: '#0f172a'},
  deviceMeta: {fontSize: 12, color: '#64748b', marginTop: 3},
  chevron: {fontSize: 22, color: '#cbd5e1', fontWeight: '300'},
  loader: {marginVertical: 24},
  emptyCard: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 24,
    alignItems: 'center',
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  emptyIcon: {fontSize: 36, marginBottom: 8},
  emptyTitle: {fontSize: 17, fontWeight: '700', color: '#0f172a'},
  emptyBody: {
    fontSize: 13,
    color: '#64748b',
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 19,
  },
  emptyBtn: {
    marginTop: 16,
    backgroundColor: '#2563eb',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 10,
  },
  emptyBtnText: {color: '#fff', fontWeight: '700', fontSize: 14},
  quickGrid: {marginTop: 8},
  quickHeading: {
    fontSize: 13,
    fontWeight: '700',
    color: '#64748b',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 10,
  },
  quickRow: {flexDirection: 'row', gap: 10, marginBottom: 10},
  quickAction: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  quickActionAccent: {
    backgroundColor: '#eff6ff',
    borderColor: '#bfdbfe',
  },
  quickActionPressed: {opacity: 0.85},
  quickIcon: {fontSize: 22, marginBottom: 6},
  quickLabel: {fontSize: 13, fontWeight: '600', color: '#334155'},
  quickLabelAccent: {color: '#1d4ed8'},
});
