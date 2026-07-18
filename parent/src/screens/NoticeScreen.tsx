import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {Animated, FlatList, Pressable, RefreshControl, StyleSheet, Text, View} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {
  alertsApi,
  AlertItem,
  deviceApi,
  DeviceSummary,
  monitoringApi,
  NotificationItem,
  timeExtensionApi,
  TimeExtensionRequestItem,
} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {Colors, Spacing, Radii, Elevation, Typography, Layout} from '../theme/design';
import {useFadeSlideIn} from '../utils/animations';
import {appDisplayName} from '../utils/appDisplayName';

type Nav = NativeStackNavigationProp<RootStackParamList>;

type NoticeItem =
  | {kind: 'request'; data: TimeExtensionRequestItem}
  | {kind: 'alert'; data: AlertItem}
  | {kind: 'notification'; data: NotificationItem & {deviceId?: string}};

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
          <Text style={styles.reqSub}>
            {appDisplayName({appLabel: item.appLabel, packageName: item.packageName})} ·{' '}
            {item.requestedMinutes} min
          </Text>
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

function NotifCard({
  item,
  onOpen,
}: {
  item: NotificationItem & {deviceId?: string};
  onOpen?: () => void;
}) {
  return (
    <Pressable style={styles.notifCard} onPress={onOpen} disabled={!onOpen}>
      <View style={styles.notifIconWrap}><Text style={{fontSize: 18}}>🔔</Text></View>
      <View style={styles.notifBody}>
        <Text style={styles.notifPkg} numberOfLines={1}>
          {appDisplayName({
            title: item.title,
            packageName: item.packageName,
            fallback: 'App notification',
          })}
        </Text>
        <Text style={styles.notifTitle}>{item.title ?? 'Notification'}</Text>
        {item.body ? <Text style={styles.notifMsg} numberOfLines={1}>{item.body}</Text> : null}
      </View>
      <Text style={styles.notifTime}>{fmtTime(item.postedAt)}</Text>
    </Pressable>
  );
}

