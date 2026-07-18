import React, {useCallback, useEffect, useState} from 'react';
import {Pressable, StyleSheet, Text, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {
  Card,
  FormField,
  HeroBlock,
  LinkBtn,
  PrimaryBtn,
  ScreenShell,
} from '../components/AppScreen';
import {
  authenticateWithRecoveryCode,
  authenticateWithSetupCode,
  requestRecoveryCode,
} from '../services/onboarding/childLoginService';
import {restoreDeviceCredentials} from '../services/deviceStorage';
import {nativeModules} from '../nativeModules';
import {flowLog} from '../services/flowLogger';

type Props = NativeStackScreenProps<ChildStackParamList, 'ChildRecovery'>;

export function ChildRecoveryScreen({navigation}: Props) {
  const [recoveryCode, setRecoveryCode] = useState('');
  const [setupCode, setSetupCode] = useState('');
  const [showSetupCode, setShowSetupCode] = useState(false);
  const [paired, setPaired] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [codeRequested, setCodeRequested] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const token = await restoreDeviceCredentials();
      const stored = await nativeModules.getStoredSyncConfig();
      const isPaired = Boolean(token) || stored.isPaired === true;
      if (cancelled) return;
      setPaired(isPaired);

      // Unpaired / wiped device: recovery alerts require a linked device. Server always
      // returns codeSent=true (anti-enumeration), which looked like a fake "code sent".
      if (!isPaired) {
        setShowSetupCode(true);
        setStatus(
          'This phone isn’t linked to a parent yet. Generate an 8-character pairing code in the parent app, then enter it below.',
        );
        return;
      }

      setStatus('Sending a verification code to the parent app…');
      const result = await requestRecoveryCode();
      if (cancelled) return;
      if (result.ok) {
        setCodeRequested(true);
        setStatus('Code sent. Open the parent app → Alerts to view the 6-digit code.');
        return;
      }
      setStatus(result.message);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const submitRecovery = useCallback(async () => {
    setBusy(true);
    setStatus(null);
    try {
      const result = await authenticateWithRecoveryCode(recoveryCode);
      if (result.ok) {
        flowLog('ONBOARDING', 'ChildRecovery success — starting wizard');
        navigation.replace('PermissionWizard');
        return;
      }
      setStatus(result.message);
    } finally {
      setBusy(false);
    }
  }, [recoveryCode, navigation]);

  const submitSetup = useCallback(async () => {
    setBusy(true);
    setStatus(null);
    try {
      const result = await authenticateWithSetupCode(setupCode);
      if (result.ok) {
        flowLog('ONBOARDING', 'ChildRecovery setup code ok — starting wizard');
        navigation.replace('PermissionWizard');
        return;
      }
      setStatus(result.message);
    } finally {
      setBusy(false);
    }
  }, [setupCode, navigation]);

  const resendCode = useCallback(async () => {
    if (paired === false) {
      setStatus(
        'This phone isn’t linked yet — use an 8-character pairing code from the parent app instead.',
      );
      setShowSetupCode(true);
      return;
    }
    setBusy(true);
    setStatus('Sending verification code…');
    try {
      const result = await requestRecoveryCode();
      setStatus(
        result.ok
          ? 'New code sent to the parent app Alerts.'
          : result.message,
      );
      if (result.ok) {
        setCodeRequested(true);
      }
    } finally {
      setBusy(false);
    }
  }, [paired]);

  const unpaired = paired === false;

  return (
    <ScreenShell>
      <HeroBlock
        eyebrow="Clock Time"
        title={unpaired ? 'Link with parent' : 'Forgot password'}
        subtitle={
          unpaired
            ? 'Enter the 8-character pairing code from the parent app to begin setup.'
            : 'A 6-digit code is sent to the parent app Alerts when you open this screen.'
        }
      />
      <Card>
        {!unpaired ? (
          <>
            <FormField
              label="Verification code (from parent Alerts)"
              value={recoveryCode}
              onChangeText={setRecoveryCode}
              keyboardType="number-pad"
              maxLength={6}
              placeholder="6-digit code"
              editable={!busy}
            />
            {status ? (
              <Text
                style={[
                  styles.status,
                  codeRequested && !status.startsWith('Incorrect') && styles.statusOk,
                ]}>
                {status}
              </Text>
            ) : null}
            <PrimaryBtn
              title={busy ? 'Verifying…' : 'Continue'}
              onPress={() => void submitRecovery()}
              disabled={busy || recoveryCode.trim().length < 6}
            />
            <View style={styles.linkRow}>
              <LinkBtn title="Resend code to parent app" onPress={() => void resendCode()} />
            </View>
            <View style={styles.linkRow}>
              <LinkBtn
                title={
                  showSetupCode
                    ? 'Hide 8-character setup code'
                    : 'Have an 8-character setup code instead?'
                }
                onPress={() => setShowSetupCode(v => !v)}
              />
            </View>
          </>
        ) : status ? (
          <Text style={[styles.status, styles.statusOk]}>{status}</Text>
        ) : null}

        {(unpaired || showSetupCode) ? (
          <>
            <FormField
              label="Pairing / setup code"
              value={setupCode}
              onChangeText={(t: string) => setSetupCode(t.toUpperCase())}
              autoCapitalize="characters"
              maxLength={8}
              placeholder="ABCD1234"
              editable={!busy}
            />
            <PrimaryBtn
              title={busy ? 'Verifying…' : 'Use pairing code'}
              onPress={() => void submitSetup()}
              disabled={busy || setupCode.trim().length < 8}
            />
          </>
        ) : null}

        <View style={styles.linkRow}>
          <LinkBtn title="Back to password login" onPress={() => navigation.goBack()} />
        </View>
      </Card>
      <Pressable onPress={() => navigation.goBack()}>
        <Text style={styles.help}>
          {unpaired
            ? 'Forgot password only works after this phone is paired with a parent.'
            : 'Only use this if you forgot the parent password.'}
        </Text>
      </Pressable>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  status: {
    fontSize: 13,
    color: '#dc2626',
    marginBottom: 12,
    lineHeight: 18,
  },
  statusOk: {
    color: '#15803d',
  },
  linkRow: {marginTop: 12, alignItems: 'center'},
  help: {
    textAlign: 'center',
    fontSize: 12,
    color: '#94a3b8',
    marginTop: 16,
  },
});
