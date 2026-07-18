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
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {authApi} from '../services/api';
import {useAuthStore} from '../store/authStore';
import {getApiErrorMessage} from '../utils/apiError';
import {showError, showSuccessAlert} from '../utils/notify';
import {ApiDebugPanel} from '../components/ApiDebugPanel';
import {Colors, Typography, Spacing, Radii, Elevation, Layout} from '../theme/design';

type Props = NativeStackScreenProps<RootStackParamList, 'Register'>;

function PremiumInput({
  icon, label, value, onChangeText, placeholder, secureTextEntry, keyboardType, autoCapitalize, half,
}: {
  icon: string; label: string; value: string; onChangeText: (v: string) => void;
  placeholder: string; secureTextEntry?: boolean; keyboardType?: 'default' | 'email-address'; autoCapitalize?: 'none' | 'sentences' | 'words'; half?: boolean;
}) {
  const borderAnim = useRef(new Animated.Value(0)).current;
  const onFocus = () => { Animated.timing(borderAnim, {toValue: 1, duration: 200, useNativeDriver: false}).start(); };
  const onBlur = () => { Animated.timing(borderAnim, {toValue: 0, duration: 200, useNativeDriver: false}).start(); };
  const borderColor = borderAnim.interpolate({inputRange: [0, 1], outputRange: [Colors.border, Colors.primaryContainer]});
  return (
    <View style={[inp.wrap, half && {flex: 1}]}>
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
          autoCorrect={false}
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

export function RegisterScreen({navigation}: Props) {
  const [form, setForm] = useState({email: '', password: '', firstName: '', lastName: ''});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const setTokens = useAuthStore(s => s.setTokens);

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

  const update = (key: keyof typeof form, value: string) => {
    setForm(f => ({...f, [key]: value}));
    setError(null);
  };

  const onRegister = async () => {
    setError(null);
    if (!form.email.trim() || !form.password || !form.firstName.trim() || !form.lastName.trim()) {
      setError('Please fill in all fields.'); return;
    }
    if (form.password.length < 8) {
      setError('Password must be at least 8 characters.'); return;
    }
    if (!form.email.includes('@')) {
      setError('Enter a valid email address.'); return;
    }
    setLoading(true);
    try {
      const {data} = await authApi.register({
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        consentAccepted: 'true',
      });
      if (data.success) {
        const verified = data.data.profile?.emailVerified ?? true;
        const message = verified
          ? 'Your parent account is ready. Tap Continue to get started.'
          : 'We sent a verification email. Verify your email to start your free trial.';
        showSuccessAlert('Account created! 🎉', message, () =>
          setTokens(
            data.data.accessToken,
            data.data.refreshToken,
            form.email.trim(),
            verified,
          ),
        );
        return;
      }
      const message = data.message ?? 'Couldn’t create your account.';
      const hint = message.toLowerCase().includes('already registered')
        ? `${message} Use Sign in if you already have an account.`
        : message;
      setError(hint);
      showError('Registration failed', hint);
    } catch (err) {
      const message = getApiErrorMessage(err, 'Couldn’t create your account. Please try again.');
      setError(message);
      showError('Registration failed', message);
    } finally {
      setLoading(false);
    }
  };

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
            <Pressable style={s.backBtnAbs} onPress={() => navigation.goBack()}>
              <Text style={s.backBtnText}>‹</Text>
            </Pressable>
            <View style={s.heroIconWrap}>
              <Text style={s.heroIcon}>✨</Text>
            </View>
            <Text style={s.heroTitle}>Create account</Text>
            <Text style={s.heroSub}>Set up your parent profile to get started</Text>
          </Animated.View>

          {/* Consent banner */}
          <Animated.View style={[s.consentBanner, {opacity: cardOpacity}]}>
            <Text style={s.consentIcon}>ℹ️</Text>
            <Text style={s.consentText}>By registering you confirm lawful parental monitoring with informed consent from your child.</Text>
          </Animated.View>

          {/* Form */}
          <Animated.View style={[s.formCard, {opacity: cardOpacity, transform: [{translateY: cardAnim}]}]}>
            <Text style={s.formTitle}>Your details</Text>

            {error ? (
              <View style={s.errorBox}>
                <Text style={s.errorIcon}>⚠</Text>
                <Text style={s.errorText}>{error}</Text>
              </View>
            ) : null}

            <View style={s.nameRow}>
              <PremiumInput icon="👤" label="First name" value={form.firstName} onChangeText={v => update('firstName', v)} placeholder="Jane" autoCapitalize="words" half />
              <View style={s.nameGap} />
              <PremiumInput icon="👤" label="Last name" value={form.lastName} onChangeText={v => update('lastName', v)} placeholder="Doe" autoCapitalize="words" half />
            </View>

            <PremiumInput icon="✉" label="Email address" value={form.email} onChangeText={v => update('email', v)} placeholder="you@example.com" keyboardType="email-address" autoCapitalize="none" />
            <PremiumInput icon="🔒" label="Password" value={form.password} onChangeText={v => update('password', v)} placeholder="Min. 8 characters" secureTextEntry autoCapitalize="none" />

            <Pressable
              style={({pressed}) => [s.createBtn, pressed && {opacity: 0.9}, loading && {opacity: 0.6}]}
              onPress={onRegister}
              disabled={loading}>
              {loading
                ? <ActivityIndicator color={Colors.textOnDark} />
                : <Text style={s.createBtnText}>Create account  →</Text>
              }
            </Pressable>

            <View style={s.dividerRow}>
              <View style={s.dividerLine} />
              <Text style={s.dividerText}>already have an account?</Text>
              <View style={s.dividerLine} />
            </View>

            <Pressable style={s.signInBtn} onPress={() => navigation.navigate('Login')}>
              <Text style={s.signInBtnText}>Sign in instead</Text>
            </Pressable>
          </Animated.View>

          {__DEV__ ? <ApiDebugPanel /> : null}
          <View style={s.scrollFooter} />
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
  backBtnAbs: {
    position: 'absolute',
    top: Spacing.md,
    left: Spacing.md,
    width: 36,
    height: 36,
    borderRadius: Radii.sm,
    backgroundColor: 'rgba(255,255,255,0.15)',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 2,
  },
  backBtnText: {fontSize: 22, color: Colors.textOnDark, fontWeight: '700', lineHeight: 28},
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
  },
  heroIcon: {fontSize: 34},
  heroTitle: {...Typography.largeTitle, color: Colors.textOnDark, zIndex: 1},
  heroSub: {...Typography.body, color: Colors.textOnDarkSoft, textAlign: 'center', zIndex: 1},

  consentBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.sm,
    backgroundColor: Colors.warningLight,
    borderRadius: Radii.md,
    padding: Layout.componentInternal,
    marginBottom: Layout.componentInternal,
    borderWidth: 1,
    borderColor: '#FDE68A',
    borderLeftWidth: 3,
    borderLeftColor: Colors.warning,
  },
  consentIcon: {fontSize: 16},
  consentText: {flex: 1, fontSize: 13, color: '#92400E', lineHeight: 19},

  formCard: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Layout.gutter,
    borderWidth: 1,
    borderColor: Colors.border,
    ...Elevation.card,
  },
  formTitle: {...Typography.sectionTitle, marginBottom: Layout.componentInternal},

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

  nameRow: {flexDirection: 'row'},
  nameGap: {width: Spacing.md},

  createBtn: {
    backgroundColor: Colors.primaryContainer,
    borderRadius: Radii.md,
    minHeight: Layout.touchTargetMin,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.sm,
    ...Elevation.cardHigh,
  },
  createBtnText: {...Typography.cardTitle, color: Colors.textOnDark},

  dividerRow: {flexDirection: 'row', alignItems: 'center', marginVertical: Layout.componentInternal},
  dividerLine: {flex: 1, height: StyleSheet.hairlineWidth, backgroundColor: Colors.borderLight},
  dividerText: {...Typography.smallLabel, color: Colors.textTertiary, marginHorizontal: Spacing.md},

  signInBtn: {
    backgroundColor: Colors.primarySurface,
    borderRadius: Radii.md,
    minHeight: Layout.touchTargetMin,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    borderColor: Colors.primarySurface,
  },
  signInBtnText: {...Typography.cardTitle, color: Colors.primary, fontSize: 15},

  scrollFooter: {height: Layout.componentInternal},
});
