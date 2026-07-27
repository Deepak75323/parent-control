import React, {useEffect, useRef, useState} from 'react';
import {
  View,
  StyleSheet,
  Text,
  Pressable,
  TextInput,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Animated,
  ActivityIndicator,
  StatusBar,
  Image,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {authApi} from '../services/api';
import {useAuthStore} from '../store/authStore';
import {getApiErrorMessage} from '../utils/apiError';
import {showSuccessAlert} from '../utils/notify';
import {Colors, Typography, Spacing, Radii, Elevation, Layout} from '../theme/design';
import {captureException, track} from '../services/telemetry';
import {TelemetryEvents} from '../services/telemetryEvents';

const APP_LOGO = require('../assets/app-logo.png');
const DEV_AUTO_LOGIN = process.env.DEV_AUTO_LOGIN === 'true';
const DEV_AUTO_EMAIL = process.env.DEV_AUTO_EMAIL ?? '';
const DEV_AUTO_PASSWORD = process.env.DEV_AUTO_PASSWORD ?? '';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

function PremiumInput({
  icon,
  label,
  value,
  onChangeText,
  placeholder,
  secureTextEntry,
  keyboardType,
  autoCapitalize,
  autoCorrect,
}: {
  icon: string;
  label: string;
  value: string;
  onChangeText: (v: string) => void;
  placeholder: string;
  secureTextEntry?: boolean;
  keyboardType?: 'default' | 'email-address';
  autoCapitalize?: 'none' | 'sentences';
  autoCorrect?: boolean;
}) {
  const borderAnim = useRef(new Animated.Value(0)).current;

  const onFocus = () => {
    Animated.timing(borderAnim, {toValue: 1, duration: 200, useNativeDriver: false}).start();
  };
  const onBlur = () => {
    Animated.timing(borderAnim, {toValue: 0, duration: 200, useNativeDriver: false}).start();
  };

  const borderColor = borderAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [Colors.border, Colors.primaryContainer],
  });

  return (
    <View style={inp.wrap}>
      <Text style={inp.label}>{label}</Text>
      <Animated.View style={[inp.inputRow, {borderColor}]}>
        <Text style={inp.icon}>{icon}</Text>
        <TextInput
          style={inp.input}
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor={Colors.textTertiary}
          secureTextEntry={secureTextEntry}
          keyboardType={keyboardType}
          autoCapitalize={autoCapitalize ?? 'sentences'}
          autoCorrect={autoCorrect}
          onFocus={onFocus}
          onBlur={onBlur}
        />
      </Animated.View>
    </View>
  );
}

const inp = StyleSheet.create({
  wrap: {marginBottom: Layout.componentInternal},
  label: {...Typography.smallLabel, color: Colors.textSecondary, marginBottom: Spacing.sm},
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: Radii.md,
    backgroundColor: Colors.background,
    paddingHorizontal: Layout.componentInternal,
    minHeight: Layout.touchTargetMin,
  },
  icon: {fontSize: 18, marginRight: Spacing.md, width: 26, textAlign: 'center'},
  input: {flex: 1, fontSize: 15, color: Colors.textPrimary, paddingVertical: Spacing.md},
});

