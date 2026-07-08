import React, {useCallback, useEffect, useState} from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  Alert,
  RefreshControl,
  Pressable,
  ActivityIndicator,
} from 'react-native';
import {useFocusEffect} from '@react-navigation/native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {SafeAreaView} from 'react-native-safe-area-context';
import {RootStackParamList} from '../navigation/RootNavigator';
import {deviceApi, DeviceSummary, childLoginPasswordApi} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {useAuthStore} from '../store/authStore';
import {plog} from '../services/pairingLog';

function apiErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const data = (err as {response?: {data?: {message?: string}}}).response?.data;
    if (data?.message?.trim()) {
      return data.message.trim();
    }
  }
  return fallback;
}

type Props = NativeStackScreenProps<RootStackParamList, 'DeviceList'>;

function isOnline(status?: string): boolean {
  return (status ?? '').toUpperCase() === 'ONLINE';
}

function isRevoked(status?: string): boolean {
  return (status ?? '').toUpperCase() === 'REVOKED';
}

function DeviceRow({
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
  const revoked = isRevoked(status);
  const battery = liveBattery ?? device.batteryLevel;

  return (
    <Pressable
      style={({pressed}) => [styles.deviceCard, pressed && styles.deviceCardPressed]}
      onPress={onPress}>
      <View
        style={[
          styles.statusDot,
          revoked ? styles.statusRevoked : online ? styles.statusOnline : styles.statusOffline,
        ]}
      />
      <View style={styles.deviceInfo}>
        <Text style={styles.deviceName} numberOfLines={1}>
          {device.deviceName}
        </Text>
        <Text style={styles.deviceMeta}>
          {revoked ? 'Revoked' : online ? 'Online' : status ?? 'Offline'}
          {battery != null ? ` · ${battery}%` : ''}
        </Text>
      </View>
      <Text style={styles.chevron}>›</Text>
    </Pressable>
  );
}

function CodeDisplay({code}: {code: string}) {
  const chars = code.split('');
  return (
    <View style={styles.codeRow}>
      {chars.map((ch, i) => (
        <View key={`${i}-${ch}`} style={[styles.codeBox, i === 3 && styles.codeGap]}>
          <Text style={styles.codeChar}>{ch}</Text>
        </View>
      ))}
    </View>
  );
}

export function DeviceListScreen({navigation}: Props) {
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [generating, setGenerating] = useState(false);
  const userEmail = useAuthStore(s => s.email);
  const deviceStatuses = useRealtimeStore(s => s.deviceStatuses);
  const deviceListVersion = useRealtimeStore(s => s.deviceListVersion);
  const lastPairedDevice = useRealtimeStore(s => s.lastPairedDevice);
  const pendingPairingCode = useRealtimeStore(s => s.pendingPairingCode);
  const codeExpiresAt = useRealtimeStore(s => s.codeExpiresAt);
  const wsConnected = useRealtimeStore(s => s.wsConnected);
  const setPendingPairingCode = useRealtimeStore(s => s.setPendingPairingCode);
  const clearPendingPairingCode = useRealtimeStore(s => s.clearPendingPairingCode);

  const load = useCallback(async () => {
    try {
      const {data} = await deviceApi.list();
      const list = data.data ?? [];
      setDevices(list);
      useRealtimeStore.getState().mergeDevices(list);
    } catch {
      Alert.alert('Error', 'Could not load devices');
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      childLoginPasswordApi
        .status()
        .then(r => {
          if (!r.data.data?.configured) {
            navigation.replace('ChildLoginPassword', {afterSetup: 'DeviceList'});
          }
        })
        .catch(() => navigation.replace('ChildLoginPassword', {afterSetup: 'DeviceList'}));
    }, [navigation]),
  );

  useFocusEffect(
    useCallback(() => {
      load();
      const code = useRealtimeStore.getState().pendingPairingCode;
      if (code) {
        deviceApi
          .getPairingCodeStatus(code)
          .then(({data}) => {
            const status = data.data;
            if (status?.used && status.deviceId) {
              plog('focus detected pairing via code status', status);
              useRealtimeStore.getState().notifyPairingComplete({
                deviceId: status.deviceId,
                deviceName: status.deviceName?.trim() || 'Child device',
              });
            }
          })
          .catch(err => plog('focus pairing status error', String(err)));
      }
    }, [load]),
  );

  useEffect(() => {
    load();
  }, [deviceListVersion, load]);

  const generateCode = async () => {
    setGenerating(true);
    try {
      plog('generate pairing code');
      const {data: devicesBefore} = await deviceApi.list();
      const fromApi = devicesBefore.data ?? [];
      const baseline = fromApi.length > 0 ? fromApi : devices;
      const {data} = await deviceApi.createPairingCode();
      const code = data.data?.code?.trim();
      if (!code) {
        throw new Error('Server returned an empty pairing code');
      }
      setPendingPairingCode(code, baseline, data.data.expiresAt ?? null);
      plog('pairing code created', {
        code,
        expiresAt: data.data.expiresAt,
        baselineCount: baseline.length,
      });
      await load();
    } catch (err) {
      plog('pairing code failed', String(err));
      const message = apiErrorMessage(err, 'Could not create pairing code');
      Alert.alert('Error', message);
    } finally {
      setGenerating(false);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const connectionColor = wsConnected ? '#22c55e' : '#f59e0b';
  const connectionLabel = wsConnected
    ? 'Live sync connected'
    : pendingPairingCode
      ? 'Connecting live sync…'
      : 'Live sync offline';

  return (
    <SafeAreaView style={styles.root} edges={['bottom']}>
      <FlatList
        data={devices}
        keyExtractor={item => item.id}
        contentContainerStyle={styles.listContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        showsVerticalScrollIndicator={false}
        ListHeaderComponent={
          <>
            <Pressable style={styles.backRow} onPress={() => navigation.goBack()}>
              <Text style={styles.backText}>← Dashboard</Text>
            </Pressable>

            <View style={styles.hero}>
              <Text style={styles.heroEyebrow}>Device management</Text>
              <Text style={styles.heroTitle}>Pair & manage</Text>
              {userEmail ? (
                <Text style={styles.heroEmail} numberOfLines={1}>
                  {userEmail}
                </Text>
              ) : null}
              <View style={styles.connectionRow}>
                <View style={[styles.connectionDot, {backgroundColor: connectionColor}]} />
                <Text style={styles.connectionText}>{connectionLabel}</Text>
              </View>
            </View>

            {lastPairedDevice ? (
              <View style={styles.successBanner}>
                <Text style={styles.successText}>
                  Just paired: {lastPairedDevice.deviceName}
                </Text>
              </View>
            ) : null}

            {pendingPairingCode ? (
              <View style={styles.pairingCard}>
                <Text style={styles.pairingLabel}>Active pairing code</Text>
                <CodeDisplay code={pendingPairingCode} />
                <Text style={styles.pairingHint}>
                  Enter the setup code on the child phone only if recovery is needed. Children log in
                  with your family password from Settings. One use only — expires in 15 minutes
                  {codeExpiresAt
                    ? ` (until ${new Date(codeExpiresAt).toLocaleTimeString()})`
                    : ''}
                  .
                </Text>
                <View style={styles.waitingRow}>
                  <ActivityIndicator size="small" color="#2563eb" />
                  <Text style={styles.waitingText}>Waiting for child device…</Text>
                </View>
                <Pressable onPress={clearPendingPairingCode}>
                  <Text style={styles.cancelCode}>Cancel code</Text>
                </Pressable>
              </View>
            ) : (
              <Pressable
                style={({pressed}) => [
                  styles.generateBtn,
                  pressed && styles.generateBtnPressed,
                  generating && styles.generateBtnDisabled,
                ]}
                onPress={() => void generateCode()}
                disabled={generating}>
                {generating ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <>
                    <Text style={styles.generateIcon}>🔗</Text>
                    <Text style={styles.generateText}>Generate pairing code</Text>
                  </>
                )}
              </Pressable>
            )}

            <View style={styles.sectionHeader}>
              <Text style={styles.sectionTitle}>Paired devices</Text>
              <Text style={styles.sectionCount}>{devices.length}</Text>
            </View>
          </>
        }
        renderItem={({item}) => {
          const live = deviceStatuses[item.id];
          return (
            <DeviceRow
              device={item}
              liveStatus={live?.status}
              liveBattery={live?.batteryLevel}
              onPress={() => navigation.navigate('DeviceDetail', {deviceId: item.id})}
            />
          );
        }}
        ListEmptyComponent={
          <View style={styles.emptyCard}>
            <Text style={styles.emptyIcon}>📱</Text>
            <Text style={styles.emptyTitle}>No devices paired</Text>
            <Text style={styles.emptyBody}>
              Generate a code above and enter it on your child&apos;s phone.
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#f1f5f9'},
  listContent: {padding: 16, paddingBottom: 32},
  backRow: {marginBottom: 12},
  backText: {color: '#2563eb', fontSize: 14, fontWeight: '600'},
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
    letterSpacing: 0.5,
  },
  heroTitle: {color: '#fff', fontSize: 24, fontWeight: '700', marginTop: 4},
  heroEmail: {color: '#94a3b8', fontSize: 13, marginTop: 6},
  connectionRow: {flexDirection: 'row', alignItems: 'center', marginTop: 12},
  connectionDot: {width: 8, height: 8, borderRadius: 4, marginRight: 8},
  connectionText: {color: '#94a3b8', fontSize: 13},
  successBanner: {
    backgroundColor: '#ecfdf5',
    borderRadius: 12,
    padding: 14,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#bbf7d0',
  },
  successText: {color: '#15803d', fontWeight: '600', textAlign: 'center', fontSize: 14},
  pairingCard: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#bfdbfe',
    alignItems: 'center',
  },
  pairingLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: '#2563eb',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 16,
  },
  codeRow: {flexDirection: 'row', alignItems: 'center', marginBottom: 14},
  codeBox: {
    width: 34,
    height: 44,
    borderRadius: 10,
    backgroundColor: '#eff6ff',
    borderWidth: 1,
    borderColor: '#bfdbfe',
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 2,
  },
  codeGap: {marginRight: 10},
  codeChar: {fontSize: 18, fontWeight: '700', color: '#1e40af'},
  pairingHint: {
    fontSize: 13,
    color: '#64748b',
    textAlign: 'center',
    lineHeight: 19,
    marginBottom: 12,
  },
  waitingRow: {flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10},
  waitingText: {fontSize: 13, color: '#2563eb', fontWeight: '600'},
  cancelCode: {color: '#94a3b8', fontSize: 13, fontWeight: '600'},
  generateBtn: {
    backgroundColor: '#2563eb',
    borderRadius: 14,
    paddingVertical: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
    gap: 8,
  },
  generateBtnPressed: {backgroundColor: '#1d4ed8'},
  generateBtnDisabled: {opacity: 0.7},
  generateIcon: {fontSize: 18},
  generateText: {color: '#fff', fontWeight: '700', fontSize: 16},
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  sectionTitle: {fontSize: 16, fontWeight: '700', color: '#0f172a'},
  sectionCount: {
    fontSize: 13,
    fontWeight: '700',
    color: '#64748b',
    backgroundColor: '#e2e8f0',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 10,
    overflow: 'hidden',
  },
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
  statusRevoked: {backgroundColor: '#dc2626'},
  statusOffline: {backgroundColor: '#cbd5e1'},
  deviceInfo: {flex: 1},
  deviceName: {fontSize: 15, fontWeight: '600', color: '#0f172a'},
  deviceMeta: {fontSize: 12, color: '#64748b', marginTop: 3},
  chevron: {fontSize: 22, color: '#cbd5e1', fontWeight: '300'},
  emptyCard: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 28,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  emptyIcon: {fontSize: 36, marginBottom: 8},
  emptyTitle: {fontSize: 16, fontWeight: '700', color: '#0f172a'},
  emptyBody: {
    fontSize: 13,
    color: '#64748b',
    textAlign: 'center',
    marginTop: 6,
    lineHeight: 19,
  },
});
