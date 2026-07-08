import React, {useCallback, useEffect, useState} from 'react';
import {Alert, Animated, Pressable, ScrollView, StyleSheet, Text, View} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {useAuthStore} from '../store/authStore';
import {deviceApi} from '../services/api';
import {RootStackParamList} from '../navigation/RootNavigator';
import {navigateToPairingFlow} from '../navigation/pairingNavigation';
import {Colors, Spacing, Radii, Elevation, Typography} from '../theme/design';
import {useFadeSlideIn, usePressScale} from '../utils/animations';

type Nav = NativeStackNavigationProp<RootStackParamList>;

function MenuRow({icon, label, sub, value, onPress, danger}: {
  icon: string; label: string; sub?: string; value?: string; onPress?: () => void; danger?: boolean;
}) {
  const press = usePressScale(0.98);
  return (
    <Animated.View style={{transform: press.scale}}>
      <Pressable
        style={styles.menuRow}
        onPress={onPress}
        onPressIn={press.onPressIn}
        onPressOut={press.onPressOut}
        disabled={!onPress}>
        <View style={[styles.menuIconWrap, danger && styles.menuIconDanger]}>
          <Text style={{fontSize: 18}}>{icon}</Text>
        </View>
        <View style={styles.menuText}>
          <Text style={[styles.menuLabel, danger && {color: Colors.danger}]}>{label}</Text>
          {sub ? <Text style={styles.menuSub}>{sub}</Text> : null}
        </View>
        {value ? <Text style={styles.menuValue}>{value}</Text> : null}
        {onPress ? <Text style={[styles.menuChevron, danger && {color: Colors.danger}]}>›</Text> : null}
      </Pressable>
    </Animated.View>
  );
}

function SectionGroup({children}: {children: React.ReactNode}) {
  return <View style={styles.group}>{children}</View>;
}