export function NoticeScreen({navigation}: {navigation: Nav}) {
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [requests, setRequests] = useState<TimeExtensionRequestItem[]>([]);
  const [historyNotifs, setHistoryNotifs] = useState<Array<NotificationItem & {deviceId?: string}>>([]);
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const liveAlerts = useRealtimeStore(s => s.liveAlerts);
  const liveNotifications = useRealtimeStore(s => s.liveNotifications);
  const headerAnim = useFadeSlideIn(0);

  const load = useCallback(async () => {
    const [a, r, d] = await Promise.allSettled([
      alertsApi.list(0, 30),
      timeExtensionApi.listPending(),
      deviceApi.list(),
    ]);
    if (a.status === 'fulfilled') setAlerts(a.value.data.data.content);
    if (r.status === 'fulfilled') setRequests(r.value.data.data);
    const deviceList =
      d.status === 'fulfilled' ? (d.value.data.data ?? []) : [];
    setDevices(deviceList);

    if (deviceList.length === 0) {
      setHistoryNotifs([]);
      return;
    }
    const perDevice = await Promise.allSettled(
      deviceList.slice(0, 5).map(async device => {
        const res = await monitoringApi.notifications(device.id, 0, 20);
        return (res.data.data.content ?? []).map(n => ({...n, deviceId: device.id}));
      }),
    );
    const merged: Array<NotificationItem & {deviceId?: string}> = [];
    perDevice.forEach(result => {
      if (result.status === 'fulfilled') merged.push(...result.value);
    });
    merged.sort((x, y) => new Date(y.postedAt).getTime() - new Date(x.postedAt).getTime());
    setHistoryNotifs(merged.slice(0, 40));
  }, []);

  useEffect(() => { void load(); }, [load]);

  const respondRequest = async (id: string, approved: boolean, minutes: number) => {
    try { await timeExtensionApi.respond(id, approved, minutes); await load(); } catch {}
  };

  const mergedAlerts = useMemo(() => {
    const map = new Map<string, AlertItem>();
    [...liveAlerts, ...alerts].forEach(a => map.set(a.id, a));
    return Array.from(map.values()).sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [alerts, liveAlerts]);

  const mergedNotifs = useMemo(() => {
    const map = new Map<string, NotificationItem & {deviceId?: string}>();
    historyNotifs.forEach(n => map.set(n.id, n));
    liveNotifications.forEach(n => map.set(n.id, n));
    return Array.from(map.values())
      .sort((a, b) => new Date(b.postedAt).getTime() - new Date(a.postedAt).getTime())
      .slice(0, 25);
  }, [historyNotifs, liveNotifications]);

  const primaryDeviceId = devices[0]?.id;

  const items: NoticeItem[] = [
    ...requests.map(d => ({kind: 'request' as const, data: d})),
    ...mergedAlerts.map(d => ({kind: 'alert' as const, data: d})),
    ...mergedNotifs.map(d => ({kind: 'notification' as const, data: d})),
  ];

  const total = items.length;

  return (
    <SafeAreaView style={styles.root} edges={['top']}>
      <Animated.View style={[styles.header, headerAnim]}>
        <View style={styles.headerGradientBase} />
        <View style={styles.headerGradientOverlay} />
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
        {primaryDeviceId ? (
          <Pressable
            style={styles.childNotifLink}
            onPress={() => navigation.navigate('Notifications', {deviceId: primaryDeviceId})}>
            <Text style={styles.childNotifLinkText}>Child notifications ›</Text>
          </Pressable>
        ) : null}
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
          const notif = item.data;
          const openId = notif.deviceId ?? primaryDeviceId;
          return (
            <NotifCard
              item={notif}
              onOpen={openId ? () => navigation.navigate('Notifications', {deviceId: openId}) : undefined}
            />
          );
        }}
        ListEmptyComponent={
          <View style={styles.empty}>
            <View style={styles.emptyIcon}><Text style={{fontSize: 40}}>✅</Text></View>
            <Text style={styles.emptyTitle}>All clear!</Text>
            <Text style={styles.emptyBody}>
              No alerts yet. Child phone notifications appear here after Clock Time syncs — open a device → Child notifications for full history.
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.background},

  header: {
    paddingBottom: Layout.gutter,
    overflow: 'hidden',
    paddingHorizontal: Layout.screenPadding,
  },
  headerGradientBase: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: Colors.primary,
  },
  headerGradientOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  headerBubble: {
    position: 'absolute',
    right: -40,
    top: -40,
    width: 140,
    height: 140,
    borderRadius: 70,
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  headerContent: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: Spacing.md,
  },
  headerEyebrow: {...Typography.smallLabel, color: 'rgba(255,255,255,0.75)'},
  headerTitle: {...Typography.pageTitle, color: '#fff'},
  headerBadge: {
    minWidth: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(255,255,255,0.25)',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 8,
  },
  headerBadgeText: {color: '#fff', fontWeight: '700', fontSize: 13},
  childNotifLink: {marginTop: Spacing.sm, alignSelf: 'flex-start'},
  childNotifLinkText: {color: '#fff', fontWeight: '600', fontSize: 14, opacity: 0.95},

  listContent: {padding: Layout.screenPadding, paddingBottom: 40},

  reqCard: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.lg,
    padding: Spacing.md,
    marginBottom: Spacing.sm,
    ...Elevation.card,
  },
  reqTop: {flexDirection: 'row', alignItems: 'flex-start'},
  reqIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: Colors.primaryContainer + '22',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: Spacing.sm,
  },
  reqBody: {flex: 1},
  reqBadge: {...Typography.smallLabel, color: Colors.primary, marginBottom: 2},
  reqTitle: {...Typography.cardTitle, color: Colors.onSurface},
  reqSub: {...Typography.caption, color: Colors.onSurfaceVariant, marginTop: 2},
  reqTime: {...Typography.caption, color: Colors.onSurfaceVariant},
  reqActions: {flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.md},
  reqNo: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: Radii.md,
    backgroundColor: Colors.surfaceContainer,
    alignItems: 'center',
  },
  reqNoText: {fontWeight: '600', color: Colors.onSurfaceVariant},
  reqYes: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: Radii.md,
    backgroundColor: Colors.primary,
    alignItems: 'center',
  },
  reqYesText: {fontWeight: '700', color: '#fff'},

  alertCard: {
    flexDirection: 'row',
    backgroundColor: Colors.surface,
    borderRadius: Radii.lg,
    marginBottom: Spacing.sm,
    overflow: 'hidden',
    ...Elevation.card,
  },
  alertStripe: {width: 4},
  alertIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    margin: Spacing.sm,
  },
  alertBody: {flex: 1, paddingVertical: Spacing.sm, paddingRight: Spacing.sm},
  alertType: {...Typography.smallLabel, marginBottom: 2},
  alertTitle: {...Typography.body, fontWeight: '600', color: Colors.onSurface},
  alertMsg: {...Typography.caption, color: Colors.onSurfaceVariant, marginTop: 2},
  alertTime: {...Typography.caption, color: Colors.onSurfaceVariant, padding: Spacing.sm},
  codeBox: {
    marginTop: Spacing.sm,
    padding: Spacing.sm,
    borderRadius: Radii.md,
    backgroundColor: Colors.surfaceContainer,
  },
  codeLabel: {...Typography.caption, color: Colors.onSurfaceVariant},
  codeValue: {...Typography.cardTitle, letterSpacing: 2, marginTop: 4},

  notifCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    borderRadius: Radii.lg,
    padding: Spacing.sm,
    marginBottom: Spacing.sm,
    ...Elevation.card,
  },
  notifIconWrap: {
    width: 40,
    height: 40,
    borderRadius: 10,
    backgroundColor: Colors.surfaceContainer,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: Spacing.sm,
  },
  notifBody: {flex: 1},
  notifPkg: {...Typography.smallLabel, color: Colors.onSurfaceVariant},
  notifTitle: {...Typography.body, fontWeight: '600', color: Colors.onSurface},
  notifMsg: {...Typography.caption, color: Colors.onSurfaceVariant, marginTop: 2},
  notifTime: {...Typography.caption, color: Colors.onSurfaceVariant, marginLeft: Spacing.xs},

  empty: {alignItems: 'center', paddingTop: 48, paddingHorizontal: Spacing.lg},
  emptyIcon: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: Colors.surfaceContainer,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.md,
  },
  emptyTitle: {...Typography.cardTitle, color: Colors.onSurface},
  emptyBody: {...Typography.body, color: Colors.onSurfaceVariant, textAlign: 'center', marginTop: Spacing.xs, lineHeight: 22},
});
