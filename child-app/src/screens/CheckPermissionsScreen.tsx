import React, {useCallback, useState} from 'react';
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
import {flowLog} from '../services/flowLogger';
import {recoverScreenCaptureIfLost} from '../services/streamingPermissions';

type Props = NativeStackScreenProps<ChildStackParamList, 'CheckPermissions'>;

interface PermStatus {
  key: string;
  icon: string;
  title: string;
  features: string;
  granted: boolean | null;
  openSettings: () => Promise<void>;
}

function PermCard({perm}: {perm: PermStatus}) {
  const [opening, setOpening] = useState(false);

  const onGetHelp = async () => {
    setOpening(true);
    try {
      await perm.openSettings();
    } finally {
      setOpening(false);
    }
  };

  const status = perm.granted == null ? 'checking' : perm.granted ? 'ok' : 'abnormal';

  return (
    <View style={[styles.card, status === 'abnormal' && styles.cardBad, status === 'ok' && styles.cardOk]}>
      <View style={styles.cardTop}>
        <View style={[styles.iconWrap, status === 'ok' ? styles.iconWrapOk : styles.iconWrapBad]}>
          <Text style={styles.icon}>{perm.icon}</Text>
        </View>
        <View style={styles.cardMid}>
          <Text style={styles.cardTitle}>{perm.title}</Text>
          <View style={styles.statusRow}>
            {status === 'checking' ? (
              <ActivityIndicator size="small" color="#7c3aed" />
            ) : (
              <>
                <View style={[styles.statusDot, {backgroundColor: status === 'ok' ? '#22c55e' : '#ef4444'}]} />
                <Text style={[styles.statusText, {color: status === 'ok' ? '#16a34a' : '#dc2626'}]}>
                  {status === 'ok' ? 'Working normally' : 'Abnormal'}
                </Text>
              </>
            )}
          </View>
        </View>
        {status !== 'checking' ? (
          <Text style={styles.checkmark}>{status === 'ok' ? '✓' : '✗'}</Text>
        ) : null}
      </View>
      {status !== 'ok' && (
        <>
          <Text style={styles.featureLabel}>
            If this permission is not turned on, the following features will be unavailable:
          </Text>
          <Text style={styles.featureList}>{perm.features}</Text>
          <Pressable
            style={({pressed}) => [styles.helpBtn, pressed && {opacity: 0.8}]}
            onPress={onGetHelp}
            disabled={opening}>
            {opening ? (
              <ActivityIndicator size="small" color="#7c3aed" />
            ) : (
              <Text style={styles.helpBtnText}>Get help</Text>
            )}
          </Pressable>
        </>
      )}
    </View>
  );
}

