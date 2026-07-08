import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  Alert,
  Animated,
  View,
  Text,
  StyleSheet,
  Pressable,
  ScrollView,
  StatusBar,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useFocusEffect} from '@react-navigation/native';
import {ChildStackParamList, usePairingComplete} from './RootNavigator';
import {runSyncCycle} from '../services/syncService';
import {slog} from '../services/streamDebug';
import {nativeModules} from '../nativeModules';
import {grantScreenCaptureWithAlerts} from '../services/grantScreenCapture';
import {ConfirmModal} from '../components/AppScreen';
import {useFadeSlideIn, usePulse, useOpacityPulse, usePressScale} from '../utils/animations';

type Props = NativeStackScreenProps<ChildStackParamList, 'ServiceStatus'>;
type ConfirmAction = 'repair';

function PulseRing({color}: {color: string}) {
  const pulse = usePulse(0.85, 1.15, 1600);
  const opacity = useOpacityPulse(0.2, 0.5, 1600);
  return (
    <Animated.View
      style={[styles.pulseRing, {borderColor: color, transform: pulse, opacity}]}
    />
  );
}

function AnimCard({children, delay = 0, style}: {children: React.ReactNode; delay?: number; style?: object}) {
  const anim = useFadeSlideIn(delay);
  return (
    <Animated.View style={[styles.card, style, anim]}>
      {children}
    </Animated.View>
  );
}

function MenuButton({icon, label, subtitle, onPress, danger}: {icon: string; label: string; subtitle: string; onPress: () => void; danger?: boolean}) {
  const press = usePressScale(0.97);
  return (
    <Animated.View style={{transform: press.scale}}>
      <Pressable
        style={({pressed}) => [styles.menuBtn, pressed && {opacity: 0.9}]}
        onPress={onPress}
        onPressIn={press.onPressIn}
        onPressOut={press.onPressOut}>
        <View style={[styles.menuIconWrap, danger && styles.menuIconDanger]}>
          <Text style={styles.menuIcon}>{icon}</Text>
        </View>
        <View style={styles.menuText}>
          <Text style={[styles.menuLabel, danger && {color: '#ef4444'}]}>{label}</Text>
          <Text style={styles.menuSub}>{subtitle}</Text>
        </View>
        <Text style={[styles.menuChevron, danger && {color: '#ef4444'}]}>›</Text>
      </Pressable>
    </Animated.View>
  );
}

