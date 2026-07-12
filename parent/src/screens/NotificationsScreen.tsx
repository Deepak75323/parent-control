import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {View, Text, FlatList, StyleSheet, RefreshControl, Alert} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {monitoringApi, NotificationItem} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {EmptyState, HeroBlock, ScreenShell} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'Notifications'>;

export function NotificationsScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const liveNotifications = useRealtimeStore(s => s.liveNotifications);

  const load = useCallback(async () => {
    try {
      const {data} = await monitoringApi.notifications(deviceId);
      setItems(data.data.content);
    } catch {
      Alert.alert('Error', 'Could not load notifications');
    }
  }, [deviceId]);

  useEffect(() => {
    load();
  }, [load]);

  const merged = useMemo(() => {
    const map = new Map<string, NotificationItem>();
    [...liveNotifications, ...items].forEach(n => map.set(n.id, n));
    return Array.from(map.values()).sort(
      (a, b) => new Date(b.postedAt).getTime() - new Date(a.postedAt).getTime(),
    );
  }, [items, liveNotifications]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  return (
    <ScreenShell backLabel="← Back to device" onBack={() => navigation.goBack()} scroll={false}>
      <View style={styles.header}>
        <HeroBlock
          eyebrow="Live updates"
          title="Notifications"
          subtitle="Mirrored from the child device in real time."
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
            <Text style={styles.cardTitle}>{item.title ?? item.packageName ?? 'Notification'}</Text>
            {item.body ? <Text style={styles.body}>{item.body}</Text> : null}
            <Text style={styles.meta}>
              {item.packageName ?? 'unknown'} · {new Date(item.postedAt).toLocaleString()}
            </Text>
          </View>
        )}
        ListEmptyComponent={
          <EmptyState
            icon="📱"
            title="No notifications yet"
            body="Notifications from the child device will appear here."
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
