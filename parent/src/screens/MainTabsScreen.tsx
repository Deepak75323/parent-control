import React, {useCallback, useEffect, useState} from 'react';
import {
  Animated,
  FlatList,
  Pressable,
  RefreshControl,
  StatusBar,
  StyleSheet,
  Text,
  View,
  ActivityIndicator,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {navigateToPairingFlow} from '../navigation/pairingNavigation';
import {deviceApi, DeviceSummary} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {NoticeScreen} from './NoticeScreen';
import {ProfileScreen} from './ProfileScreen';
import {EmailVerificationBanner} from '../components/EmailVerificationBanner';
import {Colors, Spacing, Radii, Elevation, Typography, Layout} from '../theme/design';
import {useFadeSlideIn, usePressScale, useOpacityPulse} from '../utils/animations';

type Props = NativeStackScreenProps<RootStackParamList, 'MainTabs'>;
type Tab = 'device' | 'notice' | 'me';

function isOnline(status?: string): boolean {
  return (status ?? '').toUpperCase() === 'ONLINE';
}

function PairDeviceButton({
  label,
  onPress,
  variant = 'primary',
}: {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'outline';
}) {
  const press = usePressScale(0.97);
  return (
    <Animated.View style={{transform: press.scale}}>
      <Pressable
        style={[styles.pairBtn, variant === 'outline' && styles.pairBtnOutline]}
        onPress={onPress}
        onPressIn={press.onPressIn}
        onPressOut={press.onPressOut}>
        <Text style={styles.pairBtnIcon}>🔗</Text>
        <Text style={[styles.pairBtnText, variant === 'outline' && styles.pairBtnTextOutline]}>
          {label}
        </Text>
      </Pressable>
    </Animated.View>
  );
}

function EmptyDevices({onPair}: {onPair: () => void}) {
  const anim = useFadeSlideIn(100);
  return (
    <Animated.View style={[styles.emptyWrap, anim]}>
      <View style={styles.emptyIllustration}>
        <Text style={{fontSize: 52}}>📱</Text>
      </View>
      <Text style={styles.emptyTitle}>No devices connected</Text>
      <Text style={styles.emptyBody}>
        Generate a pairing code and enter it on your child&apos;s phone to start monitoring.
      </Text>
      <View style={{marginTop: Spacing.lg}}>
        <PairDeviceButton label="Pair a device" onPress={onPair} />
      </View>
    </Animated.View>
  );
}

function PairMoreFooter({onPair}: {onPair: () => void}) {
  const anim = useFadeSlideIn(80);
  return (
    <Animated.View style={[styles.pairMoreFooter, anim]}>
      <Text style={styles.pairMoreHint}>Add another child phone to your family</Text>
      <PairDeviceButton label="Pair more devices" onPress={onPair} variant="outline" />
    </Animated.View>
  );
}

function DeviceCard({device, liveStatus, liveBattery, foregroundApp, onPress, index}: {
  device: DeviceSummary;
  liveStatus?: string;
  liveBattery?: number;
  foregroundApp?: string;
  onPress: () => void;
  index: number;
}) {
  const status = liveStatus ?? device.status;
  const online = isOnline(status);
  const battery = liveBattery ?? device.batteryLevel;
  const press = usePressScale(0.97);
  const anim = useFadeSlideIn(index * 70);
  const blink = useOpacityPulse(0.5, 1, 800);
  const name = device.deviceName;
  const initials = name.split(' ').map((w: string) => w[0]).slice(0, 2).join('').toUpperCase();

  return (
    <Animated.View style={[{marginBottom: Spacing.md}, anim]}>
      <Animated.View style={{transform: press.scale}}>
        <Pressable style={styles.deviceCard} onPress={onPress} onPressIn={press.onPressIn} onPressOut={press.onPressOut}>
          {/* Avatar */}
          <View style={[styles.deviceAvatar, {backgroundColor: online ? Colors.primarySurface : '#F1F5F9'}]}>
            <Text style={[styles.deviceAvatarText, {color: online ? Colors.primary : Colors.textTertiary}]}>{initials}</Text>
            <Animated.View style={[styles.deviceAvatarDot, {backgroundColor: online ? Colors.online : Colors.offline, opacity: online ? blink : 1}]} />
          </View>

          {/* Info */}
          <View style={styles.deviceInfo}>
            <Text style={styles.deviceName}>{name}</Text>
            <View style={styles.deviceRow}>
              <View style={[styles.statusPill, {backgroundColor: online ? Colors.successLight : '#F1F5F9'}]}>
                <Text style={[styles.statusPillText, {color: online ? Colors.success : Colors.textTertiary}]}>
                  {online ? '● Online' : '○ Offline'}
                </Text>
              </View>
              {battery != null ? <Text style={styles.batteryText}>🔋 {battery}%</Text> : null}
            </View>
            {foregroundApp ? (
              <View style={styles.liveAppBadge}>
                <View style={styles.liveAppDot} />
                <Text style={styles.liveAppText} numberOfLines={1}>
                  {foregroundApp.split('.').pop()}
                </Text>
              </View>
            ) : null}
          </View>

          {/* Chevron */}
          <View style={styles.chevronWrap}>
            <Text style={styles.chevron}>›</Text>
          </View>
        </Pressable>
      </Animated.View>
    </Animated.View>
  );
}

function DeviceListTab({navigation}: Props) {
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const deviceStatuses = useRealtimeStore(s => s.deviceStatuses);
  const deviceListVersion = useRealtimeStore(s => s.deviceListVersion);
  const wsConnected = useRealtimeStore(s => s.wsConnected);

  const heroAnim = useFadeSlideIn(0);
  const wsBlink = useOpacityPulse(0.4, 1, 600);

  const load = useCallback(async () => {
    try {
      const r = await deviceApi.list();
      const list = r.data.data ?? [];
      setDevices(list);
      useRealtimeStore.getState().mergeDevices(list);
    } catch {} finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load, deviceListVersion]);

  const onRefresh = async () => { setRefreshing(true); await load(); setRefreshing(false); };

  const onlineCount = devices.filter(d => isOnline(deviceStatuses[d.id]?.status ?? d.status)).length;
  const goToPairing = () => {
    void navigateToPairingFlow(navigation);
  };

  return (
    <FlatList
      style={{flex: 1}}
      contentContainerStyle={styles.listContent}
      data={devices}
      keyExtractor={d => d.id}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={Colors.primary} colors={[Colors.primary]} />}
      ListHeaderComponent={
        <Animated.View style={heroAnim}>
          {/* Hero */}
          <View style={styles.hero}>
            <View style={styles.heroGradientBase} />
            <View style={styles.heroGradientOverlay} />
            <View style={styles.heroBubble1} />
            <View style={styles.heroBubble2} />
            <View style={styles.heroContent}>
              <View style={styles.heroTop}>
                <View>
                  <Text style={styles.heroGreeting}>Good morning 👋</Text>
                  <Text style={styles.heroTitle}>Your Family</Text>
                </View>
                <Animated.View style={[styles.wsIndicator, {opacity: wsConnected ? wsBlink : 0.5}]}>
                  <View style={[styles.wsDot, {backgroundColor: wsConnected ? '#4ADE80' : '#FCD34D'}]} />
                  <Text style={styles.wsText}>{wsConnected ? 'Live' : 'Offline'}</Text>
                </Animated.View>
              </View>

              {/* Stats row */}
              <View style={styles.statsRow}>
                <View style={styles.statCard}>
                  <Text style={styles.statNum}>{devices.length}</Text>
                  <Text style={styles.statLabel}>Devices</Text>
                </View>
                <View style={[styles.statCard, styles.statCardOnline]}>
                  <Text style={[styles.statNum, {color: '#fff'}]}>{onlineCount}</Text>
                  <Text style={[styles.statLabel, {color: 'rgba(255,255,255,0.75)'}]}>Online</Text>
                </View>
                <View style={styles.statCard}>
                  <Text style={styles.statNum}>{devices.length - onlineCount}</Text>
                  <Text style={styles.statLabel}>Offline</Text>
                </View>
              </View>
            </View>
          </View>

          {/* Section label */}
          <View style={styles.listHeader}>
            <Text style={styles.listHeaderText}>CONNECTED DEVICES</Text>
          </View>
        </Animated.View>
      }
      renderItem={({item, index}) => {
        const live = deviceStatuses[item.id];
        return (
          <View style={{paddingHorizontal: Layout.screenPadding}}>
            <DeviceCard
              device={item}
              liveStatus={live?.status}
              liveBattery={live?.batteryLevel}
              foregroundApp={live?.foregroundApp}
              index={index}
              onPress={() => navigation.navigate('DeviceDetail', {deviceId: item.id})}
            />
          </View>
        );
      }}
      ListEmptyComponent={
        loading
          ? <ActivityIndicator color={Colors.primary} size="large" style={{marginTop: 60}} />
          : <EmptyDevices onPair={goToPairing} />
      }
      ListFooterComponent={
        !loading && devices.length > 0 ? <PairMoreFooter onPair={goToPairing} /> : null
      }
    />
  );
}

