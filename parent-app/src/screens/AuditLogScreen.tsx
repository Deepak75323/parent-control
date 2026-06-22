import React, {useEffect, useState} from 'react';
import {View, Text, FlatList, StyleSheet, RefreshControl} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {auditApi, AuditLogItem} from '../services/api';
import {EmptyState, HeroBlock, LoadingCenter, ScreenShell} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'AuditLog'>;

function actionIcon(action: string): string {
  const a = action.toUpperCase();
  if (a.includes('BILLING') || a.includes('CHECKOUT')) return '💳';
  if (a.includes('STREAM')) return '📺';
  if (a.includes('PIN')) return '🔒';
  if (a.includes('LOGIN') || a.includes('AUTH')) return '👤';
  return '📋';
}

function formatAction(action: string): string {
  return action
    .toLowerCase()
    .split('_')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

export function AuditLogScreen({navigation}: Props) {
  const [logs, setLogs] = useState<AuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    auditApi
      .list(0, 100)
      .then(res => setLogs(res.data.data.content))
      .catch(() => setLogs([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  if (loading && logs.length === 0) return <LoadingCenter />;

  return (
    <ScreenShell backLabel="← Back to settings" onBack={() => navigation.goBack()} scroll={false}>
      <View style={styles.header}>
        <HeroBlock
          eyebrow="Activity"
          title="Audit log"
          subtitle="Recent actions on your account."
        />
      </View>
      <FlatList
        style={styles.list}
        contentContainerStyle={logs.length === 0 ? styles.listEmpty : styles.listContent}
        data={logs}
        keyExtractor={item => item.id}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={load} />}
        ListEmptyComponent={
          <EmptyState icon="📋" title="No activity yet" body="Account events will appear here." />
        }
        renderItem={({item}) => (
          <View style={styles.row}>
            <Text style={styles.rowIcon}>{actionIcon(item.action)}</Text>
            <View style={styles.rowBody}>
              <Text style={styles.action}>{formatAction(item.action)}</Text>
              {item.resource ? (
                <Text style={styles.meta} numberOfLines={2}>
                  {item.resource}
                </Text>
              ) : null}
              <Text style={styles.time}>{new Date(item.createdAt).toLocaleString()}</Text>
            </View>
          </View>
        )}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  header: {paddingHorizontal: 16, paddingTop: 8},
  list: {flex: 1},
  listContent: {paddingHorizontal: 16, paddingBottom: 24},
  listEmpty: {flexGrow: 1, paddingHorizontal: 16, paddingBottom: 24},
  row: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 14,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  rowIcon: {fontSize: 22, marginRight: 12, marginTop: 2},
  rowBody: {flex: 1},
  action: {fontWeight: '600', fontSize: 15, color: '#0f172a'},
  meta: {color: '#64748b', marginTop: 4, fontSize: 12, lineHeight: 17},
  time: {color: '#94a3b8', marginTop: 6, fontSize: 12},
});
