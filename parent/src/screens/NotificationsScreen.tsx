import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {View, Text, FlatList, StyleSheet, RefreshControl} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {monitoringApi, NotificationItem} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {EmptyState, HeroBlock, ScreenShell} from '../components/AppScreen';
import {appDisplayName} from '../utils/appDisplayName';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'Notifications'>;

export function NotificationsScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const liveNotifications = useRealtimeStore(s => s.liveNotifications);

  const load = useCallback(async () => {
    try {
      setLoadError(null);
      const {data} = await monitoringApi.notifications(deviceId, 0, 50);
      setItems(data.data.content ?? []);
    } catch {
      setLoadError('Couldn’t load notifications');
      appAlert('Error', 'Couldn’t load child notifications. Pull down to try again.');
    }
  }, [deviceId]);

  useEffect(() => {
    void load();
  }, [load]);

  const merged = useMemo(() => {
    const map = new Map<string, NotificationItem>();
    items.forEach(n => map.set(n.id, n));
    liveNotifications
      .filter(n => !n.deviceId || n.deviceId === deviceId)
      .forEach(n => map.set(n.id, n));
    return Array.from(map.values()).sort(
      (a, b) => new Date(b.postedAt).getTime() - new Date(a.postedAt).getTime(),
    );
  }, [items, liveNotifications, deviceId]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  return (
    <ScreenShell backLabel="← Back to device" onBack={() => navigation.goBack()} scroll={false}>
      <View style={styles.header}>
        <HeroBlock
          eyebrow="Child phone"
          title="Notifications"
          subtitle="History from the child device, plus live updates while this screen is open."
        />
      </View>
      <FlatList
        style={styles.list}
        contentContainerStyle={merged.length === 0 ? styles.listEmpty : styles.listContent}
        data={merged}
        keyExtractor={item => item.id}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        renderItem={({item}) => {
          const appName = appDisplayName({
            packageName: item.packageName,
            fallback: 'App',
          });
          const headline = appDisplayName({
            title: item.title,
            packageName: item.packageName,
            fallback: 'Notification',
          });
          return (
            <View style={styles.card}>
              <Text style={styles.cardTitle}>{headline}</Text>
              {item.body ? <Text style={styles.body}>{item.body}</Text> : null}
              <Text style={styles.meta}>
                {headline !== appName ? `${appName} · ` : ''}
                {new Date(item.postedAt).toLocaleString()}
              </Text>
            </View>
          );
        }}
        ListEmptyComponent={
          <EmptyState
            icon="📱"
            title={loadError ? 'Couldn’t load' : 'No notifications yet'}
            body={
              loadError ??
              'When WhatsApp, SMS, and other apps notify the child phone, they appear here after Clock Time syncs (about every few minutes). Pull down to refresh.'
            }
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
  cardTitle: {fontSize: 15, fontWeight: '600', color: '#0f172a'},
  body: {marginTop: 4, color: '#475569', lineHeight: 20},
  meta: {marginTop: 8, fontSize: 12, color: '#94a3b8'},
});
