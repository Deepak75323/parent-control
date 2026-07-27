import React, {useState} from 'react';
import {Pressable, StyleSheet, Text, TextInput} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {authApi} from '../services/api';
import {useAuthStore} from '../store/authStore';
import {RootStackParamList} from '../navigation/RootNavigator';
import {Card, HeroBlock, ScreenShell} from '../components/AppScreen';
import {getApiErrorMessage} from '../utils/apiError';
import {getCorrelationId} from '../utils/correlation';
import {captureException, track} from '../services/telemetry';
import {TelemetryEvents} from '../services/telemetryEvents';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'DeleteAccount'>;

const CONFIRM_WORD = 'DELETE';

export function DeleteAccountScreen({navigation}: Props) {
  const logout = useAuthStore(s => s.logout);
  const [password, setPassword] = useState('');
  const [confirmText, setConfirmText] = useState('');
  const [busy, setBusy] = useState(false);

  const confirmDelete = () => {
    if (password.trim().length < 4) {
      appAlert('Password required', 'Enter your account password to confirm deletion.');
      return;
    }
    if (confirmText.trim().toUpperCase() !== CONFIRM_WORD) {
      appAlert('Confirmation required', `Type ${CONFIRM_WORD} to confirm permanent deletion.`);
      return;
    }
    appAlert(
      'Delete account permanently?',
      'This removes your profile, devices, monitoring history, and alerts. Child phones stop monitoring and must be set up again. This cannot be undone.',
      [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Delete everything',
          style: 'destructive',
          onPress: async () => {
            setBusy(true);
            try {
              await authApi.deleteAccount(password.trim());
              track(TelemetryEvents.DELETE_ACCOUNT_SUCCESS);
              await logout();
              appAlert(
                'Account deleted',
                'Your personal data has been removed. Reference ID for your records: ' +
                  getCorrelationId(),
              );
            } catch (err) {
              track(TelemetryEvents.DELETE_ACCOUNT_FAILURE);
              captureException(err, {flow: 'delete_account'});
              appAlert(
                'Deletion failed',
                getApiErrorMessage(err, 'Check your password and try again.') +
                  `\n\nSupport ID: ${getCorrelationId()}`,
              );
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
          alerts, and audit logs. Active live streams stop. Child phones need a new pairing under
          another account (or a new registration).
        </Text>
        <Text style={styles.checklistTitle}>What is removed</Text>
        <Text style={styles.checklistItem}>• Login and profile</Text>
        <Text style={styles.checklistItem}>• Device links and monitoring history</Text>
        <Text style={styles.checklistItem}>• Alerts, policies, and subscription link</Text>

        <Text style={styles.label}>Confirm with your password</Text>
        <TextInput
          style={styles.input}
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoCapitalize="none"
          placeholder="Account password"
          editable={!busy}
        />
        <Text style={styles.label}>Type {CONFIRM_WORD} to confirm</Text>
        <TextInput
          style={styles.input}
          value={confirmText}
          onChangeText={setConfirmText}
          autoCapitalize="characters"
          autoCorrect={false}
          placeholder={CONFIRM_WORD}
          editable={!busy}
        />
        <Pressable
          style={[styles.deleteBtn, busy && styles.deleteBtnDisabled]}
          onPress={confirmDelete}
          disabled={busy}>
          <Text style={styles.deleteBtnText}>{busy ? 'Deleting…' : 'Delete my account'}</Text>
        </Pressable>
        <Pressable
          style={styles.supportLink}
          onPress={() => navigation.navigate('Support')}
          disabled={busy}>
          <Text style={styles.supportLinkText}>Need help? Contact support</Text>
        </Pressable>
      </Card>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  warning: {fontSize: 14, color: '#7f1d1d', lineHeight: 20, marginBottom: 12},
  checklistTitle: {fontSize: 13, fontWeight: '700', color: '#374151', marginBottom: 6},
  checklistItem: {fontSize: 13, color: '#64748b', lineHeight: 20},
  label: {fontSize: 13, fontWeight: '600', color: '#374151', marginBottom: 8, marginTop: 14},
  input: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 4,
    fontSize: 16,
  },
  deleteBtn: {
    marginTop: 16,
    backgroundColor: '#dc2626',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  deleteBtnDisabled: {opacity: 0.6},
  deleteBtnText: {color: '#fff', fontWeight: '700', fontSize: 15},
  supportLink: {marginTop: 14, alignItems: 'center', paddingVertical: 8},
  supportLinkText: {color: '#2563eb', fontWeight: '600', fontSize: 14},
});
