import axios from 'axios';
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

type Props = NativeStackScreenProps<ChildStackParamList, 'PinVerification'>;
type Mode = 'pin' | 'code';

function userFacingRequestError(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    if (!err.response) {
      return 'No internet connection. Check Wi‑Fi or mobile data and try again.';
    }
    if (err.response.status === 429) {
      return 'Too many tries. Wait a minute, then try again.';
    }
    if (err.response.status >= 500) {
      return 'Something went wrong. Please try again in a moment.';
    }
    const msg = (err.response.data as {message?: string} | undefined)?.message;
    if (typeof msg === 'string' && msg.trim()) {
      return msg.trim();
    }
  }
  return fallback;
}

export function PinVerificationScreen({navigation}: Props) {
  const [mode, setMode] = useState<Mode>('pin');
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const requestCode = async () => {
    setBusy(true);
    setStatus(null);
    try {
      await tamperApi.reportAttempt();
      setStatus(
        'Verification code sent to the parent app. Open Notice or Alerts on the parent phone.',
      );
    } catch (err) {
      Alert.alert(
        'Error',
        userFacingRequestError(err, 'Something went wrong. Please try again in a moment.'),
      );
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
      // Native path: online-first, offline cached verifier + local lockout when API is down.
      const result =
        mode === 'pin'
          ? await nativeModules.verifyTamperSecret(trimmed, null, null)
          : await nativeModules.verifyTamperSecret(null, trimmed, null);
      if (result.verified) {
        const offlineNote = result.usedOffline
          ? ' (verified offline — connect soon so the parent password stays in sync)'
          : '';
        Alert.alert(
          'Verified',
          `You may change protection settings in Settings (within 10 minutes).${offlineNote}`,
          [{text: 'OK', onPress: () => navigation.goBack()}],
        );
        return;
      }
      setStatus(result.error?.trim() || 'Incorrect password or verification code.');
    } catch (err) {
      Alert.alert(
        'Error',
        userFacingRequestError(err, 'Something went wrong. Please try again in a moment.'),
      );
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
            ? 'Parent password'
            : 'Parent verification code'
        }
        subtitle="Required before turning off device protection settings."
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
            {mode === 'pin' ? 'Use Verification Code' : 'Use Parent Password'}
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
            ? 'Uses the parent password from Settings. Works offline with lockout after wrong tries when the server was recently reachable.'
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
