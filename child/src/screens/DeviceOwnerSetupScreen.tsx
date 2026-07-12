import React, {useCallback, useState} from 'react';
import {Text, StyleSheet, Alert} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useFocusEffect} from '@react-navigation/native';
import {ChildStackParamList} from './RootNavigator';
import {nativeModules} from '../nativeModules';
import {flowLog} from '../services/flowLogger';
import {isPermissionStatusComplete, loadPermissionStatus} from '../services/permissionDashboard/permissionStatus';
import {
  Card,
  HeroBlock,
  PrimaryBtn,
  ScreenShell,
  SecondaryBtn,
} from '../components/AppScreen';

type Props = NativeStackScreenProps<ChildStackParamList, 'DeviceOwnerSetup'>;

export function DeviceOwnerSetupScreen({navigation}: Props) {
  const [isOwner, setIsOwner] = useState(false);
  const [projectionOk, setProjectionOk] = useState(false);
  const [adbCmd, setAdbCmd] = useState('');
  const [consentBusy, setConsentBusy] = useState(false);

  const refresh = useCallback(async () => {
    await nativeModules.refreshDeviceOwnerState();
    const owner = await nativeModules.isDeviceOwner();
    const screen = await nativeModules.isMediaProjectionGranted();
    setIsOwner(owner);
    setProjectionOk(screen);
    setAdbCmd(await nativeModules.getAdbDeviceOwnerCommand());
    flowLog('PERMISSION_FLOW', 'DeviceOwnerSetup refresh', {isOwner: owner, projectionOk: screen});
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh]),
  );

  const grantScreenCapture = useCallback(async () => {
    if (projectionOk) {
      flowLog('PERMISSION_FLOW', 'DeviceOwnerSetup — screen capture already granted');
      return;
    }
    if (!(await nativeModules.isScreenCaptureAutoApproveReady())) {
      Alert.alert(
        'Enable Accessibility first',
        'Accessibility auto-taps "Share screen" on the Android dialog. Enable it in Permissions setup, then try again.',
      );
      return;
    }
    if (consentBusy) {
      flowLog('PERMISSION_FLOW', 'DeviceOwnerSetup — consent already in progress');
      return;
    }
    setConsentBusy(true);
    try {
      flowLog('PERMISSION_FLOW', 'DeviceOwnerSetup — starting screen capture consent');
      await nativeModules.armScreenCaptureAutoApprove();
      const granted = await nativeModules.requestMediaProjectionConsent();
      const debug = await nativeModules.getMediaProjectionDebugState().catch(() => ({}));
      flowLog('PERMISSION_FLOW', 'DeviceOwnerSetup — consent finished', {granted, debug});
      await refresh();
      if (!granted) {
        Alert.alert(
          'Screen capture not saved',
          'Consent was not stored after the dialog. Filter Logcat with PCP_FLOW or MediaProjectionTokenStore.',
        );
      }
    } finally {
      await nativeModules.disarmScreenCaptureAutoApprove().catch(() => undefined);
      setConsentBusy(false);
    }
  }, [consentBusy, projectionOk, refresh]);

  const continueToPairing = useCallback(async () => {
    const status = await loadPermissionStatus();
    if (!isPermissionStatusComplete(status)) {
      Alert.alert(
        'Permissions required',
        'Grant every permission on the dashboard before continuing.',
      );
      navigation.navigate('PermissionWizard');
      return;
    }
    navigation.navigate('Pairing');
  }, [navigation]);

  const caps = isOwner && projectionOk;

  return (
    <ScreenShell backLabel="← Back" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Optional upgrade"
        title="Full protection"
        subtitle="Silent screen monitoring and stronger uninstall protection. Requires factory-reset device with no Google account, then ADB or QR provisioning."
      />

      <Card>
        <Text style={styles.cardTitle}>Device Owner {isOwner ? '✓' : '—'}</Text>
        <Text style={styles.cardSub}>
          Run on a computer with USB debugging (device must have no accounts):
        </Text>
        <Text style={styles.code} selectable>
          {adbCmd || 'adb shell dpm set-device-owner …'}
        </Text>
        <SecondaryBtn title="Check status" onPress={refresh} />
      </Card>

      <Card>
        <Text style={styles.cardTitle}>Screen capture (one-time) {projectionOk ? '✓' : '—'}</Text>
        <Text style={styles.cardSub}>
          Grant once during setup. Parent screen share will not show a dialog on each session.
        </Text>
        <SecondaryBtn
          title={
            consentBusy
              ? 'Waiting for screen capture…'
              : projectionOk
                ? 'Screen capture granted ✓'
                : 'Grant screen capture'
          }
          onPress={grantScreenCapture}
        />
      </Card>

      <PrimaryBtn
        title={caps ? 'Continue to pairing' : 'Skip — use standard protection'}
        onPress={() => void continueToPairing()}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  cardTitle: {fontSize: 16, fontWeight: '700', color: '#0f172a'},
  cardSub: {fontSize: 13, color: '#64748b', marginTop: 6, marginBottom: 8, lineHeight: 19},
  code: {fontFamily: 'monospace', fontSize: 11, color: '#334155', marginBottom: 4},
});
