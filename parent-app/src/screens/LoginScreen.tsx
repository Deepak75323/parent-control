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
import {showSuccessAlert} from '../utils/notify';

const DEV_AUTO_LOGIN = process.env.DEV_AUTO_LOGIN === 'true';
const DEV_AUTO_EMAIL = process.env.DEV_AUTO_EMAIL ?? '';
const DEV_AUTO_PASSWORD = process.env.DEV_AUTO_PASSWORD ?? '';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

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
  icon, label, value, onChangeText, placeholder, secureTextEntry, keyboardType, autoCapitalize, autoCorrect,
}: {
  icon: string; label: string; value: string; onChangeText: (v: string) => void;
  placeholder: string; secureTextEntry?: boolean; keyboardType?: any; autoCapitalize?: any; autoCorrect?: boolean;
}) {
  const [focused, setFocused] = useState(false);
  const borderAnim = useRef(new Animated.Value(0)).current;

  const onFocus = () => {
    setFocused(true);
    Animated.timing(borderAnim, {toValue: 1, duration: 200, useNativeDriver: false}).start();
  };
  const onBlur = () => {
    setFocused(false);
    Animated.timing(borderAnim, {toValue: 0, duration: 200, useNativeDriver: false}).start();
  };

  const borderColor = borderAnim.interpolate({inputRange: [0, 1], outputRange: [C.border, C.primary]});

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
          placeholderTextColor={C.textTertiary}
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
  wrap: {marginBottom: 14},
  label: {fontSize: 13, fontWeight: '700', color: C.textSecondary, marginBottom: 7},
  inputRow: {flexDirection: 'row', alignItems: 'center', borderWidth: 1.5, borderRadius: 16, backgroundColor: C.surface, paddingHorizontal: 14},
  icon: {fontSize: 18, marginRight: 10, width: 26, textAlign: 'center'},
  input: {flex: 1, fontSize: 15, color: C.textPrimary, paddingVertical: 14},
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
  }, []);

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
        if (silent || DEV_AUTO_LOGIN) {
          setTokens(data.data.accessToken, data.data.refreshToken, email.trim());
          return;
        }
        showSuccessAlert('Welcome back!', 'Tap Continue to open your dashboard.', () =>
          setTokens(data.data.accessToken, data.data.refreshToken, email.trim()),
        );
        return;
      }
      if (!silent) setError(data.message ?? 'Check your credentials and try again.');
    } catch (err) {
      if (!silent) setError(getApiErrorMessage(err, 'Check your credentials and try again.'));
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
      <StatusBar barStyle="light-content" backgroundColor={C.primary} />
      <KeyboardAvoidingView style={s.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={s.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          {/* Premium Hero */}
          <Animated.View style={[s.hero, {opacity: heroAnim}]}>
            <View style={s.heroBubble1} />
            <View style={s.heroBubble2} />
            <View style={s.heroIconWrap}>
              <Text style={{fontSize: 34}}>🛡️</Text>
            </View>
            <Text style={s.heroTitle}>Parent Control</Text>
            <Text style={s.heroSub}>Monitor with care and consent</Text>
          </Animated.View>

          {/* Form Card */}
          <Animated.View style={[s.formCard, {opacity: cardOpacity, transform: [{translateY: cardAnim}]}]}>
            <Text style={s.formTitle}>Welcome back</Text>
            <Text style={s.formSub}>Sign in to your parent account</Text>

            {error ? (
              <View style={s.errorBox}>
                <Text style={{fontSize: 14}}>⚠️</Text>
                <Text style={s.errorText}>{error}</Text>
              </View>
            ) : null}

            <PremiumInput icon="✉️" label="Email address" value={email} onChangeText={v => { setEmail(v); setError(null); }} placeholder="you@example.com" keyboardType="email-address" autoCapitalize="none" autoCorrect={false} />
            <PremiumInput icon="🔒" label="Password" value={password} onChangeText={v => { setPassword(v); setError(null); }} placeholder="Your password" secureTextEntry />

            <Pressable
              style={({pressed}) => [s.signInBtn, pressed && {opacity: 0.87}, loading && {opacity: 0.6}]}
              onPress={() => onLogin(false)}
              disabled={loading}>
              {loading
                ? <ActivityIndicator color="#fff" />
                : <Text style={s.signInBtnText}>Sign in  →</Text>
              }
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
  root: {flex: 1, backgroundColor: C.bg},
  flex: {flex: 1},
  scroll: {paddingHorizontal: 20, paddingBottom: 40},

  // Hero
  hero: {
    backgroundColor: C.primary,
    borderRadius: 28,
    padding: 28,
    alignItems: 'center',
    marginTop: 16,
    marginBottom: 20,
    overflow: 'hidden',
    position: 'relative',
  },
  heroBubble1: {position: 'absolute', width: 220, height: 220, borderRadius: 110, backgroundColor: C.primaryDark, opacity: 0.3, top: -70, right: -60},
  heroBubble2: {position: 'absolute', width: 160, height: 160, borderRadius: 80, backgroundColor: C.primaryLight, opacity: 0.12, bottom: -50, left: -50},
  heroIconWrap: {width: 76, height: 76, borderRadius: 24, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center', marginBottom: 16, borderWidth: 1, borderColor: 'rgba(255,255,255,0.2)'},
  heroTitle: {fontSize: 26, fontWeight: '900', color: '#fff', letterSpacing: -0.4, marginBottom: 6},
  heroSub: {fontSize: 14, color: C.textOnDarkSoft, textAlign: 'center'},

  // Form card
  formCard: {backgroundColor: C.surface, borderRadius: 24, padding: 22, borderWidth: 1, borderColor: C.border, shadowColor: C.primary, shadowOffset: {width: 0, height: 6}, shadowOpacity: 0.08, shadowRadius: 20, elevation: 5},
  formTitle: {fontSize: 22, fontWeight: '900', color: C.textPrimary, letterSpacing: -0.3},
  formSub: {fontSize: 14, color: C.textTertiary, marginTop: 3, marginBottom: 20},

  // Error
  errorBox: {flexDirection: 'row', alignItems: 'flex-start', gap: 8, backgroundColor: '#FEF2F2', borderRadius: 13, padding: 12, marginBottom: 14, borderWidth: 1, borderColor: '#FECACA'},
  errorText: {flex: 1, color: '#991B1B', fontSize: 14, lineHeight: 20},

  // Sign In button
  signInBtn: {backgroundColor: C.primary, borderRadius: 16, paddingVertical: 16, alignItems: 'center', marginTop: 6, shadowColor: C.primary, shadowOffset: {width: 0, height: 6}, shadowOpacity: 0.35, shadowRadius: 14, elevation: 6},
  signInBtnText: {color: '#fff', fontWeight: '800', fontSize: 16, letterSpacing: 0.2},

  // Divider
  dividerRow: {flexDirection: 'row', alignItems: 'center', marginVertical: 16},
  dividerLine: {flex: 1, height: 1, backgroundColor: C.border},
  dividerText: {marginHorizontal: 12, fontSize: 13, color: C.textTertiary, fontWeight: '600'},

  // Create account
  createBtn: {backgroundColor: C.primarySurface, borderRadius: 16, paddingVertical: 15, alignItems: 'center', borderWidth: 1, borderColor: '#C7D2FE'},
  createBtnText: {color: C.primary, fontWeight: '800', fontSize: 15},

  footer: {textAlign: 'center', fontSize: 12, color: C.textTertiary, marginTop: 20, lineHeight: 18, paddingHorizontal: 10},
});
