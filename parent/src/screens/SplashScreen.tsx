import React, {useEffect, useRef, useState} from 'react';
import {View, Text, StyleSheet, Animated, StatusBar} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import axios from 'axios';
import {RootStackParamList} from '../navigation/RootNavigator';
import {config} from '../utils/config';
import {ApiPaths} from '../constants/apiPaths';
import {useAuthStore} from '../store/authStore';
import {clearPersistedAuth, loadPersistedAuth, savePersistedAuth} from '../services/authStorage';
import {plog} from '../services/pairingLog';
import {Colors} from '../theme/design';

type Props = NativeStackScreenProps<RootStackParamList, 'Splash'>;

export function SplashScreen({navigation}: Props) {
  const setTokens = useAuthStore(s => s.setTokens);
  const [status, setStatus] = useState('Signing you in…');

  const scale = useRef(new Animated.Value(0.7)).current;
  const opacity = useRef(new Animated.Value(0)).current;
  const ringScale = useRef(new Animated.Value(0.6)).current;
  const ringOpacity = useRef(new Animated.Value(0)).current;
  const textOpacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.sequence([
      Animated.parallel([
        Animated.spring(scale, {toValue: 1, tension: 80, friction: 8, useNativeDriver: true}),
        Animated.timing(opacity, {toValue: 1, duration: 400, useNativeDriver: true}),
      ]),
      Animated.parallel([
        Animated.loop(
          Animated.sequence([
            Animated.parallel([
              Animated.timing(ringScale, {toValue: 1.4, duration: 1000, useNativeDriver: true}),
              Animated.timing(ringOpacity, {toValue: 0.4, duration: 500, useNativeDriver: true}),
            ]),
            Animated.parallel([
              Animated.timing(ringOpacity, {toValue: 0, duration: 500, useNativeDriver: true}),
            ]),
          ]),
        ),
        Animated.timing(textOpacity, {toValue: 1, duration: 400, useNativeDriver: true}),
      ]),
    ]).start();
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const persisted = await loadPersistedAuth();
      if (cancelled) return;
      if (!persisted?.refreshToken) {
        plog('splash — no persisted session');
        navigation.replace('Login');
        return;
      }
      setStatus('Signing in…');
      plog('splash — refresh token restore');
      try {
        const {data} = await axios.post<{
          success: boolean;
          data?: {
            accessToken: string;
            refreshToken: string;
            profile?: {emailVerified?: boolean};
          };
        }>(`${config.apiBaseUrl}${ApiPaths.AUTH_REFRESH}`, {refreshToken: persisted.refreshToken});
        if (data.success && data.data?.accessToken && data.data.refreshToken) {
          const emailVerified = data.data.profile?.emailVerified ?? persisted.emailVerified ?? true;
          await savePersistedAuth({
            accessToken: data.data.accessToken,
            refreshToken: data.data.refreshToken,
            email: persisted.email,
            emailVerified,
          });
          setTokens(
            data.data.accessToken,
            data.data.refreshToken,
            persisted.email ?? undefined,
            emailVerified,
          );
          plog('splash — session restored');
          return;
        }
      } catch (err) {
        plog('splash — refresh failed', String(err));
        await clearPersistedAuth();
      }
      if (!cancelled) navigation.replace('Login');
    })();
    return () => { cancelled = true; };
  }, [navigation, setTokens]);

  return (
    <SafeAreaView style={s.root}>
      <StatusBar barStyle="light-content" backgroundColor={Colors.primaryContainer} />
      <View style={s.bubble1} />
      <View style={s.bubble2} />

      {/* Pulse ring */}
      <Animated.View style={[s.ring, {transform: [{scale: ringScale}], opacity: ringOpacity}]} />

      {/* Logo */}
      <Animated.View style={[s.logoWrap, {transform: [{scale}], opacity}]}>
        <Text style={s.logoEmoji}>🛡️</Text>
      </Animated.View>

      {/* Text */}
      <Animated.View style={[s.textWrap, {opacity: textOpacity}]}>
        <Text style={s.appName}>Parent Control</Text>
        <Text style={s.tagline}>Monitor with care and consent</Text>
        <Text style={s.status}>{status}</Text>
      </Animated.View>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: Colors.primaryContainer,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  bubble1: {
    position: 'absolute',
    width: 380,
    height: 380,
    borderRadius: 190,
    backgroundColor: Colors.primaryDark,
    opacity: 0.4,
    top: -120,
    right: -100,
  },
  bubble2: {
    position: 'absolute',
    width: 280,
    height: 280,
    borderRadius: 140,
    backgroundColor: Colors.primaryLight,
    opacity: 0.12,
    bottom: -80,
    left: -80,
  },
  ring: {
    position: 'absolute',
    width: 160,
    height: 160,
    borderRadius: 80,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.5)',
  },
  logoWrap: {
    width: 108,
    height: 108,
    borderRadius: 32,
    backgroundColor: 'rgba(255,255,255,0.15)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 28,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.25)',
  },
  logoEmoji: {fontSize: 52},
  textWrap: {alignItems: 'center'},
  appName: {fontSize: 30, fontWeight: '900', color: Colors.textOnDark, letterSpacing: -0.5},
  tagline: {fontSize: 15, color: Colors.textOnDarkSoft, marginTop: 6, fontWeight: '500'},
  status: {marginTop: 32, color: 'rgba(255,255,255,0.5)', fontSize: 13, fontWeight: '500'},
});
