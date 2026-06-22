import React, {useCallback, useMemo, useRef, useState} from 'react';
import {View, Text, StyleSheet, Pressable, Alert, Switch, FlatList, ListRenderItem, AppState, AppStateStatus} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useFocusEffect} from '@react-navigation/native';
import {ChildStackParamList} from './RootNavigator';
import {nativeModules} from '../nativeModules';
import {flowLog} from '../services/flowLogger';
import {recoverScreenCaptureIfLost} from '../services/streamingPermissions';
import {
  Card,
  HeroBlock,
  PrimaryBtn,
  ProgressBar,
  ScreenShell,
  StatusBadge,
} from '../components/AppScreen';

type Props = NativeStackScreenProps<ChildStackParamList, 'PermissionSetup'>;

type PermissionKey =
  | 'notification'
  | 'usage'
  | 'location'
  | 'camera'
  | 'screen'
  | 'accessibility'
  | 'overlay'
  | 'fullScreenIntent'
  | 'battery'
  | 'oemBackground'
  | 'miuiAutostart'
  | 'deviceAdmin';

type PermissionItem = {
  key: PermissionKey;
  title: string;
  description: string;
  granted: boolean;
};

function PermissionCard({
  title,
  description,
  granted,
  onPress,
}: {
  title: string;
  description: string;
  granted: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      style={({pressed}) => [styles.permCard, granted && styles.permGranted, pressed && styles.permPressed]}
      onPress={onPress}>
      <View style={styles.permHeader}>
        <Text style={styles.permTitle}>{title}</Text>
        <StatusBadge granted={granted} />
      </View>
      <Text style={styles.permSub}>{description}</Text>
    </Pressable>
  );
}

