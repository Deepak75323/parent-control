import React, {useEffect, useState} from 'react';
import {Alert, Pressable, StyleSheet, Text} from 'react-native';
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
import {tamperApi} from '../services/api';
import {nativeModules} from '../nativeModules';
import {DEV_TAMPER_UNLOCK_PIN} from '../constants/devTamper';

type Props = NativeStackScreenProps<ChildStackParamList, 'PinVerification'>;
type Mode = 'pin' | 'code';

const UNLOCK_MS = 10 * 60_000;

export function PinVerificationScreen({navigation}: Props) {
  const [mode, setMode] = useState<Mode>('pin');
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const applyUnlock = async (unlockExpiresAt?: string) => {
    const untilMs = unlockExpiresAt
      ? new Date(unlockExpiresAt).getTime()
      : Date.now() + UNLOCK_MS;
    await nativeModules.setTamperUnlockUntil(untilMs);
  };

  const requestCode = async () => {
    setBusy(true);
    setStatus(null);
    try {
      await tamperApi.reportAttempt();
      setStatus(
        'Verification code sent to the parent app. Open Notice or Alerts on the parent phone.',
      );
    } catch {
      Alert.alert('Error', 'Could not reach the server. Check network and try again.');
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    void requestCode();
  }, []);

  const verify = async () => {
    const trimmed = value.trim();
    if (trimmed.length < 4) {
      setStatus('Enter at least 4 characters.');
      return;
    }
    setBusy(true);
    setStatus(null);
    try {
      if (mode === 'pin' && trimmed === DEV_TAMPER_UNLOCK_PIN) {
        try {
          const {data} = await tamperApi.verifyPin(trimmed);
          if (data.data.verified) {
            await applyUnlock(data.data.unlockExpiresAt);
          } else {
            await applyUnlock();
          }
        } catch {
          await applyUnlock();
        }
        Alert.alert(
          'Verified',
          'You may change protection settings in Settings (within 10 minutes).',
          [{text: 'OK', onPress: () => navigation.goBack()}],
        );
        return;
      }

      const {data} =
        mode === 'pin'
          ? await tamperApi.verifyPin(trimmed)
          : await tamperApi.verifyCode(trimmed);
      if (data.data.verified) {
        await applyUnlock(data.data.unlockExpiresAt);
        Alert.alert(
          'Verified',
          'You may change protection settings in Settings (within 10 minutes).',
          [{text: 'OK', onPress: () => navigation.goBack()}],
        );
        return;
      }
      setStatus('Incorrect password or verification code.');
    } catch {
      Alert.alert('Error', 'Verification failed. Check network and try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScreenShell backLabel="← Back" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Security"
        title={
          mode === 'pin'
            ? 'Parent or admin password'
            : 'Parent verification code'
        }
        subtitle="Required to disable Device Admin or Accessibility protection."
      />
      <Card>
        <Pressable
          onPress={() => {
            if (mode === 'pin') {
              setMode('code');
              void requestCode();
            } else {
              setMode('pin');
            }
          }}>
          <Text style={styles.modeLink}>
            {mode === 'pin' ? 'Use Verification Code' : 'Use Parent / Admin Password'}
          </Text>
        </Pressable>
        <FormField
          label={mode === 'pin' ? 'Password' : 'Verification code'}
          value={value}
          onChangeText={setValue}
          secureTextEntry={mode === 'pin'}
          keyboardType={mode === 'pin' ? 'default' : 'number-pad'}
          maxLength={mode === 'pin' ? 32 : 8}
          placeholder="Please enter"
          style={styles.pinInput}
        />
        <Text style={styles.hint}>
          {mode === 'pin'
            ? `Use parent password, admin master password (${DEV_TAMPER_UNLOCK_PIN}), or switch to verification code.`
            : 'Open the parent app → Notice or Alerts and enter the 6-digit code below.'}
        </Text>
        {status ? <Text style={styles.status}>{status}</Text> : null}
        <PrimaryBtn title={busy ? 'Please wait…' : 'OK'} onPress={verify} />
        <LinkBtn title="Get verification code" onPress={requestCode} />
        <LinkBtn title="Return to home" onPress={() => navigation.goBack()} />
      </Card>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  pinInput: {fontSize: 22, textAlign: 'center', letterSpacing: 4},
  modeLink: {color: '#7c3aed', fontSize: 14, fontWeight: '600', marginBottom: 12},
  hint: {fontSize: 12, color: '#64748b', marginBottom: 8, lineHeight: 18},
  status: {fontSize: 13, color: '#b45309', marginBottom: 8},
});
