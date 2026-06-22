import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  TextInput,
  StyleSheet,
  Text,
  Pressable,
  ScrollView,
  View,
  KeyboardAvoidingView,
  AppState,
  Platform,
  Animated,
  StatusBar,
  ActivityIndicator,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList, usePairingComplete} from './RootNavigator';
import {deviceApi} from '../services/api';
import {saveDeviceCredentials} from '../services/deviceStorage';
import {startMonitoringSync} from '../services/syncService';
import {ensureAllStreamingPermissions} from '../services/streamingPermissions';
import {nativeModules} from '../nativeModules';
import {getApiBaseUrl, isApiReachable} from '../utils/config';
import {slog} from '../services/streamDebug';

type Props = NativeStackScreenProps<ChildStackParamList, 'Pairing'>;
type PairPhase = 'code' | 'permissions' | 'done';

const CODE_LENGTH = 8;
const C = {
  primary: '#4F46E5',
  primaryDark: '#3730A3',
  primaryLight: '#818CF8',
  primarySurface: '#EEF2FF',
  success: '#059669',
  successLight: '#D1FAE5',
  danger: '#DC2626',
  bg: '#F7F7FF',
  surface: '#FFFFFF',
  border: '#E8E8F0',
  textPrimary: '#0F172A',
  textSecondary: '#475569',
  textTertiary: '#94A3B8',
  textOnDarkSoft: '#C7D2FE',
};

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
  const [phase, setPhase] = useState<PairPhase>('code');
  const [parentName, setParentName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [serverUrl, setServerUrl] = useState(getApiBaseUrl());
  const [serverOk, setServerOk] = useState<boolean | null>(null);
  const [permLoading, setPermLoading] = useState(false);
  const [perms, setPerms] = useState({camera: false, mic: false, screen: false});
  const pairingRef = useRef(false);
  const pendingCredsRef = useRef<{token: string; hmacKey?: string} | null>(null);

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

  const refreshPerms = useCallback(async () => {
    setPerms({
      camera: await nativeModules.isCameraGranted(),
      mic: await nativeModules.isMicGranted(),
      screen: await nativeModules.isMediaProjectionGranted(),
    });
  }, []);

  useEffect(() => {
    if (phase !== 'permissions') return;
    void refreshPerms();
    const sub = AppState.addEventListener('change', next => {
      if (next === 'active') void refreshPerms();
    });
    return () => sub.remove();
  }, [phase, refreshPerms]);

  const finishPairing = useCallback(async (displayName: string) => {
    const creds = pendingCredsRef.current;
    if (!creds) return;
    setDone(true);
    setPhase('done');
    markPaired(displayName);
    void startMonitoringSync(creds.token, creds.hmacKey, displayName)
      .catch(e => slog('PAIR', 'startMonitoringSync failed', String(e)));
  }, [markPaired]);

  const grantPermissions = useCallback(async () => {
    setPermLoading(true);
    setError(null);
    try {
      slog('PAIR', 'requesting streaming permissions');
      const result = await ensureAllStreamingPermissions();
      setPerms(result);
      if (!result.camera || !result.mic || !result.screen) {
        setError(
          'Live view needs camera, microphone, and screen capture. Tap "Grant permissions" and approve each Android prompt.',
        );
        return;
      }
      if (parentName) {
        await finishPairing(parentName);
      }
    } catch (e) {
      setError(String(e));
    } finally {
      setPermLoading(false);
    }
  }, [finishPairing, parentName]);

  const pair = useCallback(async (codeOverride?: string) => {
    if (done || pairingRef.current) return;
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
          'Cannot reach the server on your Mac. Keep the phone plugged in via USB, then on your Mac run: ./scripts/dev/setup-phone.sh — or use the same Wi‑Fi and set DEV_LAN_HOST in child-app/.env.',
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
      slog('PAIR', 'attempt', {code: normalized, androidId, api: getApiBaseUrl(), deviceInfo, hasFcmToken: !!fcmToken});
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
      setParentName(displayName);
      await saveDeviceCredentials(data.data.deviceToken, data.data.deviceHmacKey, displayName);
      pendingCredsRef.current = {token: data.data.deviceToken, hmacKey: data.data.deviceHmacKey};

      // Check if streaming permissions were already granted during setup
      const existing = {
        camera: await nativeModules.isCameraGranted(),
        mic: await nativeModules.isMicGranted(),
        screen: await nativeModules.isMediaProjectionGranted(),
      };
      setPerms(existing);

      if (existing.camera && existing.mic && existing.screen) {
        await finishPairing(displayName);
      } else {
        setPhase('permissions');
        setError(null);
      }
    } catch (e: unknown) {
      const axiosMsg = (e as {response?: {data?: {message?: string}}})?.response?.data?.message;
      const networkMsg = (e as {message?: string})?.message;
      const message = axiosMsg === 'DEVICE_TRUST_DENIED'
        ? 'This device failed security checks (rooted, emulator, or modified app). Use a stock device from Google Play.'
        : axiosMsg === 'PAIRING_CODE_EXPIRED'
        ? 'This code expired. Generate a new one in the parent app.'
        : axiosMsg === 'PAIRING_CODE_INVALID' || axiosMsg === 'Pairing code already used'
          ? 'Wrong or used code. Generate a fresh code in the parent app.'
          : axiosMsg ?? (networkMsg?.includes('Network Error') ? 'Cannot reach the server. USB: run ./scripts/dev/setup-phone.sh on your Mac. Wi‑Fi: same network + DEV_LAN_HOST in .env.' : networkMsg) ?? 'Invalid or expired code.';
      setError(message);
      setServerOk(false);
      setServerUrl(getApiBaseUrl());
    } finally {
      pairingRef.current = false;
      setLoading(false);
    }
  }, [code, done, finishPairing]);

  const onCodeChange = (text: string) => {
    const next = text.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, CODE_LENGTH);
    setCode(next);
    setError(null);
    if (next.length === CODE_LENGTH && !loading && !done && !pairingRef.current) {
      void pair(next);
    }
  };

  const connectionLabel = serverOk === null ? '⏳ Checking…' : serverOk ? '✓ Connected' : '✗ Offline';
  const connectionColor = serverOk === null ? C.textTertiary : serverOk ? C.success : C.danger;
  const connectionBg = serverOk === null ? '#F1F5F9' : serverOk ? C.successLight : '#FEF2F2';

  return (
    <SafeAreaView style={s.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="light-content" backgroundColor={C.primary} />
      <KeyboardAvoidingView style={s.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={s.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          {/* Hero */}
          <Animated.View style={[s.hero, {opacity: heroAnim}]}>
            <View style={s.heroBubble1} />
            <View style={s.heroBubble2} />
            <Pressable style={s.backBtn} onPress={() => navigation.goBack()}>
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

          {/* Code entry / Permissions / Success */}
          <Animated.View style={[{opacity: cardOpacity, transform: [{translateY: cardAnim}]}]}>
            {phase === 'permissions' && parentName ? (
              <View style={s.permCard}>
                <Text style={s.permTitle}>One more step</Text>
                <Text style={s.permSub}>
                  Grant live view permissions now so your parent can see the screen, camera, and hear audio — without asking again later.
                </Text>
                {[
                  {key: 'camera', label: 'Camera', icon: '📷', ok: perms.camera},
                  {key: 'mic', label: 'Microphone', icon: '🎤', ok: perms.mic},
                  {key: 'screen', label: 'Screen capture', icon: '📺', ok: perms.screen},
                ].map(item => (
                  <View key={item.key} style={s.permRow}>
                    <Text style={s.permIcon}>{item.icon}</Text>
                    <Text style={s.permLabel}>{item.label}</Text>
                    <View style={[s.permBadge, item.ok && s.permBadgeOk]}>
                      <Text style={[s.permBadgeText, item.ok && s.permBadgeTextOk]}>
                        {item.ok ? 'Granted' : 'Required'}
                      </Text>
                    </View>
                  </View>
                ))}
                <Pressable
                  style={({pressed}) => [s.pairBtn, {marginTop: 16}, pressed && {opacity: 0.87}, permLoading && {opacity: 0.6}]}
                  onPress={() => void grantPermissions()}
                  disabled={permLoading}>
                  {permLoading
                    ? <ActivityIndicator color="#fff" />
                    : <Text style={s.pairBtnText}>Grant permissions</Text>
                  }
                </Pressable>
                <Text style={s.permHint}>
                  Android will show prompts — tap "Start now" or "Allow" on each one.
                </Text>
              </View>
            ) : done && parentName ? (
              <View style={s.successCard}>
                <View style={s.successCheck}><Text style={{fontSize: 28}}>✓</Text></View>
                <Text style={s.successTitle}>Paired successfully!</Text>
                <Text style={s.successSub}>Your device is now protected</Text>
              </View>
            ) : (
              <Pressable onPress={() => inputRef.current?.focus()} disabled={loading || done}>
                <View style={s.codeCard}>
                  <Text style={s.codeLabel}>Pairing code</Text>
                  <CodeBoxes code={code} focused={focused} />
                  <TextInput
                    ref={inputRef}
                    style={s.hiddenInput}
                    value={code}
                    onChangeText={onCodeChange}
                    onFocus={() => setFocused(true)}
                    onBlur={() => setFocused(false)}
                    autoCapitalize="characters"
                    autoCorrect={false}
                    maxLength={CODE_LENGTH}
                    editable={!loading && !done}
                    caretHidden
                  />
                  <Text style={s.codeHint}>
                    {loading ? '⏳ Pairing…' : code.length === 0 ? 'Tap to enter code' : `${code.length}/${CODE_LENGTH} characters`}
                  </Text>
                </View>
              </Pressable>
            )}
          </Animated.View>

          {error ? (
            <View style={s.errorBox}>
              <Text style={{fontSize: 15}}>⚠️</Text>
              <Text style={s.errorText}>{error}</Text>
            </View>
          ) : null}

          {!done && phase === 'code' ? (
            <Pressable
              style={({pressed}) => [s.pairBtn, pressed && {opacity: 0.87}, (loading || code.length < CODE_LENGTH) && {opacity: 0.5}]}
              onPress={() => void pair()}
              disabled={loading || code.length < CODE_LENGTH}>
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
  root: {flex: 1, backgroundColor: C.bg},
  flex: {flex: 1},
  scroll: {paddingHorizontal: 20, paddingBottom: 40},

  // Hero
  hero: {backgroundColor: C.primary, borderRadius: 28, padding: 24, marginTop: 16, marginBottom: 16, overflow: 'hidden', position: 'relative'},
  heroBubble1: {position: 'absolute', width: 200, height: 200, borderRadius: 100, backgroundColor: C.primaryDark, opacity: 0.3, top: -70, right: -50},
  heroBubble2: {position: 'absolute', width: 140, height: 140, borderRadius: 70, backgroundColor: C.primaryLight, opacity: 0.12, bottom: -40, left: -30},
  backBtn: {position: 'absolute', top: 14, left: 14, width: 34, height: 34, borderRadius: 11, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center'},
  backBtnText: {fontSize: 22, color: '#fff', fontWeight: '700', lineHeight: 28},
  heroIconWrap: {width: 70, height: 70, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center', marginBottom: 14, alignSelf: 'center', borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)'},
  heroTitle: {fontSize: 24, fontWeight: '900', color: '#fff', textAlign: 'center', marginBottom: 6, letterSpacing: -0.3},
  heroSub: {fontSize: 14, color: C.textOnDarkSoft, textAlign: 'center', lineHeight: 20},

  // Card
  card: {backgroundColor: C.surface, borderRadius: 22, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: C.border, shadowColor: C.primary, shadowOffset: {width: 0, height: 3}, shadowOpacity: 0.06, shadowRadius: 12, elevation: 3},
  cardTitle: {fontSize: 11, fontWeight: '700', color: C.textTertiary, letterSpacing: 0.6, marginBottom: 12},
  step: {flexDirection: 'row', alignItems: 'center', paddingVertical: 10},
  stepDivider: {height: 1, backgroundColor: C.border, marginLeft: 44},
  stepNum: {width: 28, height: 28, borderRadius: 14, backgroundColor: C.primarySurface, alignItems: 'center', justifyContent: 'center', marginRight: 14},
  stepNumText: {color: C.primary, fontWeight: '800', fontSize: 13},
  stepText: {flex: 1, color: C.textSecondary, fontSize: 14, fontWeight: '500'},

  // Server
  serverCard: {borderRadius: 16, padding: 14, marginBottom: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  serverLeft: {flexDirection: 'row', alignItems: 'center', flex: 1, gap: 10},
  statusDot: {width: 10, height: 10, borderRadius: 5},
  serverStatus: {fontSize: 13, fontWeight: '700'},
  serverUrl: {fontSize: 11, color: C.textTertiary, marginTop: 2},
  retryBtn: {backgroundColor: C.primarySurface, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 10, borderWidth: 1, borderColor: '#C7D2FE'},
  retryBtnText: {color: C.primary, fontWeight: '700', fontSize: 13},

  helpCard: {backgroundColor: '#FFFBEB', borderRadius: 16, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: '#FDE68A'},
  helpTitle: {fontSize: 14, fontWeight: '800', color: '#92400E', marginBottom: 8},
  helpText: {fontSize: 13, color: '#78350F', lineHeight: 20},

  // Code card
  codeCard: {backgroundColor: C.surface, borderRadius: 22, padding: 22, marginBottom: 14, alignItems: 'center', borderWidth: 1.5, borderColor: C.primary + '40', shadowColor: C.primary, shadowOffset: {width: 0, height: 3}, shadowOpacity: 0.08, shadowRadius: 14, elevation: 4},
  codeLabel: {fontSize: 11, fontWeight: '700', color: C.textTertiary, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 18},
  codeRow: {flexDirection: 'row', alignItems: 'center'},
  codeBox: {width: 36, height: 48, borderRadius: 12, borderWidth: 1.5, borderColor: C.border, backgroundColor: '#F8F7FF', alignItems: 'center', justifyContent: 'center', marginHorizontal: 3},
  codeBoxGap: {marginRight: 14},
  codeBoxFilled: {borderColor: C.primary, backgroundColor: C.primarySurface},
  codeBoxActive: {borderColor: C.primary, borderWidth: 2, backgroundColor: C.primarySurface},
  codeChar: {fontSize: 20, fontWeight: '800', color: C.textTertiary},
  codeCharFilled: {color: C.primary},
  hiddenInput: {position: 'absolute', opacity: 0, height: 1, width: 1},
  codeHint: {marginTop: 16, fontSize: 13, color: C.textTertiary, fontWeight: '500'},

  // Permissions step
  permCard: {backgroundColor: C.surface, borderRadius: 22, padding: 22, marginBottom: 14, borderWidth: 1.5, borderColor: C.primary + '40'},
  permTitle: {fontSize: 20, fontWeight: '900', color: C.textPrimary, marginBottom: 8},
  permSub: {fontSize: 14, color: C.textSecondary, lineHeight: 20, marginBottom: 18},
  permRow: {flexDirection: 'row', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: C.border},
  permIcon: {fontSize: 20, marginRight: 12},
  permLabel: {flex: 1, fontSize: 15, fontWeight: '600', color: C.textPrimary},
  permBadge: {paddingHorizontal: 10, paddingVertical: 4, borderRadius: 20, backgroundColor: '#FEF2F2', borderWidth: 1, borderColor: '#FECACA'},
  permBadgeOk: {backgroundColor: C.successLight, borderColor: '#6EE7B7'},
  permBadgeText: {fontSize: 12, fontWeight: '700', color: '#991B1B'},
  permBadgeTextOk: {color: '#065F46'},
  permHint: {marginTop: 12, fontSize: 12, color: C.textTertiary, textAlign: 'center', lineHeight: 17},

  // Success
  successCard: {backgroundColor: C.successLight, borderRadius: 22, padding: 28, marginBottom: 14, alignItems: 'center', borderWidth: 1, borderColor: '#6EE7B7'},
  successCheck: {width: 64, height: 64, borderRadius: 32, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center', marginBottom: 14, shadowColor: C.success, shadowOffset: {width: 0, height: 4}, shadowOpacity: 0.2, shadowRadius: 12, elevation: 4},
  successTitle: {fontSize: 20, fontWeight: '900', color: '#064E3B', marginBottom: 4},
  successSub: {fontSize: 15, color: C.success, fontWeight: '600'},

  // Error
  errorBox: {flexDirection: 'row', alignItems: 'flex-start', gap: 9, backgroundColor: '#FEF2F2', borderRadius: 14, padding: 14, marginBottom: 12, borderWidth: 1, borderColor: '#FECACA'},
  errorText: {flex: 1, color: '#991B1B', fontSize: 14, lineHeight: 20},

  // Pair button
  pairBtn: {backgroundColor: C.primary, borderRadius: 18, paddingVertical: 16, alignItems: 'center', marginBottom: 12, shadowColor: C.primary, shadowOffset: {width: 0, height: 6}, shadowOpacity: 0.35, shadowRadius: 14, elevation: 6},
  pairBtnText: {color: '#fff', fontWeight: '800', fontSize: 16, letterSpacing: 0.2},

  footerNote: {textAlign: 'center', fontSize: 12, color: C.textTertiary, lineHeight: 18, marginTop: 4},
});
