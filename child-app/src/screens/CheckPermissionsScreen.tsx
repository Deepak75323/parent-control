import React, {useCallback, useRef, useState} from 'react';
import {
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
import {grantScreenCaptureWithAlerts} from '../services/grantScreenCapture';

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
    try {
      await perm.grant();
      onGranted();
    } finally {
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
  const [perms, setPerms] = useState<PermStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const checkSeq = useRef(0);
  const grantInFlight = useRef(false);

  const check = useCallback(async () => {
    const seq = ++checkSeq.current;
    const firstLoad = perms.length === 0;
    if (firstLoad) {
      setLoading(true);
    }
    try {
      const [accessibility, camera, deviceAdmin, usageStats, notification, location, mic, overlay, battery] =
        await Promise.all([
          nativeModules.isAccessibilityEnabled(),
          nativeModules.isCameraGranted(),
          nativeModules.isDeviceAdminActive(),
          nativeModules.isUsageStatsGranted(),
          nativeModules.isNotificationListenerEnabled(),
          nativeModules.isLocationGranted(),
          nativeModules.isMicGranted(),
          nativeModules.isOverlayGranted(),
          nativeModules.isBatteryOptimizationExempt(),
        ]);

      if (seq !== checkSeq.current) {
        return;
      }

      const screen =
        (await nativeModules.isMediaProjectionGranted()) && accessibility;

      if (seq !== checkSeq.current) {
        return;
      }

      setPerms([
        {
          key: 'accessibility',
          icon: '🛡️',
          title: 'Accessibility',
          features: 'App blocking, screen time, auto-tap Share screen for live view',
          granted: accessibility,
          grant: nativeModules.openAccessibilitySettings,
        },
        {
          key: 'camera',
          icon: '📷',
          title: 'Camera',
          features: 'Remote camera and snapshots',
          granted: camera,
          grant: nativeModules.requestCameraAndMicPermissions,
        },
        {
          key: 'mic',
          icon: '🎙️',
          title: 'Microphone',
          features: 'One-way audio and ambient recording',
          granted: mic,
          grant: nativeModules.requestCameraAndMicPermissions,
        },
        {
          key: 'screen',
          icon: '📺',
          title: 'Screen capture',
          features: 'Screen mirroring and live view',
          granted: screen,
          grant: async () => {
            grantInFlight.current = true;
            try {
              await grantScreenCaptureWithAlerts();
            } finally {
              grantInFlight.current = false;
              void check();
            }
          },
        },
        {
          key: 'location',
          icon: '📍',
          title: 'Location',
          features: 'Live location and geofence alerts',
          granted: location,
          grant: nativeModules.requestLocationPermission,
        },
        {
          key: 'notification',
          icon: '🔔',
          title: 'Notification access',
          features: 'App notifications and social app detection',
          granted: notification,
          grant: nativeModules.openNotificationListenerSettings,
        },
        {
          key: 'usageStats',
          icon: '📊',
          title: 'Usage access',
          features: 'App usage time and screen time limits',
          granted: usageStats,
          grant: nativeModules.openUsageStatsSettings,
        },
        {
          key: 'overlay',
          icon: '🪟',
          title: 'Display over other apps',
          features: 'App block overlay and time-limit screen',
          granted: overlay,
          grant: nativeModules.openOverlaySettings,
        },
        {
          key: 'battery',
          icon: '🔋',
          title: 'Battery optimization',
          features: 'Background monitoring after reboot',
          granted: battery,
          grant: nativeModules.openBatteryOptimizationSettings,
        },
        {
          key: 'deviceAdmin',
          icon: '👤',
          title: 'Device administrator',
          features: 'Anti-tamper and screen lock control',
          granted: deviceAdmin,
          grant: async () => {
            if (await nativeModules.isDeviceAdminActive()) {
              await nativeModules.openDeviceAdminSettings();
            } else {
              await nativeModules.requestDeviceAdmin();
            }
          },
        },
      ]);
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
        <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
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