export function CheckPermissionsScreen({navigation}: Props) {
  const [perms, setPerms] = useState<PermStatus[]>([]);
  const [loading, setLoading] = useState(true);

  const check = useCallback(async () => {
    setLoading(true);
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

      let screen = await nativeModules.isMediaProjectionGranted();
      if (!screen && (await nativeModules.needsScreenCaptureRecovery()) && accessibility) {
        flowLog('PERMISSION_FLOW', 'CheckPermissions — auto-recovering screen capture');
        screen = await recoverScreenCaptureIfLost();
      }

      setPerms([
        {
          key: 'accessibility',
          icon: '🛡️',
          title: 'Accessibility Permission',
          features:
            'App blocking、Screen time limits、Usage reporting、MediaProjection auto-approve (Device Owner)',
          granted: accessibility,
          openSettings: nativeModules.openAccessibilitySettings,
        },
        {
          key: 'camera',
          icon: '📷',
          title: 'Camera Permission',
          features: 'Remote Camera、Camera Snapshot',
          granted: camera,
          openSettings: nativeModules.requestCameraAndMicPermissions,
        },
        {
          key: 'deviceAdmin',
          icon: '👤',
          title: 'Device Admin Permission',
          features:
            'The child app might be deleted by the child and it will not be able to be recovered. Device screen lock control will be unavailable.',
          granted: deviceAdmin,
          openSettings: nativeModules.openDeviceAdminSettings,
        },
        {
          key: 'usageStats',
          icon: '📊',
          title: 'Usage Statistics Permission',
          features: 'Usage Report、App Usage Time、Screen Time Limits',
          granted: usageStats,
          openSettings: nativeModules.openUsageStatsSettings,
        },
        {
          key: 'notification',
          icon: '🔔',
          title: 'Notification Listener Permission',
          features: 'App Notifications、Social App Detection',
          granted: notification,
          openSettings: nativeModules.openNotificationListenerSettings,
        },
        {
          key: 'location',
          icon: '📍',
          title: 'Location Permission',
          features: 'Live Location、Geofence Alerts',
          granted: location,
          openSettings: async () => {
            await nativeModules.requestLocationPermission();
          },
        },
        {
          key: 'mic',
          icon: '🎙️',
          title: 'Microphone Permission',
          features: 'One-Way Audio、Ambient Recording',
          granted: mic,
          openSettings: nativeModules.requestCameraAndMicPermissions,
        },
        {
          key: 'screen',
          icon: '📺',
          title: 'Screen Capture Permission',
          features: 'Screen Mirroring、Live View',
          granted: screen,
          openSettings: async () => {
            if (screen) {
              flowLog('PERMISSION_FLOW', 'CheckPermissions — screen capture already granted');
              return;
            }
            flowLog('PERMISSION_FLOW', 'CheckPermissions — starting screen capture consent');
            try {
              await nativeModules.armScreenCaptureAutoApprove();
              const granted = await nativeModules.requestMediaProjectionConsent();
              const debug = await nativeModules.getMediaProjectionDebugState().catch(() => ({}));
              flowLog('PERMISSION_FLOW', 'CheckPermissions — consent finished', {granted, debug});
            } finally {
              await nativeModules.disarmScreenCaptureAutoApprove().catch(() => undefined);
            }
          },
        },
        {
          key: 'overlay',
          icon: '🪟',
          title: 'Display Over Other Apps',
          features: 'App Block Overlay、Time Limit Screen',
          granted: overlay,
          openSettings: nativeModules.openOverlaySettings,
        },
        {
          key: 'battery',
          icon: '🔋',
          title: 'Battery Optimization Exemption',
          features: 'Background Monitoring、Auto Restart After Reboot',
          granted: battery,
          openSettings: nativeModules.openBatteryOptimizationSettings,
        },
      ]);
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(check);

  const abnormalCount = perms.filter(p => p.granted === false).length;
  const allOk = perms.length > 0 && abnormalCount === 0;

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      {/* Header */}
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>‹</Text>
        </Pressable>
        <Text style={styles.headerTitle}>Check Permissions</Text>
        <Pressable onPress={check} style={styles.refreshBtn}>
          <Text style={styles.refreshText}>↻</Text>
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {/* Device header */}
        <View style={styles.deviceCard}>
          <View style={styles.deviceIconWrap}>
            <Text style={styles.deviceIcon}>📱</Text>
          </View>
          <Text style={styles.deviceName}>Child Device</Text>
          <View style={[styles.statusBadge, allOk ? styles.statusBadgeOk : styles.statusBadgeBad]}>
            <Text style={[styles.statusBadgeText, allOk ? {color: '#15803d'} : {color: '#dc2626'}]}>
              {loading ? 'Checking…' : allOk ? 'Working Normally' : 'Working Abnormally'}
            </Text>
          </View>
        </View>

        {!loading && !allOk && (
          <View style={styles.bannerBad}>
            <Text style={styles.bannerTitle}>The following permissions are abnormal</Text>
            <Text style={styles.bannerSub}>
              Please get the device and follow the tutorial to enable the abnormal permissions.
            </Text>
          </View>
        )}

        {!loading && allOk && (
          <View style={styles.bannerOk}>
            <Text style={styles.bannerTitle}>All permissions are working correctly</Text>
            <Text style={styles.bannerSub}>
              All features are available on this device.
            </Text>
          </View>
        )}

        {loading ? (
          <ActivityIndicator color="#7c3aed" size="large" style={{marginTop: 40}} />
        ) : (
          perms.map(perm => <PermCard key={perm.key} perm={perm} />)
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
    alignItems: 'center',
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.07,
    shadowRadius: 6,
    elevation: 2,
  },
  deviceIconWrap: {width: 60, height: 60, borderRadius: 16, backgroundColor: '#ede9fe', alignItems: 'center', justifyContent: 'center', marginBottom: 8},
  deviceIcon: {fontSize: 30},
  deviceName: {fontSize: 16, fontWeight: '700', color: '#0f172a', marginBottom: 8},
  statusBadge: {paddingHorizontal: 14, paddingVertical: 5, borderRadius: 20},
  statusBadgeOk: {backgroundColor: '#dcfce7'},
  statusBadgeBad: {backgroundColor: '#fee2e2'},
  statusBadgeText: {fontSize: 13, fontWeight: '700'},

  bannerBad: {backgroundColor: '#fff', borderRadius: 12, padding: 16, marginBottom: 16, borderLeftWidth: 4, borderLeftColor: '#ef4444'},
  bannerOk: {backgroundColor: '#fff', borderRadius: 12, padding: 16, marginBottom: 16, borderLeftWidth: 4, borderLeftColor: '#22c55e'},
  bannerTitle: {fontSize: 15, fontWeight: '800', color: '#0f172a', marginBottom: 4},
  bannerSub: {fontSize: 13, color: '#64748b', lineHeight: 18},

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

  featureLabel: {fontSize: 12, color: '#64748b', marginTop: 12, lineHeight: 18},
  featureList: {fontSize: 12, color: '#475569', fontWeight: '600', marginTop: 4, lineHeight: 18},

  helpBtn: {
    marginTop: 14,
    borderWidth: 1.5,
    borderColor: '#7c3aed',
    borderRadius: 22,
    paddingVertical: 10,
    alignItems: 'center',
  },
  helpBtnText: {fontSize: 14, fontWeight: '700', color: '#7c3aed'},
});
