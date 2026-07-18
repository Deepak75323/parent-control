import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  ActivityIndicator,
  Alert,
  AppState,
  AppStateStatus,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {useFocusEffect} from '@react-navigation/native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {PERMISSION_DEFS} from '../services/permissionDashboard/catalog';
import type {PermissionId} from '../services/permissionDashboard/types';
import {
  buildWizardStepOrder,
  grantWizardStep,
  resolveResumeStep,
  stepIndexInOrder,
  wizardStepCount,
} from '../services/permissionWizard/wizardSteps';
import {isPermissionGranted} from '../services/permissionWizard/permissionChecks';
import {
  getWizardStep,
  isOnboardingAuthenticated,
  setWizardStep,
} from '../services/onboarding/onboardingSession';
import {restoreDeviceCredentials, getDeviceHmacKey, getParentDisplayName} from '../services/deviceStorage';
import {nativeModules} from '../nativeModules';
import {cancelActivePermissionGrant} from '../services/permissionDashboard/grantOrchestrator';
import {flowLog} from '../services/flowLogger';
import {startMonitoringSync} from '../services/syncService';
import {Colors, Radii} from '../theme/design';

type Props = NativeStackScreenProps<ChildStackParamList, 'PermissionWizard'>;

function explainAlert(title: string, body: string): Promise<void> {
  return new Promise(resolve => {
    Alert.alert(title, body, [{text: 'Continue', onPress: () => resolve()}]);
  });
}

function explainThenOpen(title: string, body: string): Promise<void> {
  return new Promise(resolve => {
    Alert.alert(title, body, [
      {text: 'Cancel', style: 'cancel', onPress: () => resolve()},
      {text: 'Open Settings', onPress: () => resolve()},
    ]);
  });
}

const explainCallbacks = {
  onExplain: async (id: PermissionId, title: string, body: string) => {
    const def = PERMISSION_DEFS[id];
    if (def.kind === 'runtime' || def.kind === 'screen') {
      await explainAlert(title, body);
    } else {
      await explainThenOpen(title, body);
    }
  },
  onManualConfirm: (id: PermissionId, title: string, body: string) =>
    explainAlert(title, body),
};

