import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  TextInput,
  StyleSheet,
  Text,
  Pressable,
  ScrollView,
  View,
  KeyboardAvoidingView,
  Platform,
  Animated,
  StatusBar,
  ActivityIndicator,
  InteractionManager,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useFocusEffect} from '@react-navigation/native';
import {ChildStackParamList, usePairingComplete} from './RootNavigator';
import {deviceApi} from '../services/api';
import {saveDeviceCredentials} from '../services/deviceStorage';
import {startMonitoringSync} from '../services/syncService';
import {
  isPermissionStatusComplete,
  loadPermissionStatus,
} from '../services/permissionDashboard/permissionStatus';
import {getPendingPairingCode} from '../services/onboarding/onboardingSession';
import {nativeModules} from '../nativeModules';
import {getApiBaseUrl, isApiReachable} from '../utils/config';
import {slog} from '../services/streamDebug';
import {Colors, Radii} from '../theme/design';

type Props = NativeStackScreenProps<ChildStackParamList, 'Pairing'>;

const CODE_LENGTH = 8;

function CodeBoxes({code, focused}: {code: string; focused: boolean}) {
  const chars = code.padEnd(CODE_LENGTH, ' ').split('').slice(0, CODE_LENGTH);
  return (
    <View style={s.codeRow}>
      {chars.map((ch, i) => {
        const filled = ch.trim().length > 0;
        const active = focused && i === code.length && code.length < CODE_LENGTH;
        return (
          <View
            key={i}
            style={[
              s.codeBox,
              filled && s.codeBoxFilled,
              active && s.codeBoxActive,
              i === 3 && s.codeBoxGap,
            ]}>
            <Text style={[s.codeChar, filled && s.codeCharFilled]}>
              {filled ? ch : '·'}
            </Text>
          </View>
        );
      })}
    </View>
  );
}

