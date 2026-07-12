import React, {useState} from 'react';
import {Alert, Pressable, StyleSheet, Text, TextInput, View} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {authApi} from '../services/api';
import {useAuthStore} from '../store/authStore';
import {RootStackParamList} from '../navigation/RootNavigator';
import {Card, HeroBlock, ScreenShell} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'DeleteAccount'>;

export function DeleteAccountScreen({navigation}: Props) {
  const logout = useAuthStore(s => s.logout);
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const confirmDelete = () => {
    if (password.trim().length < 4) {
      Alert.alert('Password required', 'Enter your account password to confirm deletion.');
      return;
    }
    Alert.alert(
      'Delete account permanently?',
      'This removes your profile, devices, monitoring history, and alerts. This cannot be undone.',
      [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Delete everything',
          style: 'destructive',
          onPress: async () => {
            setBusy(true);
            try {
              await authApi.deleteAccount(password.trim());
              await logout();
              Alert.alert('Account deleted', 'Your personal data has been removed.');
            } catch {
              Alert.alert('Deletion failed', 'Check your password and try again.');
            } finally {
              setBusy(false);
            }
          },
        },
      ],
    );
  };

  return (
    <ScreenShell backLabel="← Back" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Privacy"
        title="Delete account"
        subtitle="Permanently remove your account and all stored personal data."
      />
      <Card>
        <Text style={styles.warning}>
          This deletes your parent profile, paired devices, location history, notifications,
          alerts, and audit logs. Child phones will need to be set up again with a new account.
        </Text>
        <Text style={styles.label}>Confirm with your password</Text>
        <TextInput
          style={styles.input}
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoCapitalize="none"
          placeholder="Account password"
        />
        <Pressable
          style={[styles.deleteBtn, busy && styles.deleteBtnDisabled]}
          onPress={confirmDelete}
          disabled={busy}>
          <Text style={styles.deleteBtnText}>{busy ? 'Deleting…' : 'Delete my account'}</Text>
        </Pressable>
      </Card>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  warning: {fontSize: 14, color: '#7f1d1d', lineHeight: 20, marginBottom: 16},
  label: {fontSize: 13, fontWeight: '600', color: '#374151', marginBottom: 8},
  input: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 16,
    fontSize: 16,
  },
  deleteBtn: {
    backgroundColor: '#dc2626',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  deleteBtnDisabled: {opacity: 0.6},
  deleteBtnText: {color: '#fff', fontWeight: '700', fontSize: 15},
});
