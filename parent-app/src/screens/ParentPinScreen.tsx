import React, {useEffect, useState} from 'react';
import {View, Text, StyleSheet, Alert, Pressable} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {parentPinApi} from '../services/api';
import {Card, FormField, HeroBlock, PrimaryBtn, ScreenShell} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'ParentPin'>;

export function ParentPinScreen({navigation}: Props) {
  const [pin, setPin] = useState('');
  const [status, setStatus] = useState<{pinConfigured: boolean; silentStreamUnlocked: boolean} | null>(
    null,
  );
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    parentPinApi.status().then(r => setStatus(r.data.data)).catch(() => undefined);
  }, []);

  const savePin = async () => {
    if (pin.length < 4) {
      Alert.alert('PIN', 'Enter at least 4 characters');
      return;
    }
    setSaving(true);
    try {
      await parentPinApi.setPin(pin);
      Alert.alert('Saved', 'Parent PIN updated');
      const {data} = await parentPinApi.status();
      setStatus(data.data);
    } catch {
      Alert.alert('Error', 'Could not save PIN');
    } finally {
      setSaving(false);
    }
  };

  const verifyPin = async () => {
    try {
      const {data} = await parentPinApi.verify(pin);
      if (data.data.verified) {
        Alert.alert('Verified', 'Silent screen capture unlocked for this account');
        setStatus(s => (s ? {...s, silentStreamUnlocked: true} : s));
      } else {
        Alert.alert('Invalid PIN', 'Try again');
      }
    } catch {
      Alert.alert('Error', 'Verification failed');
    }
  };

  return (
    <ScreenShell backLabel="← Back to settings" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Security"
        title="Parent PIN"
        subtitle="Required once before silent screen monitoring on Full Protection devices."
      />

      {status ? (
        <Card>
          <View style={styles.statusRow}>
            <View style={[styles.badge, status.pinConfigured ? styles.badgeOk : styles.badgeWarn]}>
              <Text style={styles.badgeText}>
                PIN {status.pinConfigured ? 'configured' : 'not set'}
              </Text>
            </View>
            <View style={[styles.badge, status.silentStreamUnlocked ? styles.badgeOk : styles.badgeWarn]}>
              <Text style={styles.badgeText}>
                Silent stream {status.silentStreamUnlocked ? 'unlocked' : 'locked'}
              </Text>
            </View>
          </View>
        </Card>
      ) : null}

      <Card>
        <FormField
          label="Enter PIN"
          value={pin}
          onChangeText={setPin}
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={64}
          placeholder="••••"
          style={styles.pinInput}
        />
        <PrimaryBtn title="Save PIN" onPress={savePin} loading={saving} />
        <Pressable style={styles.linkBtn} onPress={verifyPin}>
          <Text style={styles.linkText}>Verify & unlock silent screen</Text>
        </Pressable>
      </Card>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  statusRow: {flexDirection: 'row', flexWrap: 'wrap', gap: 8},
  badge: {borderRadius: 20, paddingHorizontal: 12, paddingVertical: 6},
  badgeOk: {backgroundColor: '#dcfce7'},
  badgeWarn: {backgroundColor: '#fef3c7'},
  badgeText: {fontSize: 12, fontWeight: '600', color: '#334155'},
  pinInput: {fontSize: 22, textAlign: 'center', letterSpacing: 8},
  linkBtn: {marginTop: 12, paddingVertical: 10, alignItems: 'center'},
  linkText: {color: '#2563eb', fontWeight: '600', fontSize: 14},
});
