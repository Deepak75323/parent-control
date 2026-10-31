import React, {useState} from 'react';
import {Pressable, StyleSheet, Text, TextInput} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {authApi} from '../services/api';
import {useAuthStore} from '../store/authStore';
import {RootStackParamList} from '../navigation/RootNavigator';
import {Card, HeroBlock, ScreenShell} from '../components/AppScreen';
import {getApiErrorMessage} from '../utils/apiError';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'ChangePassword'>;

export function ChangePasswordScreen({navigation}: Props) {
  const logout = useAuthStore(s => s.logout);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const onSubmit = async () => {
    if (currentPassword.length < 1) {
      appAlert('Current password', 'Enter your current account password.');
      return;
    }
    if (newPassword.length < 8) {
      appAlert('New password', 'Use at least 8 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      appAlert('Mismatch', 'New password and confirmation do not match.');
      return;
    }
    setBusy(true);
    try {
      await authApi.changePassword(currentPassword, newPassword);
      await logout();
      appAlert('Password updated', 'Sign in again with your new password.');
    } catch (err) {
      appAlert('Could not update', getApiErrorMessage(err, 'Check your current password.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScreenShell backLabel="← Back" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Security"
        title="Change password"
        subtitle="You’ll be signed out on all devices after updating."
      />
      <Card>
        <Text style={styles.label}>Current password</Text>
        <TextInput
          style={styles.input}
          value={currentPassword}
          onChangeText={setCurrentPassword}
          secureTextEntry
          autoCapitalize="none"
          autoComplete="password"
          placeholder="Current password"
          editable={!busy}
        />
        <Text style={styles.label}>New password</Text>
        <TextInput
          style={styles.input}
          value={newPassword}
          onChangeText={setNewPassword}
          secureTextEntry
          autoCapitalize="none"
          autoComplete="password-new"
          placeholder="At least 8 characters"
          editable={!busy}
        />
        <Text style={styles.label}>Confirm new password</Text>
        <TextInput
          style={styles.input}
          value={confirmPassword}
          onChangeText={setConfirmPassword}
          secureTextEntry
          autoCapitalize="none"
          autoComplete="password-new"
          placeholder="Repeat new password"
          editable={!busy}
        />
        <Pressable
          style={[styles.submitBtn, busy && styles.submitBtnDisabled]}
          onPress={() => void onSubmit()}
          disabled={busy}>
          <Text style={styles.submitBtnText}>{busy ? 'Updating…' : 'Update password'}</Text>
        </Pressable>
      </Card>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  label: {marginTop: 12, marginBottom: 6, fontSize: 13, fontWeight: '600', color: '#334155'},
  input: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 15,
    color: '#0f172a',
    backgroundColor: '#fff',
  },
  submitBtn: {
    marginTop: 20,
    backgroundColor: '#4f46e5',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  submitBtnDisabled: {opacity: 0.6},
  submitBtnText: {color: '#fff', fontWeight: '700', fontSize: 15},
});
