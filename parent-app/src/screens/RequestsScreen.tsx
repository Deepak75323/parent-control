import React, {useCallback, useEffect, useState} from 'react';
import {View, Text, FlatList, StyleSheet, RefreshControl, Alert, Pressable} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {TimeExtensionRequestItem, timeExtensionApi} from '../services/api';
import {EmptyState, HeroBlock, PrimaryBtn, ScreenShell} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'Requests'>;

export function RequestsScreen({navigation}: Props) {
  const [items, setItems] = useState<TimeExtensionRequestItem[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [respondingId, setRespondingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const {data} = await timeExtensionApi.listPending();
      setItems(data.data);
    } catch {
      Alert.alert('Error', 'Could not load requests');
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const respond = async (item: TimeExtensionRequestItem, approved: boolean) => {
    setRespondingId(item.id);
    try {
      await timeExtensionApi.respond(item.id, approved, item.requestedMinutes);
      await load();
      Alert.alert(
        approved ? 'Approved' : 'Denied',
        approved
          ? `${item.appLabel ?? item.packageName} unlocked for ${item.requestedMinutes} minutes`
          : 'Request rejected',
      );
    } catch {
      Alert.alert('Error', 'Could not respond to request');
    } finally {
      setRespondingId(null);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  return (
    <ScreenShell backLabel="← Back" onBack={() => navigation.goBack()} scroll={false}>
      <View style={styles.header}>
        <HeroBlock
          eyebrow="Alerts"
          title="Requests"
          subtitle="Child asked for more time on blocked apps."
        />
      </View>
      <FlatList
        style={styles.list}
        contentContainerStyle={items.length === 0 ? styles.listEmpty : styles.listContent}
        data={items}
        keyExtractor={item => item.id}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        renderItem={({item}) => (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>
              {item.deviceName ?? 'Device'} requests access
            </Text>
            <Text style={styles.appName}>{item.appLabel ?? item.packageName}</Text>
            <Text style={styles.meta}>
              {item.requestedMinutes} min · {new Date(item.createdAt).toLocaleString()}
            </Text>
            <View style={styles.actions}>
              <Pressable
                style={[styles.btn, styles.btnNo, respondingId === item.id && styles.btnDisabled]}
                disabled={respondingId === item.id}
                onPress={() => respond(item, false)}>
                <Text style={styles.btnNoText}>No</Text>
              </Pressable>
              <Pressable
                style={[styles.btn, styles.btnYes, respondingId === item.id && styles.btnDisabled]}
                disabled={respondingId === item.id}
                onPress={() => respond(item, true)}>
                <Text style={styles.btnYesText}>Yes</Text>
              </Pressable>
            </View>
          </View>
        )}
        ListEmptyComponent={
          <EmptyState
            icon="✅"
            title="No pending requests"
            body="When your child taps “Ask parent for more time”, requests appear here."
          />
        }
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  header: {paddingHorizontal: 16},
  list: {flex: 1},
  listContent: {paddingHorizontal: 16, paddingBottom: 24},
  listEmpty: {flexGrow: 1, paddingHorizontal: 16},
  card: {
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 16,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  cardTitle: {fontSize: 14, color: '#64748b', marginBottom: 4},
  appName: {fontSize: 18, fontWeight: '700', color: '#0f172a'},
  meta: {fontSize: 12, color: '#94a3b8', marginTop: 6, marginBottom: 12},
  actions: {flexDirection: 'row', gap: 10},
  btn: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: 'center',
  },
  btnNo: {backgroundColor: '#f1f5f9', borderWidth: 1, borderColor: '#e2e8f0'},
  btnYes: {backgroundColor: '#2563eb'},
  btnDisabled: {opacity: 0.6},
  btnNoText: {fontWeight: '600', color: '#475569'},
  btnYesText: {fontWeight: '600', color: '#fff'},
});
