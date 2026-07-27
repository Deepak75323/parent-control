import React, {useEffect, useState} from 'react';
import {View, Text, StyleSheet} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {childLoginPasswordApi, familyHandleApi} from '../services/api';
import {Card, FormField, HeroBlock, PrimaryBtn, ScreenShell} from '../components/AppScreen';
import {getApiErrorMessage} from '../utils/apiError';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'FamilyHandle'>;

export function FamilyHandleScreen({navigation, route}: Props) {
  const afterSetup = route.params?.afterSetup;
  const pairingGate = afterSetup === 'pairing';
  const [handle, setHandle] = useState('');
  const [status, setStatus] = useState<{configured: boolean; familyHandle: string | null} | null>(
    null,
  );
  const [saving, setSaving] = useState(false);

  const loadStatus = () => {
    familyHandleApi
      .status()
      .then(r => {
        setStatus(r.data.data);
        if (r.data.data.familyHandle) {
          setHandle(r.data.data.familyHandle);
        }
      })
      .catch(() => undefined);
  };

  useEffect(() => {
    loadStatus();
  }, []);

  const continueAfterSave = async () => {
    if (!pairingGate) {
      appAlert('Saved', 'Children use this handle with your family password to link.');
      return;
    }
    try {
      const {data} = await childLoginPasswordApi.status();
      const pwOk =
        data.data?.configured === true || (data.data?.linkedDeviceCount ?? 0) > 0;
      if (pwOk) {
        navigation.replace('DeviceList');
      } else {
        navigation.replace('ChildLoginPassword', {afterSetup: 'DeviceList'});
      }
    } catch {
      navigation.replace('ChildLoginPassword', {afterSetup: 'DeviceList'});
    }
  };

  const saveHandle = async () => {
    const trimmed = handle.trim();
    if (trimmed.replace(/^@/, '').length < 3) {
      appAlert('Family handle', 'Enter at least 3 characters');
      return;
    }
    setSaving(true);
    try {
      const {data} = await familyHandleApi.setHandle(trimmed);
      setStatus(data.data);
      if (data.data.familyHandle) {
        setHandle(data.data.familyHandle);
      }
      await continueAfterSave();
    } catch (e) {
      appAlert('Couldn’t save', getApiErrorMessage(e, 'Handle may already be taken.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScreenShell
      backLabel={pairingGate ? '← Back' : '← Back to settings'}
      onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Family identity"
        title="Family handle"
        subtitle={
          pairingGate && !status?.configured
            ? 'Pick a unique handle once. Your child enters this with the family password to find you.'
            : 'Unique ID for your family. Children type this on first setup — like @mom.sharma.'
        }
      />

      {status ? (
        <Card>
          <View style={styles.statusRow}>
            <View style={[styles.badge, status.configured ? styles.badgeOk : styles.badgeWarn]}>
              <Text style={styles.badgeText}>
                {status.configured ? `@${status.familyHandle}` : 'Not set yet'}
              </Text>
            </View>
          </View>
          <Text style={styles.hint}>
            {status.configured
              ? 'You can change this anytime if the new handle is available.'
              : '3–20 characters: letters, numbers, dots, underscores, or hyphens.'}
          </Text>
        </Card>
      ) : null}

      <Card>
        <FormField
          label="Family handle"
          value={handle}
          onChangeText={setHandle}
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={32}
          placeholder="@mom.sharma"
        />
        <PrimaryBtn
          title={
            pairingGate && !status?.configured
              ? 'Save and continue'
              : status?.configured
                ? 'Update handle'
                : 'Save family handle'
          }
          onPress={() => void saveHandle()}
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
});
