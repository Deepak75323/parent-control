import React, {useState} from 'react';
import {Platform, StyleSheet} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {authApi} from '../services/api';
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
} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'ResetPassword'>;

const tokenInputStyle = StyleSheet.create({
  input: {
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 13,
  },
}).input;

export function ResetPasswordScreen({navigation, route}: Props) {
  const [token, setToken] = useState(route.params?.token ?? '');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    if (!token.trim()) {
      setError('Paste the reset token from your email link.');
      return;
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    setLoading(true);
    try {
      const {data} = await authApi.resetPassword(token.trim(), password);
      if (data.success) {
        showSuccessAlert(
          'Password updated',
          data.message ?? 'Sign in with your new password.',
          () => navigation.navigate('Login'),
        );
        return;
      }
      setError(data.message ?? 'Unable to reset password. Request a new link.');
    } catch (err) {
      setError(getApiErrorMessage(err, 'Invalid or expired reset link.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenShell backLabel="Forgot password" onBack={() => navigation.navigate('ForgotPassword')}>
      <HeroIconBlock
        icon="🔑"
        title="Choose a new password"
        subtitle="Open the link from your email, or paste the reset token below. Tokens expire after one hour."
      />
      <Card>
        {error ? <ErrorBox message={error} /> : null}
        <FormField
          label="Reset token"
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
        <FormField
          label="New password"
          value={password}
          onChangeText={setPassword}
          placeholder="At least 8 characters"
          secureTextEntry
        />
        <FormField
          label="Confirm password"
          value={confirmPassword}
          onChangeText={setConfirmPassword}
          placeholder="Repeat new password"
          secureTextEntry
        />
        <PrimaryBtn title="Update password" onPress={onSubmit} loading={loading} />
        <LinkBtn title="Request a new link" onPress={() => navigation.navigate('ForgotPassword')} />
      </Card>
    </ScreenShell>
  );
}