export function ProfileScreen({navigation}: {navigation: Nav}) {
  const {email, logout} = useAuthStore();
  const [deviceCount, setDeviceCount] = useState<number | null>(null);
  const headerAnim = useFadeSlideIn(0);
  const contentAnim = useFadeSlideIn(120);

  useEffect(() => {
    deviceApi.list().then(r => setDeviceCount(r.data.data.length)).catch(() => {});
  }, []);

  const name = email?.split('@')[0] ?? 'Parent';
  const initials = name.slice(0, 2).toUpperCase();

  const confirmLogout = () => Alert.alert('Sign out', 'Are you sure?', [
    {text: 'Cancel', style: 'cancel'},
    {text: 'Sign out', style: 'destructive', onPress: logout},
  ]);

  return (
    <SafeAreaView style={styles.root} edges={['top']}>
      {/* Profile Hero */}
      <Animated.View style={[styles.hero, headerAnim]}>
        <View style={styles.heroBubble} />
        <View style={styles.heroContent}>
          <View style={styles.avatarWrap}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>{initials}</Text>
            </View>
            <View style={styles.avatarBadge}><Text style={{fontSize: 14}}>👑</Text></View>
          </View>
          <View style={styles.profileInfo}>
            <Text style={styles.profileName}>{name}</Text>
            <Text style={styles.profileEmail}>{email ?? '—'}</Text>
            <View style={styles.rolePill}>
              <Text style={styles.rolePillText}>Parent Account</Text>
            </View>
          </View>
          <Pressable style={styles.settingsBtn} onPress={() => navigation.navigate('Settings')}>
            <Text style={{fontSize: 20}}>⚙️</Text>
          </Pressable>
        </View>
      </Animated.View>

      <Animated.View style={[{flex: 1}, contentAnim]}>
        <ScrollView contentContainerStyle={styles.scroll}>

          {/* Subscription Banner */}
          <Pressable style={styles.subBanner} onPress={() => navigation.navigate('Subscription')}>
            <View style={styles.subBannerLeft}>
              <Text style={styles.subBannerIcon}>👑</Text>
              <View>
                <Text style={styles.subBannerTitle}>Upgrade to Premium</Text>
                <Text style={styles.subBannerSub}>Unlock all features · 7-day free trial</Text>
              </View>
            </View>
            <View style={styles.subBannerBtn}>
              <Text style={styles.subBannerBtnText}>Try Free</Text>
            </View>
          </Pressable>

          {/* Stats row */}
          <View style={styles.statsRow}>
            <View style={styles.statCard}>
              <Text style={styles.statNum}>{deviceCount ?? '—'}</Text>
              <Text style={styles.statLabel}>Devices</Text>
            </View>
            <View style={styles.statCard}>
              <Text style={styles.statNum}>0</Text>
              <Text style={styles.statLabel}>Alerts today</Text>
            </View>
            <View style={styles.statCard}>
              <Text style={styles.statNum}>—</Text>
              <Text style={styles.statLabel}>Screen time avg</Text>
            </View>
          </View>

          {/* Account section */}
          <Text style={styles.groupLabel}>ACCOUNT</Text>
          <SectionGroup>
            <MenuRow icon="📱" label="My Devices" sub="Manage paired phones" value={deviceCount != null ? String(deviceCount) : '—'} onPress={() => void navigateToPairingFlow(navigation)} />
            <View style={styles.divider} />
            <MenuRow icon="📨" label="Time Requests" sub="Pending approvals" onPress={() => navigation.navigate('Requests')} />
            <View style={styles.divider} />
            <MenuRow icon="📋" label="Audit Log" sub="Recent activity" onPress={() => navigation.navigate('AuditLog')} />
          </SectionGroup>

          {/* Security */}
          <Text style={styles.groupLabel}>SECURITY</Text>
          <SectionGroup>
            <MenuRow icon="🔑" label="Child login password" sub="Same on every child phone" onPress={() => navigation.navigate('ChildLoginPassword')} />
            <View style={styles.divider} />
            <MenuRow icon="🔒" label="Parent PIN" sub="Unlock silent monitoring" onPress={() => navigation.navigate('ParentPin')} />
            <View style={styles.divider} />
            <MenuRow icon="💳" label="Subscription" sub="Plan & billing" onPress={() => navigation.navigate('Subscription')} />
          </SectionGroup>

          {/* App */}
          <Text style={styles.groupLabel}>APP</Text>
          <SectionGroup>
            <MenuRow icon="ℹ️" label="About" sub="Version 1.0.0" />
          </SectionGroup>

          {/* Sign out */}
          <SectionGroup>
            <MenuRow icon="🚪" label="Sign out" danger onPress={confirmLogout} />
          </SectionGroup>

          <View style={{height: Spacing.xxl}} />
        </ScrollView>
      </Animated.View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.background},

  hero: {
    backgroundColor: Colors.primary,
    paddingBottom: Spacing.xl,
    overflow: 'hidden',
    position: 'relative',
  },
  heroBubble: {position: 'absolute', width: 250, height: 250, borderRadius: 125, backgroundColor: Colors.primaryDark, opacity: 0.3, top: -80, right: -50},
  heroContent: {paddingHorizontal: Spacing.base, paddingTop: Spacing.base, flexDirection: 'row', alignItems: 'center'},
  avatarWrap: {position: 'relative', marginRight: Spacing.md},
  avatar: {width: 64, height: 64, borderRadius: 32, backgroundColor: 'rgba(255,255,255,0.2)', alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: 'rgba(255,255,255,0.3)'},
  avatarText: {fontSize: 24, fontWeight: '800', color: '#fff'},
  avatarBadge: {position: 'absolute', bottom: 0, right: -2, width: 22, height: 22, borderRadius: 11, backgroundColor: '#FCD34D', alignItems: 'center', justifyContent: 'center'},
  profileInfo: {flex: 1},
  profileName: {fontSize: 20, fontWeight: '800', color: '#fff', letterSpacing: -0.3},
  profileEmail: {fontSize: 13, color: Colors.accentLight, marginTop: 2},
  rolePill: {backgroundColor: 'rgba(255,255,255,0.15)', borderRadius: Radii.pill, paddingHorizontal: 10, paddingVertical: 4, alignSelf: 'flex-start', marginTop: 6},
  rolePillText: {fontSize: 11, fontWeight: '700', color: '#fff'},
  settingsBtn: {width: 38, height: 38, borderRadius: 12, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center'},

  scroll: {padding: Spacing.base, paddingBottom: Spacing.xxxl},

  // Subscription banner
  subBanner: {
    backgroundColor: '#FEF3C7',
    borderRadius: Radii.xl,
    padding: Spacing.base,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: Spacing.base,
    borderWidth: 1,
    borderColor: '#FDE68A',
    ...Elevation.card,
  },
  subBannerLeft: {flexDirection: 'row', alignItems: 'center', flex: 1},
  subBannerIcon: {fontSize: 26, marginRight: Spacing.md},
  subBannerTitle: {fontSize: 14, fontWeight: '800', color: '#92400E'},
  subBannerSub: {fontSize: 12, color: '#B45309', marginTop: 2},
  subBannerBtn: {backgroundColor: '#D97706', borderRadius: Radii.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm},
  subBannerBtnText: {fontSize: 13, fontWeight: '800', color: '#fff'},

  // Stats
  statsRow: {flexDirection: 'row', gap: Spacing.sm, marginBottom: Spacing.base},
  statCard: {flex: 1, backgroundColor: Colors.surface, borderRadius: Radii.xl, padding: Spacing.md, alignItems: 'center', ...Elevation.card, borderWidth: 1, borderColor: Colors.borderLight},
  statNum: {fontSize: 22, fontWeight: '900', color: Colors.primary, letterSpacing: -0.5},
  statLabel: {fontSize: 11, color: Colors.textTertiary, fontWeight: '600', marginTop: 2, textAlign: 'center'},

  // Group
  groupLabel: {...Typography.caption, marginBottom: Spacing.sm, marginTop: Spacing.sm},
  group: {backgroundColor: Colors.surface, borderRadius: Radii.xl, overflow: 'hidden', marginBottom: Spacing.base, ...Elevation.card, borderWidth: 1, borderColor: Colors.borderLight},
  divider: {height: 0.5, backgroundColor: Colors.borderLight, marginLeft: Spacing.base + 46 + Spacing.md},

  // Menu row
  menuRow: {flexDirection: 'row', alignItems: 'center', padding: Spacing.base},
  menuIconWrap: {width: 40, height: 40, borderRadius: Radii.md, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginRight: Spacing.md},
  menuIconDanger: {backgroundColor: Colors.dangerLight},
  menuText: {flex: 1},
  menuLabel: {fontSize: 15, fontWeight: '700', color: Colors.textPrimary},
  menuSub: {fontSize: 12, color: Colors.textTertiary, marginTop: 2},
  menuValue: {fontSize: 14, fontWeight: '700', color: Colors.textTertiary, marginRight: Spacing.sm},
  menuChevron: {fontSize: 20, color: Colors.border},
});