export function PermissionSetupScreen({navigation}: Props) {
  const [notifOk, setNotifOk] = useState(false);
  const [usageOk, setUsageOk] = useState(false);
  const [locationOk, setLocationOk] = useState(false);
  const [cameraOk, setCameraOk] = useState(false);
  const [micOk, setMicOk] = useState(false);
  const [screenCaptureOk, setScreenCaptureOk] = useState(false);
  const [accessibilityOk, setAccessibilityOk] = useState(false);
  const [overlayOk, setOverlayOk] = useState(false);
  const [fullScreenIntentOk, setFullScreenIntentOk] = useState(true);
  const [batteryOk, setBatteryOk] = useState(false);
  const [oemBackgroundOk, setOemBackgroundOk] = useState(false);
  const [requiresOemBackground, setRequiresOemBackground] = useState(false);
  const [oemVendorName, setOemVendorName] = useState('Device');
  const [miuiAutostartOk, setMiuiAutostartOk] = useState(false);
  const [isXiaomi, setIsXiaomi] = useState(false);
  const [deviceAdminOk, setDeviceAdminOk] = useState(false);
  const [hideLauncher, setHideLauncher] = useState(false);

  const [screenCaptureBusy, setScreenCaptureBusy] = useState(false);
  const recoveringScreenRef = useRef(false);

  const refresh = useCallback(async (tryRecoverScreen = false) => {
    setNotifOk(await nativeModules.isNotificationListenerEnabled());
    setUsageOk(await nativeModules.isUsageStatsGranted());
    setLocationOk(await nativeModules.isLocationGranted());
    setCameraOk(await nativeModules.isCameraGranted());
    setMicOk(await nativeModules.isMicGranted());
    let screen = await nativeModules.isMediaProjectionGranted();
    if (!screen && tryRecoverScreen && !recoveringScreenRef.current) {
      const needsRecovery = await nativeModules.needsScreenCaptureRecovery();
      const accessibility = await nativeModules.isAccessibilityEnabled();
      if (needsRecovery && accessibility) {
        recoveringScreenRef.current = true;
        setScreenCaptureBusy(true);
        try {
          flowLog('PERMISSION_FLOW', 'PermissionSetup — auto-recovering screen capture');
          screen = await recoverScreenCaptureIfLost();
          if (!screen) {
            Alert.alert(
              'Screen capture needs re-approval',
              'Android cleared screen capture while you were in Settings. Tap "Screen capture" below to grant it again (accessibility will auto-approve if enabled).',
            );
          }
        } finally {
          recoveringScreenRef.current = false;
          setScreenCaptureBusy(false);
        }
      } else if (needsRecovery) {
        flowLog('PERMISSION_FLOW', 'PermissionSetup — screen capture lost; enable accessibility first for auto-recover');
        Alert.alert(
          'Screen capture was reset',
          'Some phones clear screen capture when you open other Settings. Tap "Screen capture" at the bottom of this list to grant it again.',
        );
      }
    }
    setScreenCaptureOk(screen);
    flowLog('PERMISSION_FLOW', 'PermissionSetup refresh', {screenCapture: screen});
    setAccessibilityOk(await nativeModules.isAccessibilityEnabled());
    setOverlayOk(await nativeModules.isOverlayGranted());
    setFullScreenIntentOk(await nativeModules.isFullScreenIntentGranted());
    setBatteryOk(await nativeModules.isBatteryOptimizationExempt());
    const xiaomi = await nativeModules.isXiaomiDevice();
    setIsXiaomi(xiaomi);
    const needsOemBg = await nativeModules.requiresOemBackgroundSetup();
    setRequiresOemBackground(needsOemBg);
    setOemVendorName(await nativeModules.getOemVendorDisplayName());
    if (needsOemBg) {
      setOemBackgroundOk(await nativeModules.isOemAutostartPromptDone());
    }
    if (xiaomi) {
      setMiuiAutostartOk(await nativeModules.isMiuiAutostartPromptDone());
    }
    setDeviceAdminOk(await nativeModules.isDeviceAdminActive());
    setHideLauncher(await nativeModules.isLauncherHidden());
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh(true);
      const onAppStateChange = (nextState: AppStateStatus) => {
        if (nextState === 'active') refresh(true);
      };
      const subscription = AppState.addEventListener('change', onAppStateChange);
      return () => subscription.remove();
    }, [refresh]),
  );

  const grantedCount = useMemo(() => {
    let count = 0;
    if (notifOk) count++;
    if (usageOk) count++;
    if (locationOk) count++;
    if (cameraOk && micOk) count++;
    if (screenCaptureOk) count++;
    if (accessibilityOk) count++;
    if (overlayOk) count++;
    if (fullScreenIntentOk) count++;
    if (batteryOk) count++;
    if (!requiresOemBackground || oemBackgroundOk) count++;
    if (deviceAdminOk) count++;
    return count;
  }, [notifOk, usageOk, locationOk, cameraOk, micOk, screenCaptureOk, accessibilityOk, overlayOk, fullScreenIntentOk, batteryOk, requiresOemBackground, oemBackgroundOk, deviceAdminOk]);

  const totalRequired = 10;
  const allSetupGranted =
    notifOk &&
    usageOk &&
    locationOk &&
    cameraOk &&
    micOk &&
    screenCaptureOk &&
    accessibilityOk &&
    overlayOk &&
    fullScreenIntentOk &&
    batteryOk &&
    (!requiresOemBackground || oemBackgroundOk) &&
    deviceAdminOk;

  const toggleHideLauncher = useCallback(async (value: boolean) => {
    if (value) {
      const adminActive = await nativeModules.isDeviceAdminActive();
      if (!adminActive) {
        Alert.alert(
          'Device administrator required',
          'Enable device administrator first. It prevents uninstall without deactivating admin.',
        );
        return;
      }
    }
    const ok = await nativeModules.setHideLauncher(value);
    if (value && !ok) {
      Alert.alert(
        'Could not hide icon',
        'Your launcher entry could not be updated. On some phones (e.g. Xiaomi) you may need to restart the phone after hiding, or skip this optional step — monitoring still works without it.',
      );
      return;
    }
    setHideLauncher(value);
  }, []);

  const openPermission = useCallback(
    async (key: PermissionKey) => {
      switch (key) {
        case 'notification':
          await nativeModules.openNotificationListenerSettings();
          break;
        case 'usage':
          await nativeModules.openUsageStatsSettings();
          break;
        case 'location':
          await nativeModules.requestLocationPermission();
          await refresh();
          break;
        case 'camera':
          await nativeModules.requestCameraAndMicPermissions();
          await refresh();
          break;
        case 'screen':
          if (screenCaptureOk) {
            flowLog('PERMISSION_FLOW', 'screen capture already granted — skip dialog');
            break;
          }
          if (screenCaptureBusy) {
            flowLog('PERMISSION_FLOW', 'screen capture consent already in progress — ignore tap');
            break;
          }
          setScreenCaptureBusy(true);
          try {
            flowLog('PERMISSION_FLOW', 'PermissionSetup — starting screen capture consent');
            await nativeModules.armScreenCaptureAutoApprove();
            const granted = await nativeModules.requestMediaProjectionConsent();
            const debug = await nativeModules.getMediaProjectionDebugState().catch(() => ({}));
            flowLog('PERMISSION_FLOW', 'PermissionSetup — consent finished', {granted, debug});
            if (!granted) {
              Alert.alert(
                'Screen capture not saved',
                'The permission dialog was shown but consent was not stored. Check Logcat for PCP_FLOW / MEDIA_PROJECTION and try again.',
              );
            }
          } finally {
            await nativeModules.disarmScreenCaptureAutoApprove().catch(() => undefined);
            setScreenCaptureBusy(false);
          }
          await refresh();
          break;
        case 'accessibility':
          await nativeModules.openAccessibilitySettings();
          break;
        case 'overlay':
          await nativeModules.openOverlaySettings();
          break;
        case 'fullScreenIntent':
          await nativeModules.openFullScreenIntentSettings();
          Alert.alert(
            'Allow full-screen notifications',
            'Enable "Allow full-screen intent notifications" for Device Monitor. This lets the monitoring service start reliably on Xiaomi, Redmi, and other phones that block background app launches.',
          );
          break;
        case 'battery':
          await nativeModules.openBatteryOptimizationSettings();
          break;
        case 'oemBackground':
        case 'miuiAutostart':
          await nativeModules.openOemBackgroundSettings();
          Alert.alert(
            oemVendorName,
            'Enable background / autostart for this app so monitoring survives when hidden or after reboot.',
          );
          break;
        case 'deviceAdmin':
          try {
            if (deviceAdminOk) {
              await nativeModules.openDeviceAdminSettings();
            } else {
              await nativeModules.requestDeviceAdmin();
            }
          } catch (e: unknown) {
            const message =
              e instanceof Error ? e.message : 'Could not open device administrator settings.';
            Alert.alert('Device administrator', message);
          }
          break;
      }
    },
    [deviceAdminOk, refresh, screenCaptureBusy, screenCaptureOk],
  );

  const permissions: PermissionItem[] = useMemo(
    () => [
      {
        key: 'notification',
        title: 'Notification access',
        description: 'Captures incoming notifications for the parent dashboard.',
        granted: notifOk,
      },
      {
        key: 'usage',
        title: 'Usage access',
        description: 'Reads app screen time and usage statistics.',
        granted: usageOk,
      },
      {
        key: 'location',
        title: 'Location',
        description: 'GPS for location history and geofence alerts.',
        granted: locationOk,
      },
      {
        key: 'camera',
        title: 'Camera & microphone',
        description: 'Remote camera and one-way audio — grant once for parent live view.',
        granted: cameraOk && micOk,
      },
      {
        key: 'accessibility',
        title: 'Accessibility',
        description: 'App blocking and screen content access. Enable once in Android Settings.',
        granted: accessibilityOk,
      },
      {
        key: 'overlay',
        title: 'Display over other apps',
        description: 'Shows block screens and alerts on top of other apps. Grant once in Android Settings.',
        granted: overlayOk,
      },
      {
        key: 'fullScreenIntent' as PermissionKey,
        title: 'Full-screen notifications',
        description:
          'Lets the app start screen monitoring reliably on Xiaomi, Redmi, and other phones that block background launches (Android 14+ only).',
        granted: fullScreenIntentOk,
      },
      {
        key: 'battery',
        title: 'Battery optimization',
        description: 'Keeps monitoring running in the background. Choose "Don\'t optimize" or Allow.',
        granted: batteryOk,
      },
      ...(requiresOemBackground
        ? [
            {
              key: 'oemBackground' as PermissionKey,
              title: `${oemVendorName} background (required)`,
              description:
                'Allow autostart / background activity so screen share, commands, and recovery work on this phone.',
              granted: oemBackgroundOk,
            },
          ]
        : []),
      {
        key: 'deviceAdmin',
        title: 'Device administrator',
        description: 'Must be deactivated before uninstalling this app (anti-tampering).',
        granted: deviceAdminOk,
      },
      {
        key: 'screen',
        title: 'Screen capture (one-time)',
        description:
          'Grant this last. Some phones reset screen capture when you leave the app for other Settings.',
        granted: screenCaptureOk,
      },
    ],
    [notifOk, usageOk, locationOk, cameraOk, micOk, screenCaptureOk, accessibilityOk, overlayOk, fullScreenIntentOk, batteryOk, requiresOemBackground, oemVendorName, oemBackgroundOk, deviceAdminOk],
  );

  const renderItem: ListRenderItem<PermissionItem> = useCallback(
    ({item}) => (
      <PermissionCard
        title={item.title}
        description={item.description}
        granted={item.granted}
        onPress={() => openPermission(item.key)}
      />
    ),
    [openPermission],
  );

  const listHeader = useMemo(
    () => (
      <View style={styles.headerWrap}>
        <HeroBlock
          eyebrow="Setup"
          title="Grant permissions"
          subtitle="Grant each permission once during setup. Parent live view should not prompt again unless Android revokes access."
        />
        <ProgressBar granted={grantedCount} total={totalRequired} />
      </View>
    ),
    [grantedCount],
  );

  const liveViewReady = cameraOk && micOk && screenCaptureOk;

  const listFooter = useMemo(
    () => (
      <View style={styles.footer}>
        <Card style={styles.switchCard}>
          <View style={styles.switchRow}>
            <View style={styles.switchText}>
              <Text style={styles.switchTitle}>Hide app from launcher</Text>
              <Text style={styles.switchSub}>
                Open via the background-services notification. Optional — requires device admin.
              </Text>
            </View>
            <Switch value={hideLauncher} onValueChange={toggleHideLauncher} />
          </View>
        </Card>
        {!allSetupGranted ? (
          <Text style={styles.liveViewHint}>
            {liveViewReady
              ? 'Grant every permission above before continuing — each is required once during setup.'
              : 'Grant camera, microphone, and screen capture above, then complete the remaining permissions.'}
          </Text>
        ) : null}
        <PrimaryBtn
          title="Continue"
          onPress={async () => {
            if (!allSetupGranted) {
              Alert.alert(
                'Setup incomplete',
                'Tap each permission above and approve it in Android Settings. All items must show granted before pairing.',
              );
              return;
            }
            try {
              const wizard = await nativeModules.getOemSetupWizard();
              if (wizard.steps.length > 0 && !wizard.complete) {
                navigation.navigate('OemSetupWizard');
                return;
              }
            } catch {
              // If wizard fails to load, don't block setup.
            }
            navigation.navigate('DeviceOwnerSetup');
          }}
        />
      </View>
    ),
    [allSetupGranted, hideLauncher, liveViewReady, navigation, toggleHideLauncher],
  );

  return (
    <ScreenShell
      backLabel="← Back to setup"
      onBack={() => navigation.goBack()}
      scroll={false}>
      <FlatList
        style={styles.list}
        data={permissions}
        keyExtractor={item => item.key}
        renderItem={renderItem}
        ListHeaderComponent={listHeader}
        ListFooterComponent={listFooter}
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator
        keyboardShouldPersistTaps="handled"
        nestedScrollEnabled
        removeClippedSubviews={false}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  list: {flex: 1},
  listContent: {paddingHorizontal: 16, paddingBottom: 24},
  headerWrap: {paddingTop: 8},
  permCard: {
    padding: 14,
    backgroundColor: '#fff',
    borderRadius: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  permGranted: {borderColor: '#bbf7d0', backgroundColor: '#f0fdf4'},
  permPressed: {opacity: 0.85},
  permHeader: {flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8},
  permTitle: {flex: 1, fontSize: 15, fontWeight: '600', color: '#0f172a'},
  permSub: {fontSize: 13, color: '#64748b', marginTop: 6, lineHeight: 18},
  switchCard: {marginBottom: 8},
  switchRow: {flexDirection: 'row', alignItems: 'center'},
  switchText: {flex: 1, marginRight: 12},
  switchTitle: {fontSize: 15, fontWeight: '600', color: '#0f172a'},
  switchSub: {fontSize: 13, color: '#64748b', marginTop: 4, lineHeight: 18},
  liveViewHint: {
    fontSize: 13,
    color: '#92400e',
    backgroundColor: '#fef3c7',
    borderRadius: 12,
    padding: 12,
    marginBottom: 10,
    lineHeight: 18,
  },
  footer: {paddingBottom: 16},
});
