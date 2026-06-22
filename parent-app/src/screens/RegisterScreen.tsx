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

type Props = NativeStackScreenProps<RootStackParamList, 'Register'>;

const C = {
  primary: '#4F46E5',
  primaryDark: '#3730A3',
  primaryLight: '#818CF8',
  primarySurface: '#EEF2FF',
  bg: '#F7F7FF',
  surface: '#FFFFFF',
  border: '#E8E8F0',
  textPrimary: '#0F172A',
  textSecondary: '#475569',
  textTertiary: '#94A3B8',
  textOnDarkSoft: '#C7D2FE',
};

function PremiumInput({
  icon, label, value, onChangeText, placeholder, secureTextEntry, keyboardType, autoCapitalize, half,
}: {
  icon: string; label: string; value: string; onChangeText: (v: string) => void;
  placeholder: string; secureTextEntry?: boolean; keyboardType?: any; autoCapitalize?: any; half?: boolean;
}) {
  const [focused, setFocused] = useState(false);
  const borderAnim = useRef(new Animated.Value(0)).current;
  const onFocus = () => { setFocused(true); Animated.timing(borderAnim, {toValue: 1, duration: 200, useNativeDriver: false}).start(); };
  const onBlur = () => { setFocused(false); Animated.timing(borderAnim, {toValue: 0, duration: 200, useNativeDriver: false}).start(); };
  const borderColor = borderAnim.interpolate({inputRange: [0, 1], outputRange: [C.border, C.primary]});
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
          placeholderTextColor={C.textTertiary}
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
  wrap: {marginBottom: 14},
  label: {fontSize: 13, fontWeight: '700', color: C.textSecondary, marginBottom: 7},
  inputRow: {flexDirection: 'row', alignItems: 'center', borderWidth: 1.5, borderRadius: 16, backgroundColor: C.surface, paddingHorizontal: 14},
  icon: {fontSize: 16, marginRight: 8, width: 22, textAlign: 'center'},
  input: {flex: 1, fontSize: 15, color: C.textPrimary, paddingVertical: 14},
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
  }, []);

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
        showSuccessAlert('Account created! 🎉', 'Your parent account is ready. Tap Continue to get started.', () =>
          setTokens(data.data.accessToken, data.data.refreshToken, form.email.trim()),
        );
        return;
      }
      const message = data.message ?? 'Unable to create account.';
      const hint = message.toLowerCase().includes('already registered')
        ? `${message} Use Sign in if you already have an account.`
        : message;
      setError(hint);
      showError('Registration failed', hint);
    } catch (err) {
      const message = getApiErrorMessage(err, 'Unable to create account. Please try again.');
      setError(message);
      showError('Registration failed', message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={s.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="light-content" backgroundColor={C.primary} />
      <KeyboardAvoidingView style={s.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={s.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          {/* Hero */}
          <Animated.View style={[s.hero, {opacity: heroAnim}]}>
            <View style={s.heroBubble1} />
            <View style={s.heroBubble2} />
            <Pressable style={s.backBtnAbs} onPress={() => navigation.goBack()}>
              <Text style={s.backBtnText}>‹</Text>
            </Pressable>
            <View style={s.heroIconWrap}>
              <Text style={{fontSize: 34}}>✨</Text>
            </View>
            <Text style={s.heroTitle}>Create account</Text>
            <Text style={s.heroSub}>Set up your parent profile to get started</Text>
          </Animated.View>

          {/* Consent banner */}
          <Animated.View style={[s.consentBanner, {opacity: cardOpacity}]}>
            <Text style={{fontSize: 16}}>ℹ️</Text>
            <Text style={s.consentText}>By registering you confirm lawful parental monitoring with informed consent from your child.</Text>
          </Animated.View>

          {/* Form */}
          <Animated.View style={[s.formCard, {opacity: cardOpacity, transform: [{translateY: cardAnim}]}]}>
            <Text style={s.formTitle}>Your details</Text>

            {error ? (
              <View style={s.errorBox}>
                <Text style={{fontSize: 14}}>⚠️</Text>
                <Text style={s.errorText}>{error}</Text>
              </View>
            ) : null}

            <View style={s.nameRow}>
              <PremiumInput icon="👤" label="First name" value={form.firstName} onChangeText={v => update('firstName', v)} placeholder="Jane" autoCapitalize="words" half />
              <View style={{width: 10}} />
              <PremiumInput icon="👤" label="Last name" value={form.lastName} onChangeText={v => update('lastName', v)} placeholder="Doe" autoCapitalize="words" half />
            </View>

            <PremiumInput icon="✉️" label="Email address" value={form.email} onChangeText={v => update('email', v)} placeholder="you@example.com" keyboardType="email-address" autoCapitalize="none" />
            <PremiumInput icon="🔒" label="Password" value={form.password} onChangeText={v => update('password', v)} placeholder="Min. 8 characters" secureTextEntry autoCapitalize="none" />

            <Pressable
              style={({pressed}) => [s.createBtn, pressed && {opacity: 0.87}, loading && {opacity: 0.6}]}
              onPress={onRegister}
              disabled={loading}>
              {loading
                ? <ActivityIndicator color="#fff" />
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
          <View style={{height: 20}} />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  root: {flex: 1, backgroundColor: C.bg},
  flex: {flex: 1},
  scroll: {paddingHorizontal: 20, paddingBottom: 40},

  hero: {
    backgroundColor: C.primary,
    borderRadius: 28,
    padding: 28,
    alignItems: 'center',
    marginTop: 16,
    marginBottom: 14,
    overflow: 'hidden',
    position: 'relative',
  },
  heroBubble1: {position: 'absolute', width: 220, height: 220, borderRadius: 110, backgroundColor: C.primaryDark, opacity: 0.3, top: -70, right: -60},
  heroBubble2: {position: 'absolute', width: 160, height: 160, borderRadius: 80, backgroundColor: C.primaryLight, opacity: 0.12, bottom: -50, left: -50},
  backBtnAbs: {position: 'absolute', top: 14, left: 14, width: 34, height: 34, borderRadius: 11, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center'},
  backBtnText: {fontSize: 22, color: '#fff', fontWeight: '700', lineHeight: 28},
  heroIconWrap: {width: 76, height: 76, borderRadius: 24, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center', marginBottom: 16, borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)'},
  heroTitle: {fontSize: 26, fontWeight: '900', color: '#fff', letterSpacing: -0.4, marginBottom: 6},
  heroSub: {fontSize: 14, color: C.textOnDarkSoft, textAlign: 'center'},

  consentBanner: {flexDirection: 'row', alignItems: 'flex-start', gap: 10, backgroundColor: '#FFFBEB', borderRadius: 14, padding: 14, marginBottom: 14, borderWidth: 1, borderColor: '#FDE68A', borderLeftWidth: 3, borderLeftColor: '#D97706'},
  consentText: {flex: 1, fontSize: 13, color: '#92400E', lineHeight: 19},

  formCard: {backgroundColor: C.surface, borderRadius: 24, padding: 22, borderWidth: 1, borderColor: C.border, shadowColor: C.primary, shadowOffset: {width: 0, height: 6}, shadowOpacity: 0.08, shadowRadius: 20, elevation: 5},
  formTitle: {fontSize: 20, fontWeight: '900', color: C.textPrimary, letterSpacing: -0.3, marginBottom: 16},

  errorBox: {flexDirection: 'row', alignItems: 'flex-start', gap: 8, backgroundColor: '#FEF2F2', borderRadius: 13, padding: 12, marginBottom: 14, borderWidth: 1, borderColor: '#FECACA'},
  errorText: {flex: 1, color: '#991B1B', fontSize: 14, lineHeight: 20},

  nameRow: {flexDirection: 'row'},

  createBtn: {backgroundColor: C.primary, borderRadius: 16, paddingVertical: 16, alignItems: 'center', marginTop: 6, shadowColor: C.primary, shadowOffset: {width: 0, height: 6}, shadowOpacity: 0.35, shadowRadius: 14, elevation: 6},
  createBtnText: {color: '#fff', fontWeight: '800', fontSize: 16, letterSpacing: 0.2},

  dividerRow: {flexDirection: 'row', alignItems: 'center', marginVertical: 16},
  dividerLine: {flex: 1, height: 1, backgroundColor: C.border},
  dividerText: {marginHorizontal: 10, fontSize: 12, color: C.textTertiary, fontWeight: '600'},

  signInBtn: {backgroundColor: C.primarySurface, borderRadius: 16, paddingVertical: 15, alignItems: 'center', borderWidth: 1, borderColor: '#C7D2FE'},
  signInBtnText: {color: C.primary, fontWeight: '800', fontSize: 15},
});