const TAB_CONFIG: {id: Tab; icon: string; label: string}[] = [
  {id: 'device', icon: '▦', label: 'Dashboard'},
  {id: 'notice', icon: '🔔', label: 'Alerts'},
  {id: 'me', icon: '👤', label: 'Me'},
];

export function MainTabsScreen(props: Props) {
  const {navigation} = props;
  const [activeTab, setActiveTab] = useState<Tab>('device');
  const liveAlerts = useRealtimeStore(s => s.liveAlerts);
  const liveNotifications = useRealtimeStore(s => s.liveNotifications);
  const noticeBadge = liveAlerts.length + liveNotifications.length;

  return (
    <SafeAreaView style={styles.root} edges={['bottom']}>
      <StatusBar barStyle={activeTab === 'device' ? 'light-content' : 'dark-content'} backgroundColor={activeTab === 'device' ? Colors.primaryContainer : Colors.surface} />
      <View style={styles.content}>
        <EmailVerificationBanner />
        {activeTab === 'device' && <DeviceListTab {...props} />}
        {activeTab === 'notice' && <NoticeScreen navigation={navigation} />}
        {activeTab === 'me' && <ProfileScreen navigation={navigation} />}
      </View>

      {/* Premium Bottom Tab Bar */}
      <View style={styles.tabBar}>
        {TAB_CONFIG.map(tab => {
          const active = activeTab === tab.id;
          const badge = tab.id === 'notice' && noticeBadge > 0 ? noticeBadge : 0;
          return (
            <Pressable
              key={tab.id}
              style={[styles.tabItem, active && styles.tabItemActive]}
              onPress={() => setActiveTab(tab.id)}>
              <View style={styles.tabIconRow}>
                <Text style={[styles.tabIcon, active && styles.tabIconActive]}>{tab.icon}</Text>
                {badge > 0 && (
                  <View style={styles.badge}>
                    <Text style={styles.badgeText}>{badge > 9 ? '9+' : badge}</Text>
                  </View>
                )}
              </View>
              <Text style={[styles.tabLabel, active && styles.tabLabelActive]}>{tab.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.background},
  content: {flex: 1},
  listContent: {paddingBottom: Layout.gutter},

  // Hero
  hero: {
    paddingBottom: Spacing.xxl,
    overflow: 'hidden',
    position: 'relative',
  },
  heroGradientBase: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: Colors.primaryContainer,
  },
  heroGradientOverlay: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: '40%',
    backgroundColor: Colors.accent,
    opacity: 0.85,
  },
  heroBubble1: {
    position: 'absolute',
    width: 300,
    height: 300,
    borderRadius: 150,
    backgroundColor: 'rgba(255,255,255,0.08)',
    top: -90,
    right: -60,
  },
  heroBubble2: {
    position: 'absolute',
    width: 200,
    height: 200,
    borderRadius: 100,
    backgroundColor: 'rgba(255,255,255,0.06)',
    bottom: -60,
    left: -50,
  },
  heroContent: {paddingHorizontal: Layout.screenPadding, paddingTop: Layout.gutter, zIndex: 1},
  heroTop: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: Layout.gutter},
  heroGreeting: {fontSize: 13, color: Colors.accentLight, fontWeight: '600', marginBottom: 3},
  heroTitle: {fontSize: 28, fontWeight: '900', color: '#fff', letterSpacing: -0.6},
  wsIndicator: {flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(255,255,255,0.1)', borderRadius: Radii.pill, paddingHorizontal: Spacing.sm + 2, paddingVertical: 5, borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)'},
  wsDot: {width: 6, height: 6, borderRadius: 3, marginRight: 5},
  wsText: {fontSize: 12, color: '#fff', fontWeight: '700'},
  statsRow: {flexDirection: 'row', gap: Spacing.sm},
  statCard: {flex: 1, backgroundColor: 'rgba(255,255,255,0.1)', borderRadius: Radii.lg, padding: Spacing.md, borderWidth: 1, borderColor: 'rgba(255,255,255,0.12)'},
  statCardOnline: {backgroundColor: 'rgba(16,185,129,0.3)', borderColor: 'rgba(16,185,129,0.4)'},
  statNum: {fontSize: 26, fontWeight: '900', color: '#fff', letterSpacing: -0.5},
  statLabel: {fontSize: 11, color: 'rgba(255,255,255,0.65)', fontWeight: '600', marginTop: 2},

  listHeader: {paddingHorizontal: Layout.screenPadding, paddingTop: Layout.gutter, paddingBottom: Spacing.sm},
  listHeaderText: {...Typography.caption},

  // Device card
  deviceCard: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Spacing.base,
    flexDirection: 'row',
    alignItems: 'center',
    ...Elevation.card,
    borderWidth: 1,
    borderColor: Colors.borderLight,
  },
  deviceAvatar: {
    width: 54,
    height: 54,
    borderRadius: Radii.lg,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: Spacing.md,
    position: 'relative',
  },
  deviceAvatarText: {fontSize: 20, fontWeight: '800'},
  deviceAvatarDot: {position: 'absolute', bottom: 1, right: 1, width: 12, height: 12, borderRadius: 6, borderWidth: 2, borderColor: Colors.surface},
  deviceInfo: {flex: 1},
  deviceName: {fontSize: 16, fontWeight: '800', color: Colors.textPrimary, marginBottom: 6},
  deviceRow: {flexDirection: 'row', alignItems: 'center', gap: 8},
  statusPill: {borderRadius: Radii.pill, paddingHorizontal: 8, paddingVertical: 3},
  statusPillText: {fontSize: 12, fontWeight: '700'},
  batteryText: {fontSize: 12, color: Colors.textTertiary, fontWeight: '600'},
  liveAppBadge: {flexDirection: 'row', alignItems: 'center', marginTop: 6, backgroundColor: Colors.primarySurface, borderRadius: 8, paddingHorizontal: 8, paddingVertical: 3, alignSelf: 'flex-start'},
  liveAppDot: {width: 5, height: 5, borderRadius: 3, backgroundColor: Colors.primary, marginRight: 5},
  liveAppText: {fontSize: 11, color: Colors.primary, fontWeight: '700'},
  chevronWrap: {width: 30, height: 30, borderRadius: Spacing.sm, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center'},
  chevron: {fontSize: 18, color: Colors.primary, fontWeight: '700'},

  // Empty
  emptyWrap: {alignItems: 'center', paddingHorizontal: Spacing.xxl, paddingTop: 60},
  emptyIllustration: {width: 100, height: 100, borderRadius: 50, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginBottom: Spacing.lg},
  emptyTitle: {fontSize: 20, fontWeight: '800', color: Colors.textPrimary, textAlign: 'center'},
  emptyBody: {fontSize: 14, color: Colors.textTertiary, textAlign: 'center', marginTop: 8, lineHeight: 22},
  pairBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.primaryContainer,
    borderRadius: Radii.lg,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
    ...Elevation.card,
  },
  pairBtnOutline: {
    backgroundColor: Colors.surface,
    borderWidth: 1.5,
    borderColor: Colors.primary,
  },
  pairBtnIcon: {fontSize: 16, marginRight: Spacing.sm},
  pairBtnText: {color: '#fff', fontWeight: '800', fontSize: 15},
  pairBtnTextOutline: {color: Colors.primary},
  pairMoreFooter: {
    paddingHorizontal: Spacing.base,
    paddingTop: Spacing.lg,
    paddingBottom: Spacing.md,
    alignItems: 'center',
  },
  pairMoreHint: {
    fontSize: 13,
    color: Colors.textTertiary,
    textAlign: 'center',
    marginBottom: Spacing.md,
  },

  // Tab Bar — 24px padding, pill active state
  tabBar: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: Colors.outlineVariant,
    paddingBottom: Spacing.sm,
    paddingTop: Spacing.md,
    paddingHorizontal: Layout.componentInternal,
    borderTopLeftRadius: Radii.xl,
    borderTopRightRadius: Radii.xl,
    ...Elevation.cardHigh,
  },
  tabItem: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Layout.gutter,
    paddingVertical: Spacing.half,
    borderRadius: Radii.pill,
    minHeight: Layout.touchTargetMin,
  },
  tabItemActive: {
    backgroundColor: Colors.accentLight,
  },
  tabIconRow: {position: 'relative', alignItems: 'center'},
  tabIcon: {fontSize: 22, color: Colors.textTertiary},
  tabIconActive: {color: Colors.onSecondaryContainer},
  tabLabel: {...Typography.smallLabel, marginTop: 2},
  tabLabelActive: {color: Colors.onSecondaryContainer, fontWeight: '600'},
  badge: {position: 'absolute', top: -5, right: -10, backgroundColor: Colors.danger, borderRadius: 10, minWidth: 17, height: 17, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 3},
  badgeText: {color: '#fff', fontSize: 10, fontWeight: '800'},
});
