import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {Animated, FlatList, Pressable, RefreshControl, StyleSheet, Text, View} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {alertsApi, AlertItem, NotificationItem, timeExtensionApi, TimeExtensionRequestItem} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {Colors, Spacing, Radii, Elevation, Typography} from '../theme/design';
import {useFadeSlideIn} from '../utils/animations';

type Nav = NativeStackNavigationProp<RootStackParamList>;

type NoticeItem =
  | {kind: 'request'; data: TimeExtensionRequestItem}
  | {kind: 'alert'; data: AlertItem}
  | {kind: 'notification'; data: NotificationItem};

function fmtTime(iso: string) {
  const d = new Date(iso);
  const diff = Math.floor((Date.now() - d.getTime()) / 60000);
  if (diff < 1) return 'now';
  if (diff < 60) return `${diff}m ago`;
  if (diff < 1440) return `${Math.floor(diff / 60)}h ago`;
  return d.toLocaleDateString();
}

function alertIcon(type: string) {
  if (type.includes('LOGIN_RECOVERY')) return '🔑';
  if (type.includes('GEOFENCE')) return '📍';
  if (type.includes('BATTERY')) return '🔋';
  if (type.includes('BLOCKED')) return '🚫';
  if (type.includes('EXTENSION')) return '⏰';
  return '⚠️';
}

function severityColor(s: string) {
  const u = s.toUpperCase();
  if (u === 'CRITICAL' || u === 'HIGH') return Colors.danger;
  if (u === 'MEDIUM' || u === 'WARNING') return Colors.warning;
  return Colors.primary;
}

function RequestCard({item, onYes, onNo}: {item: TimeExtensionRequestItem; onYes: () => void; onNo: () => void}) {
  const anim = useFadeSlideIn(0);
  return (
    <Animated.View style={[styles.reqCard, anim]}>
      <View style={styles.reqTop}>
        <View style={styles.reqIconWrap}><Text style={{fontSize: 22}}>⏰</Text></View>
        <View style={styles.reqBody}>
          <Text style={styles.reqBadge}>TIME REQUEST</Text>
          <Text style={styles.reqTitle}>{item.deviceName ?? 'Child'} wants more time</Text>
          <Text style={styles.reqSub}>{item.appLabel ?? item.packageName} · {item.requestedMinutes} min</Text>
        </View>
        <Text style={styles.reqTime}>{fmtTime(item.createdAt)}</Text>
      </View>
      <View style={styles.reqActions}>
        <Pressable style={styles.reqNo} onPress={onNo}><Text style={styles.reqNoText}>Deny</Text></Pressable>
        <Pressable style={styles.reqYes} onPress={onYes}><Text style={styles.reqYesText}>Allow ✓</Text></Pressable>
      </View>
    </Animated.View>
  );
}

function AlertCard({item}: {item: AlertItem}) {
  const color = severityColor(item.severity);
  const verificationCode =
    (item.alertType === 'DEVICE_ADMIN_DISABLE_ATTEMPT' ||
      item.alertType === 'CHILD_LOGIN_RECOVERY') &&
    typeof item.payloadJson?.verificationCode === 'string'
      ? item.payloadJson.verificationCode
      : null;
  return (
    <View style={styles.alertCard}>
      <View style={[styles.alertStripe, {backgroundColor: color}]} />
      <View style={[styles.alertIconWrap, {backgroundColor: color + '15'}]}>
        <Text style={{fontSize: 20}}>{alertIcon(item.alertType)}</Text>
      </View>
      <View style={styles.alertBody}>
        <Text style={[styles.alertType, {color}]}>{item.alertType.replace(/_/g, ' ')}</Text>
        <Text style={styles.alertTitle}>{item.title}</Text>
        {item.message ? <Text style={styles.alertMsg} numberOfLines={3}>{item.message}</Text> : null}
        {verificationCode ? (
          <View style={styles.codeBox}>
            <Text style={styles.codeLabel}>Verification code for child device</Text>
            <Text style={styles.codeValue}>{verificationCode}</Text>
          </View>
        ) : null}
      </View>
      <Text style={styles.alertTime}>{fmtTime(item.createdAt)}</Text>
    </View>
  );
}

function NotifCard({item}: {item: NotificationItem}) {
  return (
    <View style={styles.notifCard}>
      <View style={styles.notifIconWrap}><Text style={{fontSize: 18}}>🔔</Text></View>
      <View style={styles.notifBody}>
        <Text style={styles.notifPkg} numberOfLines={1}>{item.packageName ?? 'App Notification'}</Text>
        <Text style={styles.notifTitle}>{item.title ?? 'Notification'}</Text>
        {item.body ? <Text style={styles.notifMsg} numberOfLines={1}>{item.body}</Text> : null}
      </View>
      <Text style={styles.notifTime}>{fmtTime(item.postedAt)}</Text>
    </View>
  );
}