export function PairingScreen({navigation}: Props) {
  const {markPaired} = usePairingComplete();
  const inputRef = useRef<TextInput>(null);
  const [code, setCode] = useState('');
  const [focused, setFocused] = useState(false);
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [serverUrl, setServerUrl] = useState(getApiBaseUrl());
  const [serverOk, setServerOk] = useState<boolean | null>(null);
  const pairingRef = useRef(false);

  const heroAnim = useRef(new Animated.Value(0)).current;
  const cardAnim = useRef(new Animated.Value(24)).current;
  const cardOpacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(heroAnim, {toValue: 1, duration: 550, useNativeDriver: true}),
      Animated.sequence([
        Animated.delay(200),
        Animated.parallel([
          Animated.timing(cardAnim, {toValue: 0, duration: 500, useNativeDriver: true}),
          Animated.timing(cardOpacity, {toValue: 1, duration: 500, useNativeDriver: true}),
        ]),
      ]),
    ]).start();
  }, []);

  const checkServer = useCallback(async (force = false) => {
    setServerOk(null);
    const ok = await isApiReachable(force);
    setServerUrl(getApiBaseUrl());
    setServerOk(ok);
  }, []);

  useEffect(() => { void checkServer(); }, [checkServer]);

  const focusCodeInput = useCallback(() => {
    if (loading || done) return;
    InteractionManager.runAfterInteractions(() => {
      const input = inputRef.current;
      if (!input) return;
      if (input.isFocused()) {
        input.blur();
        setTimeout(() => input.focus(), 80);
      } else {
        input.focus();
      }
    });
  }, [done, loading]);

  useFocusEffect(
    useCallback(() => {
      void loadPermissionStatus().then(status => {
        if (!isPermissionStatusComplete(status)) {
          navigation.replace('PermissionWizard');
        }
      });
      void getPendingPairingCode().then(saved => {
        if (saved) {
          setCode(prev => (prev.trim() ? prev : saved));
        }
      });
      const t = setTimeout(() => focusCodeInput(), 350);
      return () => clearTimeout(t);
    }, [focusCodeInput, navigation]),
  );

  const pair = useCallback(async (codeOverride?: string) => {
    if (done || pairingRef.current) return;
    const permStatus = await loadPermissionStatus();
    if (!isPermissionStatusComplete(permStatus)) {
      setError('Complete all permissions in setup before pairing.');
      navigation.replace('PermissionWizard');
      return;
    }
    const normalized = (codeOverride ?? code).trim().toUpperCase();
    if (normalized.length < CODE_LENGTH) {
      setError('Enter the full 8-character code from the parent app.'); return;
    }
    pairingRef.current = true;
    setLoading(true);
    setError(null);
    try {
      const ok = await isApiReachable(true);
      setServerUrl(getApiBaseUrl());
      setServerOk(ok);
      if (!ok) {
        setError(
          'Cannot reach the server on your Mac. Keep the phone plugged in via USB, then on your Mac run: ./scripts/dev/setup-phone.sh — or use the same Wi‑Fi and set DEV_LAN_HOST in child/.env.',
        );
        return;
      }
      const androidId = (await nativeModules.getAndroidId())?.trim();
      if (!androidId) { setError('Could not read this phone\'s Android ID. Restart the app and try again.'); return; }
      const deviceInfo = await nativeModules.getDeviceInfo();
      const attestation = await nativeModules.getDeviceAttestation('pair');
      // Fetch FCM token best-effort so the backend can wake this device immediately after pairing
      // without waiting for the first heartbeat cycle.
      const fcmToken = await nativeModules.getFcmToken().catch(() => null);
      slog('PAIR', 'attempt', {code: '[REDACTED]', api: getApiBaseUrl(), deviceInfo, hasFcmToken: !!fcmToken});
      const {data} = await deviceApi.pair({
        pairingCode: normalized,
        deviceName: deviceInfo.deviceName,
        deviceModel: deviceInfo.deviceModel,
        androidId,
        consentAccepted: 'true',
        osVersion: deviceInfo.osVersion,
        appVersion: deviceInfo.appVersion,
        integrityToken: attestation.integrityToken ?? undefined,
        trustSignals: attestation.trustSignals,
        fcmToken: fcmToken ?? undefined,
      });
      if (!data.success || !data.data?.deviceToken) { setError(data.message ?? 'Could not pair this device.'); return; }
      const displayName = data.data.parentDisplayName?.trim() || 'Parent';
      await saveDeviceCredentials(data.data.deviceToken, data.data.deviceHmacKey, displayName);
      setDone(true);
      markPaired(displayName);
      void startMonitoringSync(data.data.deviceToken, data.data.deviceHmacKey, displayName)
        .catch(e => slog('PAIR', 'startMonitoringSync failed', String(e)));
    } catch (e: unknown) {
      const axiosMsg = (e as {response?: {data?: {message?: string}}})?.response?.data?.message;
      const networkMsg = (e as {message?: string})?.message;
      const hasHttpResponse = !!(e as {response?: unknown})?.response;
      const message = axiosMsg === 'DEVICE_TRUST_DENIED'
        ? 'This device failed security checks (rooted, emulator, or modified app). Use a stock device from Google Play.'
        : axiosMsg === 'PAIRING_CODE_EXPIRED'
        ? 'This code expired. Generate a new one in the parent app.'
        : axiosMsg === 'PAIRING_CODE_INVALID' || axiosMsg === 'Pairing code already used'
          ? 'Wrong or used code. Generate a fresh code in the parent app.'
          : axiosMsg ?? (networkMsg?.includes('Network Error') ? 'Cannot reach the server. USB: run ./scripts/dev/setup-phone.sh on your Mac. Wi‑Fi: same network + DEV_LAN_HOST in .env.' : networkMsg) ?? 'Invalid or expired code.';
      setError(message);
      // Keep "Connected" when the server responded (e.g. invalid code) — only mark offline for network failures.
      setServerOk(
        hasHttpResponse ||
          (!networkMsg?.includes('Network Error') &&
            !message.includes('Cannot reach the server')),
      );
      setServerUrl(getApiBaseUrl());
    } finally {
      pairingRef.current = false;
      setLoading(false);
    }
  }, [code, done, markPaired, navigation]);

  const onCodeChange = (text: string) => {
    const next = text.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, CODE_LENGTH);
    setCode(next);
    setError(null);
    if (next.length === CODE_LENGTH && !loading && !done && !pairingRef.current) {
      void pair(next);
    }
  };

  useEffect(() => {
    const t = setTimeout(() => focusCodeInput(), 400);
    return () => clearTimeout(t);
  }, [focusCodeInput]);

  const connectionLabel = serverOk === null ? '⏳ Checking…' : serverOk ? '✓ Connected' : '✗ Offline';
  const connectionColor = serverOk === null ? Colors.textTertiary : serverOk ? Colors.success : Colors.danger;
  const connectionBg = serverOk === null ? Colors.borderLight : serverOk ? Colors.successLight : Colors.dangerLight;
  const remaining = CODE_LENGTH - code.length;
  const canPair = code.length === CODE_LENGTH && !loading && !done;

  return (
    <SafeAreaView style={s.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="light-content" backgroundColor={Colors.primaryContainer} />
      <KeyboardAvoidingView style={s.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={s.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          {/* Hero */}
          <Animated.View style={[s.hero, {opacity: heroAnim}]}>
            <View style={s.heroGradientBase} />
            <View style={s.heroGradientOverlay} />
            <View style={s.heroBubble1} />
            <View style={s.heroBubble2} />
            <Pressable
              style={s.backBtn}
              onPress={() => {
                if (navigation.canGoBack()) {
                  navigation.goBack();
                } else {
                  navigation.replace('PermissionWizard');
                }
              }}>
              <Text style={s.backBtnText}>‹</Text>
            </Pressable>
            <View style={s.heroIconWrap}><Text style={{fontSize: 36}}>🔗</Text></View>
            <Text style={s.heroTitle}>Link to parent</Text>
            <Text style={s.heroSub}>Enter the code shown in the parent app to connect this device.</Text>
          </Animated.View>

          {/* Steps */}
          <Animated.View style={[s.card, {opacity: cardOpacity, transform: [{translateY: cardAnim}]}]}>
            <Text style={s.cardTitle}>HOW IT WORKS</Text>
            {[
              {n: '1', text: 'Open parent app → Devices tab'},
              {n: '2', text: 'Tap "Generate pairing code"'},
              {n: '3', text: 'Enter the 8-character code below'},
            ].map((step, i) => (
              <View key={step.n}>
                {i > 0 && <View style={s.stepDivider} />}
                <View style={s.step}>
                  <View style={s.stepNum}><Text style={s.stepNumText}>{step.n}</Text></View>
                  <Text style={s.stepText}>{step.text}</Text>
                </View>
              </View>
            ))}
          </Animated.View>

          {/* Server status */}
          <Animated.View style={[s.serverCard, {backgroundColor: connectionBg, opacity: cardOpacity}]}>
            <View style={s.serverLeft}>
              <View style={[s.statusDot, {backgroundColor: connectionColor}]} />
              <View>
                <Text style={[s.serverStatus, {color: connectionColor}]}>{connectionLabel}</Text>
                <Text style={s.serverUrl} numberOfLines={1}>{serverUrl}</Text>
              </View>
            </View>
            {serverOk === false ? (
              <Pressable style={s.retryBtn} onPress={() => void checkServer(true)}>
                <Text style={s.retryBtnText}>Retry</Text>
              </Pressable>
            ) : null}
          </Animated.View>

          {serverOk === false ? (
            <Animated.View style={[s.helpCard, {opacity: cardOpacity}]}>
              <Text style={s.helpTitle}>Phone cannot reach your Mac</Text>
              <Text style={s.helpText}>
                1. Keep the phone connected with a USB cable{'\n'}
                2. On your Mac, open Terminal in the project folder{'\n'}
                3. Run: ./scripts/dev/setup-phone.sh{'\n'}
                4. Tap Retry above — you should see ✓ Connected
              </Text>
            </Animated.View>
          ) : null}

          {/* Code entry */}
          <Animated.View style={[{opacity: cardOpacity, transform: [{translateY: cardAnim}]}]}>
            {done ? (
              <View style={s.successCard}>
                <View style={s.successCheck}><Text style={{fontSize: 28}}>✓</Text></View>
                <Text style={s.successTitle}>Paired successfully!</Text>
                <Text style={s.successSub}>Your device is now protected</Text>
              </View>
            ) : (
              <View style={s.codeCard}>
                <Text style={s.codeLabel}>Pairing code</Text>
                <View style={s.codeTapZone}>
                  <CodeBoxes code={code} focused={focused} />
                </View>
                <Text style={s.codeHint}>
                  {loading
                    ? '⏳ Pairing…'
                    : code.length === 0
                      ? 'Tap to enter the 8-character code'
                      : remaining > 0
                        ? `${code.length}/${CODE_LENGTH} — enter ${remaining} more to enable Pair device`
                        : 'Ready — tap Pair device'}
                </Text>
                <TextInput
                  ref={inputRef}
                  style={s.codeInputOverlay}
                  value={code}
                  onChangeText={onCodeChange}
                  onFocus={() => setFocused(true)}
                  onBlur={() => setFocused(false)}
                  onPressIn={focusCodeInput}
                  autoCapitalize="characters"
                  autoCorrect={false}
                  autoComplete="off"
                  importantForAutofill="no"
                  maxLength={CODE_LENGTH}
                  editable={!loading && !done}
                  caretHidden
                  showSoftInputOnFocus
                  keyboardType="visible-password"
                />
              </View>
            )}
          </Animated.View>

          {error ? (
            <View style={s.errorBox}>
              <Text style={{fontSize: 15}}>⚠️</Text>
              <Text style={s.errorText}>{error}</Text>
            </View>
          ) : null}

          {!done ? (
            <Pressable
              style={({pressed}) => [
                s.pairBtn,
                pressed && canPair && {opacity: 0.87},
                !canPair && s.pairBtnDisabled,
              ]}
              onPress={() => void pair()}
              disabled={!canPair}>
              {loading
                ? <ActivityIndicator color="#fff" />
                : <Text style={s.pairBtnText}>Pair device  →</Text>
              }
            </Pressable>
          ) : null}

          <Text style={s.footerNote}>Codes expire in 15 minutes and work only once.</Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.background},
  flex: {flex: 1},
  scroll: {paddingHorizontal: 20, paddingBottom: 40},

  // Hero
  hero: {borderRadius: Radii.xl, padding: 24, marginTop: 16, marginBottom: 16, overflow: 'hidden', position: 'relative'},
  heroGradientBase: {...StyleSheet.absoluteFillObject, backgroundColor: Colors.primaryContainer},
  heroGradientOverlay: {position: 'absolute', top: 0, right: 0, bottom: 0, left: '35%', backgroundColor: Colors.accent, opacity: 0.88},
  heroBubble1: {position: 'absolute', width: 200, height: 200, borderRadius: 100, backgroundColor: 'rgba(255,255,255,0.08)', top: -70, right: -50},
  heroBubble2: {position: 'absolute', width: 140, height: 140, borderRadius: 70, backgroundColor: 'rgba(255,255,255,0.06)', bottom: -40, left: -30},
  backBtn: {position: 'absolute', top: 14, left: 14, width: 34, height: 34, borderRadius: 11, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center'},
  backBtnText: {fontSize: 22, color: '#fff', fontWeight: '700', lineHeight: 28},
  heroIconWrap: {width: 70, height: 70, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center', marginBottom: 14, alignSelf: 'center', borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)'},
  heroTitle: {fontSize: 24, fontWeight: '900', color: '#fff', textAlign: 'center', marginBottom: 6, letterSpacing: -0.3},
  heroSub: {fontSize: 14, color: Colors.textOnDarkSoft, textAlign: 'center', lineHeight: 20},

  // Card
  card: {backgroundColor: Colors.surface, borderRadius: Radii.xl, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: Colors.border, shadowColor: Colors.primaryContainer, shadowOffset: {width: 0, height: 3}, shadowOpacity: 0.06, shadowRadius: 12, elevation: 3},
  cardTitle: {fontSize: 11, fontWeight: '700', color: Colors.textTertiary, letterSpacing: 0.6, marginBottom: 12},
  step: {flexDirection: 'row', alignItems: 'center', paddingVertical: 10},
  stepDivider: {height: 1, backgroundColor: Colors.border, marginLeft: 44},
  stepNum: {width: 28, height: 28, borderRadius: 14, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginRight: 14},
  stepNumText: {color: Colors.primaryContainer, fontWeight: '800', fontSize: 13},
  stepText: {flex: 1, color: Colors.textSecondary, fontSize: 14, fontWeight: '500'},

  // Server
  serverCard: {borderRadius: Radii.xl, padding: 14, marginBottom: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  serverLeft: {flexDirection: 'row', alignItems: 'center', flex: 1, gap: 10},
  statusDot: {width: 10, height: 10, borderRadius: 5},
  serverStatus: {fontSize: 13, fontWeight: '700'},
  serverUrl: {fontSize: 11, color: Colors.textTertiary, marginTop: 2},
  retryBtn: {backgroundColor: Colors.primarySurface, paddingHorizontal: 14, paddingVertical: 8, borderRadius: Radii.md, borderWidth: 1, borderColor: Colors.outlineVariant},
  retryBtnText: {color: Colors.primaryContainer, fontWeight: '700', fontSize: 13},

  helpCard: {backgroundColor: Colors.warningLight, borderRadius: Radii.xl, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: Colors.warning},
  helpTitle: {fontSize: 14, fontWeight: '800', color: Colors.warning, marginBottom: 8},
  helpText: {fontSize: 13, color: Colors.textSecondary, lineHeight: 20},

  // Code card
  codeCard: {backgroundColor: Colors.surface, borderRadius: Radii.xl, padding: 22, marginBottom: 14, alignItems: 'center', borderWidth: 1.5, borderColor: Colors.primaryContainer + '40', shadowColor: Colors.primaryContainer, shadowOffset: {width: 0, height: 3}, shadowOpacity: 0.08, shadowRadius: 14, elevation: 4, position: 'relative', overflow: 'hidden'},
  codeLabel: {fontSize: 11, fontWeight: '700', color: Colors.textTertiary, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 18},
  codeTapZone: {minHeight: 52, justifyContent: 'center', alignItems: 'center', width: '100%'},
  codeRow: {flexDirection: 'row', alignItems: 'center'},
  codeBox: {width: 36, height: 48, borderRadius: Radii.md, borderWidth: 1.5, borderColor: Colors.border, backgroundColor: Colors.background, alignItems: 'center', justifyContent: 'center', marginHorizontal: 3},
  codeBoxGap: {marginRight: 14},
  codeBoxFilled: {borderColor: Colors.primaryContainer, backgroundColor: Colors.primarySurface},
  codeBoxActive: {borderColor: Colors.primaryContainer, borderWidth: 2, backgroundColor: Colors.primarySurface},
  codeChar: {fontSize: 20, fontWeight: '800', color: Colors.textTertiary},
  codeCharFilled: {color: Colors.primaryContainer},
  codeInputOverlay: {
    ...StyleSheet.absoluteFillObject,
    opacity: 0.02,
    color: 'transparent',
    fontSize: 16,
    padding: 0,
    margin: 0,
  },
  codeHint: {marginTop: 16, fontSize: 13, color: Colors.textTertiary, fontWeight: '500'},

  // Permissions step
  permCard: {backgroundColor: Colors.surface, borderRadius: Radii.xl, padding: 22, marginBottom: 14, borderWidth: 1.5, borderColor: Colors.primaryContainer + '40'},
  permTitle: {fontSize: 20, fontWeight: '900', color: Colors.textPrimary, marginBottom: 8},
  permSub: {fontSize: 14, color: Colors.textSecondary, lineHeight: 20, marginBottom: 18},
  permRow: {flexDirection: 'row', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: Colors.border},
  permIcon: {fontSize: 20, marginRight: 12},
  permLabel: {flex: 1, fontSize: 15, fontWeight: '600', color: Colors.textPrimary},
  permBadge: {paddingHorizontal: 10, paddingVertical: 4, borderRadius: 20, backgroundColor: Colors.dangerLight, borderWidth: 1, borderColor: Colors.danger},
  permBadgeOk: {backgroundColor: Colors.successLight, borderColor: Colors.success},
  permBadgeText: {fontSize: 12, fontWeight: '700', color: Colors.danger},
  permBadgeTextOk: {color: Colors.tertiary},
  permHint: {marginTop: 12, fontSize: 12, color: Colors.textTertiary, textAlign: 'center', lineHeight: 17},

  // Success
  successCard: {backgroundColor: Colors.successLight, borderRadius: Radii.xl, padding: 28, marginBottom: 14, alignItems: 'center', borderWidth: 1, borderColor: Colors.success},
  successCheck: {width: 64, height: 64, borderRadius: 32, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center', marginBottom: 14, shadowColor: Colors.success, shadowOffset: {width: 0, height: 4}, shadowOpacity: 0.2, shadowRadius: 12, elevation: 4},
  successTitle: {fontSize: 20, fontWeight: '900', color: Colors.tertiary, marginBottom: 4},
  successSub: {fontSize: 15, color: Colors.success, fontWeight: '600'},

  // Error
  errorBox: {flexDirection: 'row', alignItems: 'flex-start', gap: 9, backgroundColor: Colors.dangerLight, borderRadius: Radii.md, padding: 14, marginBottom: 12, borderWidth: 1, borderColor: Colors.danger},
  errorText: {flex: 1, color: Colors.danger, fontSize: 14, lineHeight: 20},

  // Pair button
  pairBtn: {backgroundColor: Colors.primaryContainer, borderRadius: Radii.md, paddingVertical: 16, alignItems: 'center', marginBottom: 12, shadowColor: Colors.primaryContainer, shadowOffset: {width: 0, height: 6}, shadowOpacity: 0.35, shadowRadius: 14, elevation: 6},
  pairBtnDisabled: {backgroundColor: Colors.primarySurface, shadowOpacity: 0, elevation: 0},
  pairBtnText: {color: '#fff', fontWeight: '800', fontSize: 16, letterSpacing: 0.2},

  footerNote: {textAlign: 'center', fontSize: 12, color: Colors.textTertiary, lineHeight: 18, marginTop: 4},
});