export function ServiceStatusScreen({navigation}: Props) {
  const {markRePairing, pairedParentDisplayName} = usePairingComplete();
  const [lastSync, setLastSync] = useState<string>('—');
  const [parentName, setParentName] = useState<string | null>(pairedParentDisplayName);
  const [syncing, setSyncing] = useState(false);
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);
  const [busy, setBusy] = useState(false);
  const [screenCaptureOk, setScreenCaptureOk] = useState(true);
  const [grantBusy, setGrantBusy] = useState(false);

  // Hide App state
  const [launcherHidden, setLauncherHidden] = useState(false);
  const [hideBusy, setHideBusy] = useState(false);

  useEffect(() => {
    nativeModules.isLauncherHidden().then(h => setLauncherHidden(h)).catch(() => undefined);
  }, []);

  const toggleHideApp = useCallback(async () => {
    if (hideBusy) return;
    setHideBusy(true);
    try {
      const next = !launcherHidden;
      const ok = await nativeModules.setHideLauncher(next);
      if (ok) {
        setLauncherHidden(next);
        Alert.alert(
          next ? 'App hidden' : 'App visible',
          next
            ? 'The app icon is now hidden from the launcher. Open it again via Settings → Apps if needed.'
            : 'The app icon is now visible in the launcher.',
        );
      } else {
        Alert.alert('Not available', 'Device admin must be active to hide the app icon.');
      }
    } catch (e) {
      Alert.alert('Error', String(e));
    } finally {
      setHideBusy(false);
    }
  }, [launcherHidden, hideBusy]);

  const heroAnim = useFadeSlideIn(0);
  const syncAnim = usePressScale(0.95);
  const pulseOpacity = useOpacityPulse(0.6, 1, 800);
  const rotateAnim = useRef(new Animated.Value(0)).current;

  useFocusEffect(
    useCallback(() => {
      const refreshScreenCapture = async () => {
        try {
          const [projection, accessibility] = await Promise.all([
            nativeModules.isMediaProjectionGranted(),
            nativeModules.isAccessibilityEnabled(),
          ]);
          setScreenCaptureOk(projection && accessibility);
        } catch {
          setScreenCaptureOk(false);
        }
      };
      void refreshScreenCapture();
      const poll = setInterval(() => void refreshScreenCapture(), 1500);
      return () => clearInterval(poll);
    }, []),
  );

  const grantLiveView = async () => {
    if (grantBusy) return;
    setGrantBusy(true);
    try {
      const granted = await grantScreenCaptureWithAlerts();
      if (granted) {
        setScreenCaptureOk(true);
      }
    } finally {
      setGrantBusy(false);
    }
  };

  const syncNow = async () => {
    if (syncing) return;
    setSyncing(true);
    // Spin animation
    rotateAnim.setValue(0);
    Animated.loop(
      Animated.timing(rotateAnim, {toValue: 1, duration: 900, useNativeDriver: true}),
    ).start();
    try {
      await runSyncCycle();
      setLastSync(new Date().toLocaleTimeString());
    } finally {
      setSyncing(false);
      rotateAnim.stopAnimation();
    }
  };

  const runConfirm = async () => {
    if (!confirmAction || busy) return;
    setBusy(true);
    try {
      slog('APP', 're-pair confirmed');
      await markRePairing();
      setConfirmAction(null);
    } catch (e) {
      slog('APP', 'confirm action failed', String(e));
    } finally {
      setBusy(false);
    }
  };

  const spin = rotateAnim.interpolate({inputRange: [0, 1], outputRange: ['0deg', '360deg']});

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="light-content" backgroundColor="#4f46e5" />
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scroll}>

        {/* Hero Section */}
        <View style={styles.hero}>
          {/* Background decorative circles */}
          <View style={styles.heroBg1} />
          <View style={styles.heroBg2} />

          <Animated.View style={[styles.heroContent, heroAnim]}>
            {/* Pulse ring + status dot */}
            <View style={styles.statusOrb}>
              <PulseRing color="#a5f3fc" />
              <View style={styles.orbCore}>
                <Animated.Text style={[styles.orbEmoji, {opacity: pulseOpacity}]}>🛡️</Animated.Text>
              </View>
            </View>

            <Text style={styles.heroTitle}>Monitoring Active</Text>
            <Text style={styles.heroSub}>Your device is protected</Text>

            <View style={styles.parentChip}>
              <View style={styles.parentDot} />
              <Text style={styles.parentChipText}>Protection enabled</Text>
            </View>
          </Animated.View>
        </View>

        {!screenCaptureOk ? (
          <AnimCard delay={80} style={styles.alertCard}>
            <Text style={styles.alertTitle}>Screen capture required</Text>
            <Text style={styles.alertBody}>
              Live screen sharing needs a one-time grant. Tap below — we open Accessibility first, then Share screen is approved automatically.
            </Text>
            <Pressable
              style={({pressed}) => [styles.alertBtn, pressed && {opacity: 0.9}, grantBusy && {opacity: 0.6}]}
              onPress={() => void grantLiveView()}
              disabled={grantBusy}>
              <Text style={styles.alertBtnText}>
                {grantBusy ? 'Waiting…' : 'Enable live view'}
              </Text>
            </Pressable>
          </AnimCard>
        ) : null}

        {/* Sync card */}
        <AnimCard delay={100} style={styles.syncCard}>
          <View style={styles.syncHeader}>
            <View style={styles.syncIconWrap}>
              <Text style={styles.syncIcon}>⚡</Text>
            </View>
            <View style={styles.syncInfo}>
              <Text style={styles.syncTitle}>What's syncing</Text>
              <Text style={styles.syncLastSync}>Last sync: {lastSync}</Text>
            </View>
            <Animated.View style={{transform: [{rotate: syncing ? spin : '0deg'}]}}>
              <Pressable
                style={[styles.syncBtn, syncing && styles.syncBtnActive]}
                onPress={syncNow}
                disabled={syncing}>
                <Text style={styles.syncBtnText}>{syncing ? '↻' : '↻'}</Text>
              </Pressable>
            </Animated.View>
          </View>

          <View style={styles.syncItems}>
            {[
              {icon: '🔔', text: 'Notifications & app usage'},
              {icon: '📍', text: 'Location every 5 minutes'},
              {icon: '🔁', text: 'Restarts automatically after reboot'},
            ].map((item, i) => (
              <View key={i} style={styles.syncItem}>
                <Text style={styles.syncItemIcon}>{item.icon}</Text>
                <Text style={styles.syncItemText}>{item.text}</Text>
              </View>
            ))}
          </View>
        </AnimCard>

        {/* Settings section */}
        <AnimCard delay={200}>
          <Text style={styles.sectionLabel}>Settings</Text>
          <MenuButton
            icon="🔐"
            label="Grant Permissions"
            subtitle="See which permissions are on and grant any that are missing"
            onPress={() => navigation.navigate('CheckPermissions')}
          />
          <View style={styles.menuDivider} />
          <MenuButton
            icon="🛠️"
            label="Diagnostics"
            subtitle="WebRTC, screen capture, permissions debug view"
            onPress={() => navigation.navigate('DebugDashboard')}
          />
          <View style={styles.menuDivider} />
          <MenuButton
            icon="🔒"
            label="Parent PIN"
            subtitle="Change unlock PIN for parent access"
            onPress={() => navigation.navigate('PinVerification')}
          />
          <View style={styles.menuDivider} />
          <MenuButton
            icon={launcherHidden ? '👁️' : '🫥'}
            label={launcherHidden ? 'Show app icon' : 'Hide app icon'}
            subtitle={
              launcherHidden
                ? 'Make this app visible in the launcher again'
                : 'Remove this app from the launcher so the child cannot find it'
            }
            onPress={() => void toggleHideApp()}
          />
          <View style={styles.menuDivider} />
          <MenuButton
            icon="🔗"
            label="Re-pair with new code"
            subtitle="Link to a different parent account"
            onPress={() => setConfirmAction('repair')}
          />
        </AnimCard>

        {/* Version */}
        <AnimCard delay={300} style={styles.versionCard}>
          <Text style={styles.versionText}>Parental Control · Child Monitor v1.0</Text>
        </AnimCard>

      </ScrollView>

      <ConfirmModal
        visible={confirmAction !== null}
        title="Re-pair device?"
        body="Generate a new code in the parent app, then enter it on the next screen."
        confirmLabel="Continue"
        loading={busy}
        onCancel={() => !busy && setConfirmAction(null)}
        onConfirm={runConfirm}
      />
    </SafeAreaView>
  );
}