export function NoticeScreen({navigation}: {navigation: Nav}) {
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [requests, setRequests] = useState<TimeExtensionRequestItem[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const liveAlerts = useRealtimeStore(s => s.liveAlerts);
  const liveNotifications = useRealtimeStore(s => s.liveNotifications);
  const headerAnim = useFadeSlideIn(0);

  const load = useCallback(async () => {
    const [a, r] = await Promise.allSettled([alertsApi.list(0, 30), timeExtensionApi.listPending()]);
    if (a.status === 'fulfilled') setAlerts(a.value.data.data.content);
    if (r.status === 'fulfilled') setRequests(r.value.data.data);
  }, []);

  useEffect(() => { load(); }, [load]);

  const respondRequest = async (id: string, approved: boolean, minutes: number) => {
    try { await timeExtensionApi.respond(id, approved, minutes); await load(); } catch {}
  };

  const mergedAlerts = useMemo(() => {
    const map = new Map<string, AlertItem>();
    [...liveAlerts, ...alerts].forEach(a => map.set(a.id, a));
    return Array.from(map.values()).sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [alerts, liveAlerts]);

  const mergedNotifs = useMemo(() => {
    const map = new Map<string, NotificationItem>();
    liveNotifications.forEach(n => map.set(n.id, n));
    return Array.from(map.values()).sort((a, b) => new Date(b.postedAt).getTime() - new Date(a.postedAt).getTime()).slice(0, 15);
  }, [liveNotifications]);

  const items: NoticeItem[] = [
    ...requests.map(d => ({kind: 'request' as const, data: d})),
    ...mergedAlerts.map(d => ({kind: 'alert' as const, data: d})),
    ...mergedNotifs.map(d => ({kind: 'notification' as const, data: d})),
  ];

  const total = items.length;

  return (
    <SafeAreaView style={styles.root} edges={['top']}>
      <Animated.View style={[styles.header, headerAnim]}>
        <View style={styles.headerBubble} />
        <View style={styles.headerContent}>
          <View>
            <Text style={styles.headerEyebrow}>UPDATES</Text>
            <Text style={styles.headerTitle}>Notice</Text>
          </View>
          <View style={styles.headerBadge}>
            <Text style={styles.headerBadgeText}>{total}</Text>
          </View>
        </View>
      </Animated.View>

      <FlatList
        style={{flex: 1}}
        contentContainerStyle={styles.listContent}
        data={items}
        keyExtractor={(item, i) => `${item.kind}-${item.data.id}-${i}`}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={async () => { setRefreshing(true); await load(); setRefreshing(false); }} tintColor={Colors.primary} />}
        renderItem={({item}) => {
          if (item.kind === 'request') return (
            <RequestCard
              item={item.data}
              onYes={() => respondRequest(item.data.id, true, item.data.requestedMinutes)}
              onNo={() => respondRequest(item.data.id, false, item.data.requestedMinutes)}
            />
          );
          if (item.kind === 'alert') return <AlertCard item={item.data} />;
          return <NotifCard item={item.data} />;
        }}
        ListEmptyComponent={
          <View style={styles.empty}>
            <View style={styles.emptyIcon}><Text style={{fontSize: 40}}>✅</Text></View>
            <Text style={styles.emptyTitle}>All clear!</Text>
            <Text style={styles.emptyBody}>No alerts or pending requests right now.</Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.background},

  header: {
    backgroundColor: Colors.primary,
    paddingBottom: Spacing.xl,
    overflow: 'hidden',
    position: 'relative',
  },
  headerBubble: {position: 'absolute', width: 200, height: 200, borderRadius: 100, backgroundColor: Colors.primaryDark, opacity: 0.3, top: -80, right: -40},
  headerContent: {paddingHorizontal: Spacing.base, paddingTop: Spacing.base, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end'},
  headerEyebrow: {fontSize: 11, color: Colors.accentLight, fontWeight: '700', letterSpacing: 0.6},
  headerTitle: {fontSize: 26, fontWeight: '900', color: '#fff', marginTop: 2},
  headerBadge: {backgroundColor: 'rgba(255,255,255,0.2)', borderRadius: Radii.pill, paddingHorizontal: Spacing.md, paddingVertical: 6, minWidth: 36, alignItems: 'center'},
  headerBadgeText: {fontSize: 16, fontWeight: '800', color: '#fff'},

  listContent: {padding: Spacing.base, gap: Spacing.sm, paddingBottom: Spacing.xxl},

  // Request card
  reqCard: {backgroundColor: Colors.surface, borderRadius: Radii.xl, padding: Spacing.base, ...Elevation.cardHigh, borderWidth: 1.5, borderColor: Colors.primary + '40'},
  reqTop: {flexDirection: 'row', alignItems: 'flex-start', marginBottom: Spacing.md},
  reqIconWrap: {width: 44, height: 44, borderRadius: Radii.md, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginRight: Spacing.md},
  reqBody: {flex: 1},
  reqBadge: {fontSize: 10, fontWeight: '800', color: Colors.primary, letterSpacing: 0.5, marginBottom: 3},
  reqTitle: {fontSize: 15, fontWeight: '800', color: Colors.textPrimary},
  reqSub: {fontSize: 13, color: Colors.textSecondary, marginTop: 2},
  reqTime: {fontSize: 11, color: Colors.textTertiary, marginLeft: Spacing.sm},
  reqActions: {flexDirection: 'row', gap: Spacing.sm},
  reqNo: {flex: 1, backgroundColor: Colors.background, borderRadius: Radii.md, paddingVertical: Spacing.md, alignItems: 'center', borderWidth: 1, borderColor: Colors.border},
  reqYes: {flex: 1.5, backgroundColor: Colors.primary, borderRadius: Radii.md, paddingVertical: Spacing.md, alignItems: 'center'},
  reqNoText: {fontSize: 14, fontWeight: '700', color: Colors.textSecondary},
  reqYesText: {fontSize: 14, fontWeight: '800', color: '#fff'},

  // Alert card
  alertCard: {backgroundColor: Colors.surface, borderRadius: Radii.xl, padding: Spacing.base, flexDirection: 'row', alignItems: 'center', ...Elevation.card, borderWidth: 1, borderColor: Colors.borderLight, overflow: 'hidden'},
  alertStripe: {position: 'absolute', left: 0, top: 0, bottom: 0, width: 4, borderTopLeftRadius: Radii.xl, borderBottomLeftRadius: Radii.xl},
  alertIconWrap: {width: 44, height: 44, borderRadius: Radii.md, alignItems: 'center', justifyContent: 'center', marginRight: Spacing.md, marginLeft: Spacing.sm},
  alertBody: {flex: 1},
  alertType: {fontSize: 10, fontWeight: '800', letterSpacing: 0.5, marginBottom: 3},
  alertTitle: {fontSize: 14, fontWeight: '700', color: Colors.textPrimary},
  alertMsg: {fontSize: 12, color: Colors.textTertiary, marginTop: 2, lineHeight: 17},
  codeBox: {marginTop: 8, backgroundColor: '#ede9fe', borderRadius: Radii.md, padding: Spacing.sm},
  codeLabel: {fontSize: 11, fontWeight: '700', color: '#6d28d9'},
  codeValue: {fontSize: 24, fontWeight: '900', color: '#5b21b6', letterSpacing: 4, marginTop: 2},
  alertTime: {fontSize: 11, color: Colors.textTertiary, marginLeft: Spacing.sm},

  // Notification card
  notifCard: {backgroundColor: Colors.surface, borderRadius: Radii.lg, padding: Spacing.md, flexDirection: 'row', alignItems: 'center', ...Elevation.card, borderWidth: 1, borderColor: Colors.borderLight},
  notifIconWrap: {width: 38, height: 38, borderRadius: Spacing.sm, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginRight: Spacing.md},
  notifBody: {flex: 1},
  notifPkg: {fontSize: 10, fontWeight: '700', color: Colors.primary, letterSpacing: 0.3, marginBottom: 2},
  notifTitle: {fontSize: 14, fontWeight: '700', color: Colors.textPrimary},
  notifMsg: {fontSize: 12, color: Colors.textTertiary, marginTop: 1},
  notifTime: {fontSize: 11, color: Colors.textTertiary, marginLeft: Spacing.sm},

  // Empty
  empty: {alignItems: 'center', paddingTop: 70},
  emptyIcon: {width: 88, height: 88, borderRadius: 44, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginBottom: Spacing.lg},
  emptyTitle: {fontSize: 20, fontWeight: '800', color: Colors.textPrimary},
  emptyBody: {fontSize: 14, color: Colors.textTertiary, textAlign: 'center', marginTop: 8, lineHeight: 21, paddingHorizontal: Spacing.xxl},
});
