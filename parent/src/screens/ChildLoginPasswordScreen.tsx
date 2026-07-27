import React, {useEffect, useState} from 'react';
import {View, Text, StyleSheet} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {childLoginPasswordApi} from '../services/api';
import {Card, FormField, HeroBlock, PrimaryBtn, ScreenShell} from '../components/AppScreen';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildLoginPassword'>;

export function ChildLoginPasswordScreen({navigation, route}: Props) {
  const afterSetup = route.params?.afterSetup;
  const pairingGate = afterSetup === 'DeviceList';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [status, setStatus] = useState<{configured: boolean; linkedDeviceCount: number} | null>(
    null,
  );
  const [saving, setSaving] = useState(false);

  const loadStatus = () => {
    childLoginPasswordApi
      .status()
      .then(r => setStatus(r.data.data))
      .catch(() => undefined);
  };

  useEffect(() => {
    loadStatus();
  }, []);

  const savePassword = async () => {
    const trimmed = password.trim();
    const trimmedConfirm = confirm.trim();
    if (trimmed.length < 4) {
      appAlert('Password', 'Enter at least 4 characters');
      return;
    }
    if (trimmed !== trimmedConfirm) {
      appAlert('Password', 'Passwords do not match');
      return;
    }
    setSaving(true);
    try {
      await childLoginPasswordApi.setPassword(trimmed);
      const wasFirstSetup = !status?.configured;
      loadStatus();
      setPassword('');
      setConfirm('');
      if (wasFirstSetup && pairingGate) {
        navigation.replace('DeviceList');
        return;
      }
      appAlert(
        'Saved',
        status?.configured
          ? `Password updated on all ${status.linkedDeviceCount} linked device(s).`
          : 'Family child login password saved. Use this on every child phone.',
      );
    } catch {
      appAlert('Error', 'Couldn’t save password. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScreenShell
      backLabel={pairingGate ? '← Back' : '← Back to settings'}
      onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Family password"
        title="Child login password"
        subtitle={
          pairingGate && !status?.configured
            ? 'Set this once — every child uses the same password. You can pair devices right after.'
            : 'One password for every child device. Children enter this to open the child app — not your parent app login.'
        }
      />

      {status ? (
        <Card>
          <View style={styles.statusRow}>
            <View style={[styles.badge, status.configured ? styles.badgeOk : styles.badgeWarn]}>
              <Text style={styles.badgeText}>
                {status.configured ? 'Password set' : 'Not set yet'}
              </Text>
            </View>
            <View style={styles.badge}>
              <Text style={styles.badgeText}>
                {status.linkedDeviceCount} linked device{status.linkedDeviceCount === 1 ? '' : 's'}
              </Text>
            </View>
          </View>
          {status.configured ? (
            <Text style={styles.hint}>
              Changing this password updates every linked child device immediately.
            </Text>
          ) : (
            <Text style={styles.hint}>
              Set this before generating a pairing code. All children use the same password.
            </Text>
          )}
        </Card>
      ) : null}

      <Card>
        <FormField
          label={status?.configured ? 'New password' : 'Password'}
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={128}
          placeholder="Min 4 characters"
        />
        <FormField
          label="Confirm password"
          value={confirm}
          onChangeText={setConfirm}
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={128}
          placeholder="Repeat password"
          style={styles.fieldGap}
        />
        <PrimaryBtn
          title={
            pairingGate && !status?.configured
              ? 'Save and continue to pairing'
              : status?.configured
                ? 'Update for all children'
                : 'Save family password'
          }
          onPress={savePassword}
          loading={saving}
        />
      </Card>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  statusRow: {flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 10},
  badge: {borderRadius: 20, paddingHorizontal: 12, paddingVertical: 6, backgroundColor: '#f1f5f9'},
  badgeOk: {backgroundColor: '#dcfce7'},
  badgeWarn: {backgroundColor: '#fef3c7'},
  badgeText: {fontSize: 12, fontWeight: '600', color: '#334155'},
  hint: {fontSize: 13, color: '#64748b', lineHeight: 20},
  fieldGap: {marginTop: 12},
});