const PURPLE = '#4f46e5';
const PURPLE_DARK = '#3730a3';
const PURPLE_LIGHT = '#818cf8';

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#f0f0ff'},
  scroll: {paddingBottom: 32},

  // Hero
  hero: {
    backgroundColor: PURPLE,
    paddingTop: 32,
    paddingBottom: 48,
    alignItems: 'center',
    overflow: 'hidden',
    position: 'relative',
  },
  heroBg1: {
    position: 'absolute',
    width: 280,
    height: 280,
    borderRadius: 140,
    backgroundColor: PURPLE_DARK,
    opacity: 0.4,
    top: -80,
    right: -60,
  },
  heroBg2: {
    position: 'absolute',
    width: 200,
    height: 200,
    borderRadius: 100,
    backgroundColor: PURPLE_LIGHT,
    opacity: 0.12,
    bottom: -40,
    left: -50,
  },
  heroContent: {alignItems: 'center'},

  statusOrb: {
    width: 110,
    height: 110,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 20,
  },
  pulseRing: {
    position: 'absolute',
    width: 100,
    height: 100,
    borderRadius: 50,
    borderWidth: 2,
  },
  orbCore: {
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: 'rgba(255,255,255,0.15)',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.3)',
  },
  orbEmoji: {fontSize: 32},

  heroTitle: {fontSize: 24, fontWeight: '800', color: '#fff', letterSpacing: -0.3},
  heroSub: {fontSize: 14, color: '#c7d2fe', marginTop: 4, fontWeight: '500'},

  parentChip: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 7,
    marginTop: 16,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
  },
  parentDot: {width: 7, height: 7, borderRadius: 4, backgroundColor: '#4ade80', marginRight: 7},
  parentChipText: {color: '#c7d2fe', fontSize: 13},
  parentChipName: {color: '#fff', fontSize: 13, fontWeight: '700'},

  // Cards
  card: {
    backgroundColor: '#fff',
    marginHorizontal: 16,
    marginTop: 14,
    borderRadius: 20,
    padding: 18,
    shadowColor: '#4f46e5',
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 4,
  },

  // Sync card
  syncCard: {},
  alertCard: {
    backgroundColor: '#FEF3C7',
    borderColor: '#FCD34D',
    borderWidth: 1,
  },
  alertTitle: {fontSize: 16, fontWeight: '800', color: '#92400E', marginBottom: 6},
  alertBody: {fontSize: 13, color: '#78350F', lineHeight: 19, marginBottom: 14},
  alertBtn: {
    backgroundColor: '#D97706',
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
  },
  alertBtnText: {color: '#fff', fontWeight: '700', fontSize: 14},
  syncHeader: {flexDirection: 'row', alignItems: 'center', marginBottom: 16},
  syncIconWrap: {width: 42, height: 42, borderRadius: 12, backgroundColor: '#ede9fe', alignItems: 'center', justifyContent: 'center', marginRight: 12},
  syncIcon: {fontSize: 20},
  syncInfo: {flex: 1},
  syncTitle: {fontSize: 15, fontWeight: '700', color: '#0f172a'},
  syncLastSync: {fontSize: 12, color: '#94a3b8', marginTop: 2},
  syncBtn: {width: 38, height: 38, borderRadius: 12, backgroundColor: '#ede9fe', alignItems: 'center', justifyContent: 'center'},
  syncBtnActive: {backgroundColor: '#4f46e5'},
  syncBtnText: {fontSize: 20, color: '#4f46e5'},
  syncItems: {gap: 10},
  syncItem: {flexDirection: 'row', alignItems: 'center'},
  syncItemIcon: {fontSize: 16, width: 26},
  syncItemText: {fontSize: 13, color: '#475569', flex: 1},

  // Menu
  sectionLabel: {fontSize: 12, fontWeight: '800', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 12},
  menuBtn: {flexDirection: 'row', alignItems: 'center', paddingVertical: 10},
  menuIconWrap: {width: 42, height: 42, borderRadius: 12, backgroundColor: '#ede9fe', alignItems: 'center', justifyContent: 'center', marginRight: 14},
  menuIconDanger: {backgroundColor: '#fee2e2'},
  menuIcon: {fontSize: 20},
  menuText: {flex: 1},
  menuLabel: {fontSize: 15, fontWeight: '700', color: '#0f172a'},
  menuSub: {fontSize: 12, color: '#94a3b8', marginTop: 2},
  menuChevron: {fontSize: 22, color: '#cbd5e1'},
  menuDivider: {height: 0.5, backgroundColor: '#f1f5f9', marginVertical: 2},

  // Version
  versionCard: {alignItems: 'center', paddingVertical: 12},
  versionText: {fontSize: 12, color: '#94a3b8'},

});
