import React, {useState} from 'react';
import {Alert, StyleSheet, Text} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {
  Card,
  FormField,
  HeroBlock,
  LinkBtn,
  PrimaryBtn,
  ScreenShell,
} from '../components/AppScreen';

type Props = NativeStackScreenProps<ChildStackParamList, 'PinVerification'>;

export function PinVerificationScreen({navigation}: Props) {
  const [pin, setPin] = useState('');

  const verify = () => {
    Alert.alert('PIN', pin.length >= 4 ? 'Verify via API — Phase 3' : 'Enter parent PIN');
  };

  return (
    <ScreenShell backLabel="← Back" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Security"
        title="Parent PIN"
        subtitle="Required to change settings or uninstall monitoring on this device."
      />
      <Card>
        <FormField
          label="Enter PIN"
          value={pin}
          onChangeText={setPin}
          secureTextEntry
          keyboardType="number-pad"
          maxLength={8}
          placeholder="••••"
          style={styles.pinInput}
        />
        <PrimaryBtn title="Verify PIN" onPress={verify} />
        <LinkBtn title="Contact parent if you forgot the PIN" onPress={() => navigation.goBack()} />
      </Card>
      <Text style={styles.footer}>Your parent sets this PIN in the parent app under Settings.</Text>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  pinInput: {fontSize: 22, textAlign: 'center', letterSpacing: 8},
  footer: {textAlign: 'center', fontSize: 12, color: '#94a3b8', marginTop: 8, lineHeight: 18},
});
