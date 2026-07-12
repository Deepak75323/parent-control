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
import {restoreDeviceCredentials} from '../services/deviceStorage';
import {flowLog} from '../services/flowLogger';
import {markJustAuthenticated} from '../services/autoLockGuard';
import {
  navTraceAuth,
  navTraceNavCall,
  navTracePostNav,
  navTraceTransition,
} from '../navigation/navTrace';
import {useNavTraceLifecycle} from '../navigation/useNavTraceLifecycle';
import {blockDebugDashboardReopen} from '../navigation/navigationInputGuard';

type Props = NativeStackScreenProps<ChildStackParamList, 'ChildLogin'>;

export function ChildLoginScreen({navigation, route}: Props) {
  useNavTraceLifecycle('ChildLogin', () => ({
    returnTo: route.params?.returnTo ?? null,
    revokedByParent: route.params?.revokedByParent ?? false,
  }));

  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const revokedByParent = route.params?.revokedByParent === true;

  const returnTo = route.params?.returnTo;

  const submit = useCallback(async () => {
    setBusy(true);
    setStatus(null);
    navTraceAuth('ChildLogin submit started', {loginRedirectRequested: false});
    try {
      const result = await authenticateWithPassword(password);
      if (result.ok) {
        markJustAuthenticated();
        blockDebugDashboardReopen();
        navTraceAuth('ChildLogin authentication success', {
          authenticated: true,
          loginRedirectRequested: true,
        });
        if (returnTo === 'ServiceStatus') {
          flowLog('ONBOARDING', 'ChildLogin success — returning to ServiceStatus (param)');
          navTraceTransition('ChildLogin', 'ServiceStatus', 'Login Success (returnTo param)', 'ChildLoginScreen.submit');
          navTraceNavCall('replace', 'ChildLoginScreen.submit', 'ServiceStatus', 'before');
          navigation.replace('ServiceStatus');
          navTraceNavCall('replace', 'ChildLoginScreen.submit', 'ServiceStatus', 'after');
          navTracePostNav('ChildLoginScreen.submit');
        } else {
          const token = await restoreDeviceCredentials();
          navTraceAuth('ChildLogin post-auth token check', {tokenExists: !!token});
          if (token) {
            flowLog('ONBOARDING', 'ChildLogin success — returning to ServiceStatus (paired fallback)');
            navTraceTransition('ChildLogin', 'ServiceStatus', 'Login Success (paired fallback)', 'ChildLoginScreen.submit');
            navTraceNavCall('replace', 'ChildLoginScreen.submit', 'ServiceStatus', 'before');
            navigation.replace('ServiceStatus');
            navTraceNavCall('replace', 'ChildLoginScreen.submit', 'ServiceStatus', 'after');
            navTracePostNav('ChildLoginScreen.submit');
          } else {
            flowLog('ONBOARDING', 'ChildLogin success — starting wizard');
            navTraceTransition('ChildLogin', 'PermissionWizard', 'Login Success (unpaired)', 'ChildLoginScreen.submit');
            navTraceNavCall('replace', 'ChildLoginScreen.submit', 'PermissionWizard', 'before');
            navigation.replace('PermissionWizard');
            navTraceNavCall('replace', 'ChildLoginScreen.submit', 'PermissionWizard', 'after');
            navTracePostNav('ChildLoginScreen.submit');
          }
        }
        return;
      }
      navTraceAuth('ChildLogin authentication failed', {authenticated: false});
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
            onPress={() => {
              navTraceNavCall('navigate', 'ChildLoginScreen.recoveryLink', 'ChildRecovery', 'before');
              navigation.navigate('ChildRecovery');
              navTraceNavCall('navigate', 'ChildLoginScreen.recoveryLink', 'ChildRecovery', 'after');
            }}
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
