import React, {useCallback, useEffect, useRef, useState} from 'react';
import {Platform, StyleSheet} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {authApi} from '../services/api';
import {useAuthStore} from '../store/authStore';
import {getApiErrorMessage} from '../utils/apiError';
import {showSuccessAlert} from '../utils/notify';
import {
  Card,
  ErrorBox,
  FormField,
  HeroIconBlock,
  LinkBtn,
  PrimaryBtn,
  ScreenShell,
  SecondaryBtn,
} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'VerifyEmail'>;

const tokenInputStyle = StyleSheet.create({
  input: {
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 13,
  },
}).input;

export function VerifyEmailScreen({navigation, route}: Props) {
  const accessToken = useAuthStore(s => s.accessToken);
  const setEmailVerified = useAuthStore(s => s.setEmailVerified);
  const [token, setToken] = useState(route.params?.token ?? '');
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const autoSubmitted = useRef(false);

  const onVerify = useCallback(async () => {
    setError(null);
    if (!token.trim()) {
      setError('Paste the verification token from your email link.');
      return;
    }
    setLoading(true);
    try {
      const {data} = await authApi.verifyEmail(token.trim());
      if (data.success) {
        setEmailVerified(true);
        showSuccessAlert(
          'Email verified',
          data.message ?? 'Your trial has started. You can use all features now.',
          () => {
            if (accessToken) {
              navigation.navigate('MainTabs');
            } else {
              navigation.navigate('Login');
            }
          },
        );
        return;
      }
      setError(data.message ?? 'Unable to verify email. Request a new link.');
    } catch (err) {
      setError(getApiErrorMessage(err, 'Invalid or expired verification link.'));
    } finally {
      setLoading(false);
    }
  }, [accessToken, navigation, setEmailVerified, token]);

  const onResend = async () => {
    if (!accessToken) {
      setError('Sign in first, then use Resend from the dashboard banner.');
      return;
    }
    setError(null);
    setResending(true);
    try {
      const {data} = await authApi.resendVerification();
      if (data.success) {
        showSuccessAlert('Email sent', data.message ?? 'Check your inbox for a new verification link.');
        return;
      }
      setError(data.message ?? 'Unable to resend verification email.');
    } catch (err) {
      setError(getApiErrorMessage(err, 'Unable to resend verification email.'));
    } finally {
      setResending(false);
    }
  };

  useEffect(() => {
    if (!route.params?.token || autoSubmitted.current) {
      return;
    }
    autoSubmitted.current = true;
    onVerify().catch(() => undefined);
  }, [onVerify, route.params?.token]);

  return (
    <ScreenShell>
      <HeroIconBlock
        icon="✉"
        title="Verify your email"
        subtitle="Open the link from your welcome email, or paste the verification token below. Verification starts your free trial."
      />
      <Card>
        {error ? <ErrorBox message={error} /> : null}
        <FormField
          label="Verification token"
          value={token}
          onChangeText={v => {
            setToken(v);
            setError(null);
          }}
          placeholder="Token from email link"
          autoCapitalize="none"
          autoCorrect={false}
          style={tokenInputStyle}
        />
        <PrimaryBtn title="Verify email" onPress={onVerify} loading={loading} />
        {accessToken ? (
          <SecondaryBtn
            title={resending ? 'Sending…' : 'Resend verification email'}
            onPress={onResend}
            disabled={resending}
          />
        ) : (
          <LinkBtn title="Back to sign in" onPress={() => navigation.navigate('Login')} />
        )}
      </Card>
    </ScreenShell>
  );
}