export function LoginScreen({navigation}: Props) {
  const [email, setEmail] = useState(DEV_AUTO_EMAIL);
  const [password, setPassword] = useState(DEV_AUTO_PASSWORD);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const setTokens = useAuthStore(s => s.setTokens);
  const autoLoginAttempted = useRef(false);

  const heroAnim = useRef(new Animated.Value(0)).current;
  const cardAnim = useRef(new Animated.Value(30)).current;
  const cardOpacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(heroAnim, {toValue: 1, duration: 600, useNativeDriver: true}),
      Animated.sequence([
        Animated.delay(200),
        Animated.parallel([
          Animated.timing(cardAnim, {toValue: 0, duration: 500, useNativeDriver: true}),
          Animated.timing(cardOpacity, {toValue: 1, duration: 500, useNativeDriver: true}),
        ]),
      ]),
    ]).start();
  }, [heroAnim, cardAnim, cardOpacity]);

  const onLogin = async (silent = false) => {
    setError(null);
    if (!email.trim() || !password) {
      if (!silent) setError('Please enter your email and password.');
      return;
    }
    setLoading(true);
    try {
      const {data} = await authApi.login(email.trim(), password);
      if (data.success) {
        track(TelemetryEvents.LOGIN_SUCCESS);
        const verified = data.data.profile?.emailVerified ?? true;
        const welcomeMessage = verified
          ? 'Tap Continue to open your dashboard.'
          : 'Verify your email from the welcome message to start your trial.';
        if (silent || DEV_AUTO_LOGIN) {
          setTokens(
            data.data.accessToken,
            data.data.refreshToken,
            email.trim(),
            verified,
            data.data.expiresInSeconds,
          );
          return;
        }
        showSuccessAlert('Welcome back!', welcomeMessage, () =>
          setTokens(
            data.data.accessToken,
            data.data.refreshToken,
            email.trim(),
            verified,
            data.data.expiresInSeconds,
          ),
        );
        return;
      }
      if (!silent) setError(data.message ?? 'Check your credentials and try again.');
    } catch (err) {
      track(TelemetryEvents.LOGIN_FAILURE);
      captureException(err, {flow: 'login'});
      const message = getApiErrorMessage(err, 'Check your credentials and try again.');
      if (!silent || __DEV__) setError(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!DEV_AUTO_LOGIN || autoLoginAttempted.current) return;
    if (!DEV_AUTO_EMAIL || !DEV_AUTO_PASSWORD) return;
    autoLoginAttempted.current = true;
    onLogin(true).catch(() => undefined);
  }, []);

  return (
    <SafeAreaView style={s.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="light-content" backgroundColor={Colors.primaryContainer} />
      <KeyboardAvoidingView style={s.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          contentContainerStyle={s.scroll}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}>

          <Animated.View style={[s.hero, {opacity: heroAnim}]}>
            <View style={s.heroGradientBase} />
            <View style={s.heroGradientOverlay} />
            <View style={s.heroBubble1} />
            <View style={s.heroBubble2} />
            <View style={s.heroIconWrap}>
              <Image source={APP_LOGO} style={s.heroIconImage} resizeMode="cover" />
            </View>
            <Text style={s.heroTitle}>Parent Control</Text>
            <Text style={s.heroSub}>Monitor with care and consent</Text>
          </Animated.View>

          <Animated.View style={[s.formCard, {opacity: cardOpacity, transform: [{translateY: cardAnim}]}]}>
            <Text style={s.formTitle}>Welcome back</Text>
            <Text style={s.formSub}>Sign in to your parent account</Text>

            {error ? (
              <View style={s.errorBox}>
                <Text style={s.errorIcon}>⚠</Text>
                <Text style={s.errorText}>{error}</Text>
              </View>
            ) : null}

            <PremiumInput
              icon="✉"
              label="Email address"
              value={email}
              onChangeText={v => {
                setEmail(v);
                setError(null);
              }}
              placeholder="you@example.com"
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
            />
            <PremiumInput
              icon="🔒"
              label="Password"
              value={password}
              onChangeText={v => {
                setPassword(v);
                setError(null);
              }}
              placeholder="Your password"
              secureTextEntry
            />

            <Pressable onPress={() => navigation.navigate('ForgotPassword')} style={s.forgotWrap}>
              <Text style={s.forgotText}>Forgot password?</Text>
            </Pressable>

            <Pressable
              style={({pressed}) => [s.signInBtn, pressed && {opacity: 0.9}, loading && {opacity: 0.6}]}
              onPress={() => onLogin(false)}
              disabled={loading}>
              {loading ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={s.signInBtnText}>Sign in  →</Text>
              )}
            </Pressable>

            <View style={s.dividerRow}>
              <View style={s.dividerLine} />
              <Text style={s.dividerText}>or</Text>
              <View style={s.dividerLine} />
            </View>

            <Pressable style={s.createBtn} onPress={() => navigation.navigate('Register')}>
              <Text style={s.createBtnText}>Create new account</Text>
            </Pressable>
          </Animated.View>

          <Text style={s.footer}>By continuing you agree to lawful monitoring with consent.</Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.background},
  flex: {flex: 1},
  scroll: {
    paddingHorizontal: Layout.screenPadding,
    paddingBottom: Layout.sectionGap,
    paddingTop: Layout.componentInternal,
  },

  hero: {
    borderRadius: Radii.xl,
    padding: Layout.gutter,
    alignItems: 'center',
    marginBottom: Layout.componentInternal,
    overflow: 'hidden',
    position: 'relative',
    minHeight: 200,
    justifyContent: 'center',
    ...Elevation.hero,
  },
  heroGradientBase: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: Colors.primaryContainer,
  },
  heroGradientOverlay: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: '35%',
    backgroundColor: Colors.accent,
    opacity: 0.88,
  },
  heroBubble1: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(255,255,255,0.08)',
    top: -70,
    right: -60,
  },
  heroBubble2: {
    position: 'absolute',
    width: 160,
    height: 160,
    borderRadius: 80,
    backgroundColor: 'rgba(255,255,255,0.06)',
    bottom: -50,
    left: -50,
  },
  heroIconWrap: {
    width: 72,
    height: 72,
    borderRadius: Radii.lg,
    backgroundColor: 'rgba(255,255,255,0.18)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Layout.componentInternal,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.25)',
    zIndex: 1,
    overflow: 'hidden',
  },
  heroIconImage: {width: 72, height: 72},
  heroTitle: {...Typography.largeTitle, color: Colors.textOnDark, zIndex: 1},
  heroSub: {...Typography.body, color: Colors.textOnDarkSoft, textAlign: 'center', zIndex: 1},

  formCard: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Layout.gutter,
    borderWidth: 1,
    borderColor: Colors.border,
    ...Elevation.card,
  },
  formTitle: {...Typography.sectionTitle},
  formSub: {...Typography.body, color: Colors.textTertiary, marginTop: Spacing.half, marginBottom: Layout.gutter},

  errorBox: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.sm,
    backgroundColor: `${Colors.error}1A`,
    borderRadius: Radii.md,
    padding: Layout.componentInternal,
    marginBottom: Layout.componentInternal,
    borderWidth: 1,
    borderColor: `${Colors.error}33`,
  },
  errorIcon: {fontSize: 14, color: Colors.warning},
  errorText: {flex: 1, color: Colors.error, ...Typography.body},

  signInBtn: {
    backgroundColor: Colors.primaryContainer,
    borderRadius: Radii.md,
    minHeight: Layout.touchTargetMin,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.sm,
    ...Elevation.cardHigh,
  },
  signInBtnText: {...Typography.cardTitle, color: Colors.textOnDark},
  forgotWrap: {alignSelf: 'flex-end', marginTop: Spacing.sm, marginBottom: Spacing.sm},
  forgotText: {...Typography.smallLabel, color: Colors.primaryContainer, fontWeight: '700'},

  dividerRow: {flexDirection: 'row', alignItems: 'center', marginVertical: Layout.componentInternal},
  dividerLine: {flex: 1, height: StyleSheet.hairlineWidth, backgroundColor: Colors.borderLight},
  dividerText: {...Typography.smallLabel, color: Colors.textTertiary, marginHorizontal: Spacing.md},

  createBtn: {
    backgroundColor: Colors.primarySurface,
    borderRadius: Radii.md,
    minHeight: Layout.touchTargetMin,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    borderColor: Colors.primarySurface,
  },
  createBtnText: {...Typography.cardTitle, color: Colors.primary, fontSize: 15},

  footer: {
    ...Typography.smallLabel,
    color: Colors.textTertiary,
    textAlign: 'center',
    marginTop: Layout.gutter,
    lineHeight: 18,
    paddingHorizontal: Layout.componentInternal,
  },
});
