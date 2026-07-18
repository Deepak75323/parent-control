import React, {useState} from 'react';
import {View, Text, Pressable, StyleSheet, ActivityIndicator} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {useAuthStore} from '../store/authStore';
import {authApi} from '../services/api';
import {getApiErrorMessage} from '../utils/apiError';

type Nav = NativeStackNavigationProp<RootStackParamList>;

export function EmailVerificationBanner() {
  const navigation = useNavigation<Nav>();
  const emailVerified = useAuthStore(s => s.emailVerified);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (emailVerified) {
    return null;
  }

  const onResend = async () => {
    setError(null);
    setResending(true);
    try {
      await authApi.resendVerification();
    } catch (err) {
      setError(getApiErrorMessage(err, 'Couldn’t resend the email. Please try again.'));
    } finally {
      setResending(false);
    }
  };

  return (
    <View style={s.wrap}>
      <Text style={s.title}>Verify your email to start your trial</Text>
      <Text style={s.sub}>Check your inbox for the verification link, or paste the code from the email below.</Text>
      {error ? <Text style={s.error}>{error}</Text> : null}
      <View style={s.row}>
        <Pressable style={s.primaryBtn} onPress={() => navigation.navigate('VerifyEmail')}>
          <Text style={s.primaryText}>Enter code</Text>
        </Pressable>
        <Pressable style={s.secondaryBtn} onPress={onResend} disabled={resending}>
          {resending ? (
            <ActivityIndicator color="#3730a3" size="small" />
          ) : (
            <Text style={s.secondaryText}>Resend email</Text>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  wrap: {
    marginHorizontal: 16,
    marginTop: 8,
    marginBottom: 4,
    padding: 14,
    borderRadius: 14,
    backgroundColor: '#eef2ff',
    borderWidth: 1,
    borderColor: '#c7d2fe',
  },
  title: {fontSize: 14, fontWeight: '800', color: '#312e81'},
  sub: {fontSize: 13, color: '#4338ca', marginTop: 4, lineHeight: 18},
  error: {fontSize: 12, color: '#991b1b', marginTop: 8},
  row: {flexDirection: 'row', gap: 10, marginTop: 12},
  primaryBtn: {
    flex: 1,
    backgroundColor: '#4f46e5',
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: 'center',
  },
  primaryText: {color: '#fff', fontWeight: '700', fontSize: 13},
  secondaryBtn: {
    flex: 1,
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: 'center',
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#c7d2fe',
  },
  secondaryText: {color: '#3730a3', fontWeight: '700', fontSize: 13},
});
