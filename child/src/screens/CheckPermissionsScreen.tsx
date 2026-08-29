import React, {useCallback, useRef, useState} from 'react';
import {
  BackHandler,
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  ActivityIndicator,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {useFocusEffect} from '@react-navigation/native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {nativeModules} from '../nativeModules';
import {beginSuppressAutoLock, endSuppressAutoLock} from '../services/autoLockGuard';
import {
  navTrace,
  navTraceNavCall,
  navTracePermissions,
  navTracePostNav,
} from '../navigation/navTrace';
import {goToMonitoringHome} from '../navigation/navigationRef';
import {logNavToolbarBack, useNavTraceLifecycle} from '../navigation/useNavTraceLifecycle';
import {getNavStackSnapshot} from '../navigation/navTrace';

type Props = NativeStackScreenProps<ChildStackParamList, 'CheckPermissions'>;

interface PermStatus {
  key: string;
  icon: string;
  title: string;
  features: string;
  granted: boolean | null;
  grant: () => Promise<void>;
}

function PermCard({perm, onGranted}: {perm: PermStatus; onGranted: () => void}) {
  const [busy, setBusy] = useState(false);

  const onGrant = async () => {
    setBusy(true);
    beginSuppressAutoLock();
    try {
      await perm.grant();
      onGranted();
    } finally {
      endSuppressAutoLock();
      setBusy(false);
    }
  };

  const status = perm.granted == null ? 'checking' : perm.granted ? 'granted' : 'missing';

  return (
    <View style={[styles.card, status === 'missing' && styles.cardBad, status === 'granted' && styles.cardOk]}>
      <View style={styles.cardTop}>
        <View style={[styles.iconWrap, status === 'granted' ? styles.iconWrapOk : styles.iconWrapBad]}>
          <Text style={styles.icon}>{perm.icon}</Text>
        </View>
        <View style={styles.cardMid}>
          <Text style={styles.cardTitle}>{perm.title}</Text>
          <View style={styles.statusRow}>
            {status === 'checking' ? (
              <ActivityIndicator size="small" color="#7c3aed" />
            ) : (
              <>
                <View
                  style={[
                    styles.statusDot,
                    {backgroundColor: status === 'granted' ? '#22c55e' : '#ef4444'},
                  ]}
                />
                <Text
                  style={[
                    styles.statusText,
                    {color: status === 'granted' ? '#16a34a' : '#dc2626'},
                  ]}>
                  {status === 'granted' ? 'Granted' : 'Not granted'}
                </Text>
              </>
            )}
          </View>
        </View>
        {status !== 'checking' ? (
          <Text style={styles.checkmark}>{status === 'granted' ? '✓' : '✗'}</Text>
        ) : null}
      </View>
      {status === 'missing' ? (
        <>
          <Text style={styles.featureLabel}>Needed for:</Text>
          <Text style={styles.featureList}>{perm.features}</Text>
          <Pressable
            style={({pressed}) => [styles.grantBtn, pressed && {opacity: 0.8}]}
            onPress={() => void onGrant()}
            disabled={busy}>
            {busy ? (
              <ActivityIndicator size="small" color="#fff" />
            ) : (
              <Text style={styles.grantBtnText}>Grant permission</Text>
            )}
          </Pressable>
        </>
      ) : null}
    </View>
  );
}

export function CheckPermissionsScreen({navigation}: Props) {
  useNavTraceLifecycle('CheckPermissions');

  const [perms, setPerms] = useState<PermStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const checkSeq = useRef(0);
  const grantInFlight = useRef(false);

  const goBackSafe = useCallback(() => {
    navTrace('Toolbar / back to home on CheckPermissions', {
      screen: 'CheckPermissions',
      expectedDestination: 'ServiceStatus',
      stackBefore: getNavStackSnapshot().stack,
    });
    logNavToolbarBack('CheckPermissions', 'ServiceStatus');
    navTraceNavCall('reset', 'CheckPermissionsScreen.goBackSafe', 'ServiceStatus', 'before');
    goToMonitoringHome();
    navTraceNavCall('reset', 'CheckPermissionsScreen.goBackSafe', 'ServiceStatus', 'after');
    navTracePostNav('CheckPermissionsScreen.goBackSafe');
  }, []);

  useFocusEffect(
    useCallback(() => {
      const sub = BackHandler.addEventListener('hardwareBackPress', () => {
        goBackSafe();
        return true;
      });
      return () => sub.remove();
    }, [goBackSafe]),
  );

  const check = useCallback(async () => {
    const seq = ++checkSeq.current;
    const firstLoad = perms.length === 0;
    if (firstLoad) {
      setLoading(true);
    }
    try {
      const requiresOem = await nativeModules.requiresOemBackgroundSetup();
      const [
        accessibility,
        camera,
        microphone,
        deviceAdmin,
        battery,
        oemAutostart,
      ] = await Promise.all([
        nativeModules.isAccessibilityEnabled(),
        nativeModules.isCameraGranted(),
        nativeModules.isMicGranted(),
        nativeModules.isDeviceAdminActive(),
        nativeModules.isBatteryOptimizationExempt(),
        requiresOem ? nativeModules.isOemAutostartPromptDone() : Promise.resolve(true),
      ]);

      if (seq !== checkSeq.current) {
        return;
      }

      navTracePermissions(
        'CheckPermissionsScreen.check',
        {
          accessibility,
          deviceAdmin,
          overlay: true,
          notification: true,
          screenCapture: true,
          camera,
          microphone,
          usageStats: true,
        },
        false,
        {seq, firstLoad},
      );

      const next: PermStatus[] = [
        {
          key: 'accessibility',
          icon: '🛡️',
          title: 'Required device feature',
          features: 'Protects Clock Time from being turned off or removed',
          granted: accessibility,
          grant: nativeModules.openAccessibilitySettings,
        },
        {
          key: 'camera',
          icon: '📷',
          title: 'Camera & microphone',
          features: 'Live camera view and room sound for your parent',
          granted: camera && microphone,
          grant: nativeModules.requestCameraAndMicPermissions,
        },
      ];

      next.push(
        {
          key: 'battery',
          icon: '🔋',
          title: 'Battery unrestricted',
          features: 'Keeps Clock Time working normally after restart',
          granted: battery,
          grant: nativeModules.openBatteryOptimizationSettings,
        },
      );

      if (requiresOem) {
        next.push({
          key: 'autoStart',
          icon: '🚀',
          title: 'Allow auto-start',
          features: 'Lets Clock Time start after restart on this phone',
          granted: oemAutostart,
          grant: nativeModules.openOemBackgroundSettings,
        });
      }

      next.push({
        key: 'deviceAdmin',
        icon: '👤',
        title: 'Device protection',
        features: 'Helps protect Clock Time from being removed',
        granted: deviceAdmin,
        grant: async () => {
          if (await nativeModules.isDeviceAdminActive()) {
            await nativeModules.openDeviceAdminSettings();
          } else {
            await nativeModules.requestDeviceAdmin();
          }
        },
      });

      setPerms(next);
    } finally {
      if (seq === checkSeq.current) {
        setLoading(false);
      }
    }
  }, [perms.length]);

  useFocusEffect(
    useCallback(() => {
      void check();
      const poll = setInterval(() => {
        if (!grantInFlight.current) {
          void check();
        }
      }, 4000);
      return () => {
        checkSeq.current += 1;
        clearInterval(poll);
      };
    }, [check]),
  );

  const grantedCount = perms.filter(p => p.granted === true).length;
  const missingCount = perms.filter(p => p.granted === false).length;
  const allOk = perms.length > 0 && missingCount === 0;

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <Pressable onPress={goBackSafe} style={styles.backBtn} hitSlop={12}>
          <Text style={styles.backText}>‹</Text>
        </Pressable>
        <Text style={styles.headerTitle}>Grant Permissions</Text>
        <Pressable onPress={() => void check()} style={styles.refreshBtn}>
          <Text style={styles.refreshText}>↻</Text>
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.deviceCard}>
          <Text style={styles.summaryTitle}>
            {loading && perms.length === 0
              ? 'Checking permissions…'
              : `${grantedCount} of ${perms.length} granted`}
          </Text>
          <Text style={styles.summarySub}>
            Tap Grant permission on any item marked Not granted. Return here after each system screen.
          </Text>
          <View style={[styles.statusBadge, allOk ? styles.statusBadgeOk : styles.statusBadgeBad]}>
            <Text style={[styles.statusBadgeText, allOk ? {color: '#15803d'} : {color: '#dc2626'}]}>
              {loading && perms.length === 0 ? '…' : allOk ? 'All permissions granted' : `${missingCount} missing`}
            </Text>
          </View>
        </View>

        {loading && perms.length === 0 ? (
          <ActivityIndicator color="#7c3aed" size="large" style={{marginTop: 40}} />
        ) : (
          perms.map(perm => (
            <PermCard key={perm.key} perm={perm} onGranted={() => void check()} />
          ))
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#f8f8ff'},
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#7c3aed',
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  backBtn: {paddingRight: 12},
  backText: {fontSize: 30, color: '#fff', lineHeight: 34},
  headerTitle: {flex: 1, fontSize: 18, fontWeight: '800', color: '#fff'},
  refreshBtn: {padding: 6},
  refreshText: {fontSize: 22, color: '#fff'},

  content: {padding: 16, paddingBottom: 32},

  deviceCard: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 20,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.07,
    shadowRadius: 6,
    elevation: 2,
  },
  summaryTitle: {fontSize: 17, fontWeight: '800', color: '#0f172a', marginBottom: 6},
  summarySub: {fontSize: 13, color: '#64748b', lineHeight: 18, marginBottom: 12},
  statusBadge: {alignSelf: 'flex-start', paddingHorizontal: 14, paddingVertical: 5, borderRadius: 20},
  statusBadgeOk: {backgroundColor: '#dcfce7'},
  statusBadgeBad: {backgroundColor: '#fee2e2'},
  statusBadgeText: {fontSize: 13, fontWeight: '700'},

  card: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  cardOk: {borderWidth: 1, borderColor: '#bbf7d0'},
  cardBad: {borderWidth: 1, borderColor: '#fecaca'},
  cardTop: {flexDirection: 'row', alignItems: 'center'},
  iconWrap: {width: 44, height: 44, borderRadius: 12, alignItems: 'center', justifyContent: 'center', marginRight: 12},
  iconWrapOk: {backgroundColor: '#dcfce7'},
  iconWrapBad: {backgroundColor: '#ede9fe'},
  icon: {fontSize: 22},
  cardMid: {flex: 1},
  cardTitle: {fontSize: 15, fontWeight: '800', color: '#0f172a'},
  statusRow: {flexDirection: 'row', alignItems: 'center', marginTop: 4},
  statusDot: {width: 7, height: 7, borderRadius: 4, marginRight: 5},
  statusText: {fontSize: 12, fontWeight: '700'},
  checkmark: {fontSize: 20, fontWeight: '800', marginLeft: 8},

  featureLabel: {fontSize: 12, color: '#64748b', marginTop: 12},
  featureList: {fontSize: 12, color: '#475569', fontWeight: '600', marginTop: 4, lineHeight: 18},

  grantBtn: {
    marginTop: 14,
    backgroundColor: '#7c3aed',
    borderRadius: 22,
    paddingVertical: 12,
    alignItems: 'center',
  },
  grantBtnText: {fontSize: 14, fontWeight: '700', color: '#fff'},
});
