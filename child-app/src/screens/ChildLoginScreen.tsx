import React, {useCallback, useState} from 'react';
import {Alert, Pressable, StyleSheet, Text, View} from 'react-native';
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
import {authenticateWithPassword} from '../services/onboarding/childLoginService';
import {flowLog} from '../services/flowLogger';
import {markJustAuthenticated} from '../services/autoLockGuard';

type Props = NativeStackScreenProps<ChildStackParamList, 'ChildLogin'>;

export function ChildLoginScreen({navigation, route}: Props) {
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const revokedByParent = route.params?.revokedByParent === true;

  const returnTo = route.params?.returnTo;

  const submit = useCallback(async () => {
    setBusy(true);
    setStatus(null);
    try {
      const result = await authenticateWithPassword(password);
      if (result.ok) {
        // Guard against a spurious foreground re-lock (e.g. a pending screen-share
        // request resolving its consent dialog) bouncing the child straight back here.
        markJustAuthenticated();
        if (returnTo === 'ServiceStatus') {
          flowLog('ONBOARDING', 'ChildLogin success — returning to ServiceStatus');
          navigation.replace('ServiceStatus');
        } else {
          flowLog('ONBOARDING', 'ChildLogin success — starting wizard');
          navigation.replace('PermissionWizard');
        }
        return;
      }
      setStatus(result.message);
    } finally {
      setBusy(false);
    }
  }, [password, navigation, returnTo]);

  return (
    <ScreenShell>
      {revokedByParent ? (
        <View style={styles.revokedBanner}>
          <Text style={styles.revokedTitle}>Monitoring ended</Text>
          <Text style={styles.revokedBody}>
            Monitoring has been ended by your parent. Enter the parent password to set up again.
          </Text>
        </View>
      ) : null}
      <HeroBlock
        eyebrow="Device Monitor"
        title="Parent password"
        subtitle="Enter the password set in the parent app to begin setup."
      />
      <Card>
        <FormField
          label="Parent password"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          placeholder="••••••••"
          editable={!busy}
          autoCapitalize="none"
        />
        {status ? <Text style={styles.status}>{status}</Text> : null}
        <PrimaryBtn
          title={busy ? 'Verifying…' : 'Continue'}
          onPress={() => void submit()}
          disabled={busy || password.trim().length < 4}
        />
        <View style={styles.linkRow}>
          <LinkBtn
            title="Forgot password? Get recovery code"
            onPress={() => navigation.navigate('ChildRecovery')}
          />
        </View>
      </Card>
      <Pressable
        onPress={() =>
          Alert.alert(
            'Why login?',
            'A parent must authorize setup before permissions are granted. This prevents bypassing monitoring protection.',
          )
        }>
        <Text style={styles.help}>Why is this required?</Text>
      </Pressable>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  revokedBanner: {
    backgroundColor: '#fef2f2',
    borderColor: '#fecaca',
    borderWidth: 1,
    borderRadius: 12,
    padding: 14,
    marginBottom: 16,
  },
  revokedTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#b91c1c',
    marginBottom: 4,
  },
  revokedBody: {
    fontSize: 13,
    color: '#7f1d1d',
    lineHeight: 18,
  },
  status: {
    fontSize: 13,
    color: '#dc2626',
    marginBottom: 12,
    lineHeight: 18,
  },
  linkRow: {marginTop: 12, alignItems: 'center'},
  help: {
    textAlign: 'center',
    fontSize: 12,
    color: '#94a3b8',
    marginTop: 16,
  },
});