export function PermissionWizardScreen({navigation}: Props) {
  const [stepId, setStepId] = useState<PermissionId | null>(null);
  const [order, setOrder] = useState<PermissionId[]>([]);
  const [granting, setGranting] = useState(false);
  const [denied, setDenied] = useState(false);
  const [deniedHint, setDeniedHint] = useState<string | null>(null);
  const [accessibilityNudge, setAccessibilityNudge] = useState<string | null>(null);
  const [booting, setBooting] = useState(true);
  const grantLock = useRef(false);
  const mountedRef = useRef(true);
  const stepIdRef = useRef<PermissionId | null>(null);
  const grantingRef = useRef(false);

  useEffect(() => {
    stepIdRef.current = stepId;
  }, [stepId]);

  useEffect(() => {
    grantingRef.current = granting;
  }, [granting]);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const advanceFrom = useCallback(
    async (grantedId: PermissionId) => {
      const stepOrder = order.length ? order : await buildWizardStepOrder();
      const idx = stepOrder.indexOf(grantedId);
      for (let i = idx + 1; i < stepOrder.length; i++) {
        const candidate = stepOrder[i];
        if (!(await isPermissionGranted(candidate))) {
          if (!mountedRef.current) return;
          setStepId(candidate);
          setDenied(false);
          await setWizardStep(candidate);
          flowLog('ONBOARDING', 'wizard advanced', {stepId: candidate});
          return;
        }
      }
      await finishWizard(navigation);
    },
    [navigation, order],
  );

  const validateCurrentStep = useCallback(async () => {
    if (!stepId) return;
    if (stepId === 'screenCapture' && !(await isPermissionGranted('accessibility'))) {
      setAccessibilityNudge(
        'Turn on Clock Time in device settings before screen sharing can continue. Enable it below.',
      );
      setStepId('accessibility');
      setDenied(false);
      setDeniedHint(null);
      await setWizardStep('accessibility');
      return;
    }
    if (await isPermissionGranted(stepId)) {
      setDenied(false);
      if (stepId === 'accessibility') {
        setAccessibilityNudge(null);
      }
      await advanceFrom(stepId);
    }
  }, [advanceFrom, stepId]);

  const loadStep = useCallback(async () => {
    const stepOrder = await buildWizardStepOrder();
    const saved = await getWizardStep();
    const next = await resolveResumeStep(saved);
    if (!mountedRef.current) return null;
    setOrder(stepOrder);
    setStepId(next);
    setBooting(false);
    if (next) {
      await setWizardStep(next);
    }
    return next;
  }, []);

  useFocusEffect(
    useCallback(() => {
      void nativeModules.setPermissionSetupInProgress(true);
      void isOnboardingAuthenticated().then(async ok => {
        if (!ok) {
          // Credentials were cleared — send back to login. If the device is
          // paired, returnTo=ServiceStatus so the user lands there after login
          // instead of being looped back here.
          const token = await restoreDeviceCredentials();
          navigation.replace('ChildLogin', token ? {returnTo: 'ServiceStatus'} : undefined);
        }
      });
      void loadStep().then(next => {
        if (next === null) {
          void finishWizard(navigation);
        }
      });
      return () => {
        void nativeModules.setPermissionSetupInProgress(false);
        if (stepIdRef.current !== 'screenCapture' || !grantingRef.current) {
          cancelActivePermissionGrant();
        }
      };
    }, [loadStep, navigation]),
  );

  useEffect(() => {
    const onState = (state: AppStateStatus) => {
      if (state === 'active') {
        void validateCurrentStep();
      }
      if (state === 'background' || state === 'inactive') {
        // System MediaProjection dialog backgrounds the app — keep auto-approve armed.
        if (stepIdRef.current === 'screenCapture' && grantingRef.current) {
          return;
        }
        cancelActivePermissionGrant();
      }
    };
    const sub = AppState.addEventListener('change', onState);
    return () => sub.remove();
  }, [validateCurrentStep]);

  const runGrant = useCallback(async () => {
    if (!stepId || grantLock.current || granting) return;

    if (stepId === 'screenCapture') {
      const a11yOk = await isPermissionGranted('accessibility');
      if (!a11yOk) {
        setAccessibilityNudge(
          'Turn on Clock Time in device settings before screen sharing can continue. Enable it below.',
        );
        setStepId('accessibility');
        setDenied(false);
        setDeniedHint(null);
        await setWizardStep('accessibility');
        flowLog('ONBOARDING', 'screen capture blocked — returning to accessibility');
        return;
      }
    }

    grantLock.current = true;
    setGranting(true);
    setDenied(false);
    setDeniedHint(null);
    try {
      flowLog('ONBOARDING', 'wizard grant start', {stepId});
      const ok = await grantWizardStep(stepId, explainCallbacks);
      if (!mountedRef.current) return;
      if (ok && (await isPermissionGranted(stepId))) {
        await advanceFrom(stepId);
      } else {
        setDenied(true);
        if (stepId === 'screenCapture') {
          const enabled = await nativeModules.isAccessibilityEnabled();
          const connected = await nativeModules.isAccessibilityServiceConnected();
          if (!enabled) {
            setDeniedHint(
              'Clock Time isn’t turned on in device settings yet. Complete that step first, then return here.',
            );
          } else if (!connected) {
            setDeniedHint(
              'Clock Time needs a quick refresh in device settings. Tap Open settings below, turn Clock Time off and on, then try again.',
            );
          } else {
            setDeniedHint(
              'Screen sharing wasn’t allowed. Tap Try again — it may finish automatically after you confirm.',
            );
          }
        }
        flowLog('ONBOARDING', 'wizard grant denied — staying on step', {stepId});
      }
    } finally {
      grantLock.current = false;
      if (mountedRef.current) {
        setGranting(false);
      }
    }
  }, [advanceFrom, granting, stepId]);

  useEffect(() => {
    if (!booting && stepId && !granting && !grantLock.current && !denied) {
      const timer = setTimeout(() => void runGrant(), 400);
      return () => clearTimeout(timer);
    }
  }, [booting, stepId, granting, runGrant, denied]);

  const def = stepId ? PERMISSION_DEFS[stepId] : null;
  const stepNum = stepId ? stepIndexInOrder(order, stepId) : 0;
  const total = wizardStepCount(order);

  const body = useMemo(() => {
    if (!def) return '';
    return [def.explanation, def.settingsHint].filter(Boolean).join('\n\n');
  }, [def]);

  if (booting) {
    return (
      <SafeAreaView style={styles.root}>
        <ActivityIndicator size="large" color={Colors.primaryContainer} />
        <Text style={styles.loading}>Loading setup…</Text>
      </SafeAreaView>
    );
  }

  if (!stepId || !def) {
    return (
      <SafeAreaView style={styles.root}>
        <ActivityIndicator size="large" color={Colors.primaryContainer} />
        <Text style={styles.loading}>Finishing setup…</Text>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <Text style={styles.eyebrow}>Permission setup</Text>
        <Text style={styles.stepLabel}>
          Step {stepNum} of {total}
        </Text>
        <Text style={styles.title}>{def.label}</Text>
      </View>

      <View style={styles.bodyCard}>
        <Text style={styles.body}>{body}</Text>
        {accessibilityNudge && stepId === 'accessibility' ? (
          <Text style={styles.nudge}>{accessibilityNudge}</Text>
        ) : null}
        {denied ? (
          <Text style={styles.denied}>
            {deniedHint ??
              'Permission not detected. Grant it on the system screen, then return here.'}
          </Text>
        ) : null}
      </View>

      <View style={styles.footer}>
        {granting ? (
          <View style={styles.grantingRow}>
            <ActivityIndicator color="#fff" />
            <Text style={styles.grantingText}>Waiting for permission…</Text>
          </View>
        ) : (
          <>
            <Pressable
              style={({pressed}) => [styles.primaryBtn, pressed && styles.primaryPressed]}
              onPress={() => void runGrant()}>
              <Text style={styles.primaryText}>
                {denied
                  ? 'Try again'
                  : def.kind === 'runtime'
                    ? 'Allow permission'
                    : def.kind === 'screen'
                      ? 'Allow screen sharing'
                      : 'Open Settings'}
              </Text>
            </Pressable>
            {denied && stepId === 'screenCapture' ? (
              <Pressable
                style={({pressed}) => [styles.secondaryBtn, pressed && styles.primaryPressed]}
                onPress={() => void nativeModules.openAccessibilitySettings()}>
                <Text style={styles.secondaryBtnText}>Open settings</Text>
              </Pressable>
            ) : null}
          </>
        )}
        <Text style={styles.hint}>
          {def.kind === 'runtime'
            ? 'Tap Allow when the phone asks.'
            : def.kind === 'screen'
              ? granting
                ? 'Please wait while screen sharing is set up…'
                : 'Next, allow screen sharing when asked. It may finish automatically after you confirm.'
              : 'Return here after turning on the permission in Settings.'}
        </Text>
      </View>
    </SafeAreaView>
  );
}

async function finishWizard(
  navigation: NativeStackScreenProps<ChildStackParamList, 'PermissionWizard'>['navigation'],
) {
  await nativeModules.setPermissionSetupInProgress(false);
  await setWizardStep(null);
  flowLog('ONBOARDING', 'wizard complete');
  // Always use replace-based routing so we don't return to whatever is behind
  // the wizard in the stack (e.g. DebugDashboard or CheckPermissions), which
  // would create an infinite loop if that screen re-navigates to the wizard.
  const token = await restoreDeviceCredentials();
  if (token) {
    const hmacKey = await getDeviceHmacKey();
    const name = await getParentDisplayName();
    await startMonitoringSync(token, hmacKey ?? undefined, name ?? undefined);
    navigation.replace('ServiceStatus');
  } else {
    navigation.replace('Pairing');
  }
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: Colors.background,
    paddingHorizontal: 20,
    justifyContent: 'space-between',
  },
  loading: {marginTop: 12, fontSize: 14, color: Colors.textSecondary, textAlign: 'center'},
  header: {paddingTop: 24},
  eyebrow: {
    fontSize: 12,
    fontWeight: '700',
    color: Colors.primaryContainer,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  stepLabel: {fontSize: 13, color: Colors.textTertiary, marginTop: 8},
  title: {fontSize: 24, fontWeight: '800', color: Colors.textPrimary, marginTop: 8},
  bodyCard: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: 20,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  body: {fontSize: 15, color: Colors.textSecondary, lineHeight: 22},
  denied: {
    marginTop: 16,
    fontSize: 14,
    color: Colors.danger,
    fontWeight: '600',
    lineHeight: 20,
  },
  nudge: {
    marginTop: 16,
    fontSize: 14,
    color: Colors.warning,
    fontWeight: '600',
    lineHeight: 20,
    backgroundColor: Colors.warningLight,
    padding: 12,
    borderRadius: Radii.md,
  },
  footer: {paddingBottom: 16},
  primaryBtn: {
    backgroundColor: Colors.primaryContainer,
    borderRadius: Radii.md,
    paddingVertical: 16,
    alignItems: 'center',
  },
  primaryPressed: {opacity: 0.9},
  primaryText: {color: '#fff', fontSize: 16, fontWeight: '700'},
  secondaryBtn: {
    marginTop: 10,
    backgroundColor: Colors.surface,
    borderRadius: Radii.md,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.primarySurface,
  },
  secondaryBtnText: {color: Colors.primaryContainer, fontSize: 15, fontWeight: '700'},
  grantingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.primaryContainer,
    borderRadius: Radii.md,
    paddingVertical: 16,
    gap: 10,
  },
  grantingText: {color: '#fff', fontSize: 15, fontWeight: '600'},
  hint: {
    textAlign: 'center',
    fontSize: 12,
    color: Colors.textTertiary,
    marginTop: 12,
    lineHeight: 17,
  },
});
