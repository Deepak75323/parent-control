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
      Alert.alert('Password', 'Enter at least 4 characters');
      return;
    }
    setSaving(true);
    try {
      await parentPinApi.setPin(pin);
      Alert.alert('Saved', 'Parent password updated');
      const {data} = await parentPinApi.status();
      setStatus(data.data);
    } catch {
      Alert.alert('Error', 'Couldn’t save password. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const verifyPin = async () => {
    try {
      const {data} = await parentPinApi.verify(pin);
      if (data.data.verified) {
        Alert.alert('Verified', 'Screen sharing is unlocked for this account');
        setStatus(s => (s ? {...s, silentStreamUnlocked: true} : s));
      } else {
        Alert.alert('Incorrect password', 'Please try again');
      }
    } catch {
      Alert.alert('Error', 'Couldn’t verify password. Please try again.');
    }
  };

  return (
    <ScreenShell backLabel="← Back to settings" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Security"
        title="Parent password"
        subtitle="Used to protect screen sharing and sensitive parent actions."
      />

      {status ? (
        <Card>
          <View style={styles.statusRow}>
            <View style={[styles.badge, status.pinConfigured ? styles.badgeOk : styles.badgeWarn]}>
              <Text style={styles.badgeText}>
                Password {status.pinConfigured ? 'set' : 'not set'}
              </Text>
            </View>
            <View style={[styles.badge, status.silentStreamUnlocked ? styles.badgeOk : styles.badgeWarn]}>
              <Text style={styles.badgeText}>
                Screen sharing {status.silentStreamUnlocked ? 'unlocked' : 'locked'}
              </Text>
            </View>
          </View>
        </Card>
      ) : null}

      <Card>
        <FormField
          label="Enter password"
          value={pin}
          onChangeText={setPin}
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={64}
          placeholder="••••"
          style={styles.pinInput}
        />
        <PrimaryBtn title="Save password" onPress={savePin} loading={saving} />
        <Pressable style={styles.linkBtn} onPress={verifyPin}>
          <Text style={styles.linkText}>Verify & unlock screen sharing</Text>
        </Pressable>
      </Card>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  statusRow: {flexDirection: 'row', flexWrap: 'wrap', gap: 8},
  badge: {paddingHorizontal: 10, paddingVertical: 6, borderRadius: 999},
  badgeOk: {backgroundColor: '#dcfce7'},
  badgeWarn: {backgroundColor: '#fef3c7'},
  badgeText: {fontSize: 12, fontWeight: '700', color: '#334155'},
  pinInput: {letterSpacing: 4, fontSize: 20, textAlign: 'center'},
  linkBtn: {marginTop: 14, alignItems: 'center', padding: 8},
  linkText: {color: '#4f46e5', fontWeight: '700', fontSize: 14},
});
