import React, {useState} from 'react';
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

type Props = NativeStackScreenProps<RootStackParamList, 'ForgotPassword'>;

export function ForgotPasswordScreen({navigation}: Props) {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    if (!email.trim()) {
      setError('Enter the email address for your parent account.');
      return;
    }
    setLoading(true);
    try {
      const {data} = await authApi.forgotPassword(email.trim());
      if (data.success) {
        showSuccessAlert(
          'Check your email',
          data.message ?? 'If an account exists, we sent a reset link.',
          () => navigation.navigate('Login'),
        );
        return;
      }
      setError(data.message ?? 'Unable to send reset email. Try again later.');
    } catch (err) {
      setError(getApiErrorMessage(err, 'Unable to send reset email. Try again later.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenShell backLabel="Sign in" onBack={() => navigation.navigate('Login')}>
      <HeroIconBlock
        icon="🔑"
        title="Forgot password"
        subtitle="Enter your account email. If it is registered, we will send a link to reset your password."
      />
      <Card>
        {error ? <ErrorBox message={error} /> : null}
        <FormField
          label="Email address"
          value={email}
          onChangeText={v => {
            setEmail(v);
            setError(null);
          }}
          placeholder="you@example.com"
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
        />
        <PrimaryBtn title="Send reset link" onPress={onSubmit} loading={loading} />
        <LinkBtn title="Back to sign in" onPress={() => navigation.navigate('Login')} />
      </Card>
    </ScreenShell>
  );
}
