import React, {useEffect, useMemo, useState} from 'react';
import {
  ActivityIndicator,
  AppState,
  type AppStateStatus,
  BackHandler,
  Pressable,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {authApi, parentPinApi} from '../services/api';
import {navigationRef} from '../navigation/navigationRef';
import {promptParentFingerprint} from '../services/parentBiometric';
import {useAuthStore} from '../store/authStore';
import {useClockLockStore} from '../store/clockLockStore';
import {verifyParentUnlockPassword} from '../utils/verifyParentUnlockPassword';

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

export function ClockLockHost() {
  const phase = useClockLockStore(s => s.phase);
  const dispatch = useClockLockStore(s => s.dispatch);
  const accessToken = useAuthStore(s => s.accessToken);

  useEffect(() => {
    dispatch({type: 'SESSION_CHANGED'});
  }, [accessToken, dispatch]);

  useEffect(() => {
    const onChange = (next: AppStateStatus) => {
      if (next === 'background') {
        dispatch({type: 'APP_BACKGROUNDED'});
      }
    };
    const sub = AppState.addEventListener('change', onChange);
    return () => sub.remove();
  }, [dispatch]);

  useEffect(() => {
    if (phase === 'open') {
      return;
    }
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (phase === 'password') {
        dispatch({type: 'PASSWORD_CANCELLED'});
      }
      return true;
    });
    return () => sub.remove();
  }, [phase, dispatch]);

  if (phase === 'open') {
    return null;
  }

  return (
    <View style={styles.cover} pointerEvents="auto">
      {phase === 'password' ? <PasswordSheet /> : <ClockFace />}
    </View>
  );
}

function ClockFace() {
  const dispatch = useClockLockStore(s => s.dispatch);
  const accessToken = useAuthStore(s => s.accessToken);
  const [now, setNow] = useState(() => new Date());
  const [prompting, setPrompting] = useState(false);

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  const timeLabel = useMemo(
    () => `${pad2(now.getHours())}:${pad2(now.getMinutes())}`,
    [now],
  );
  const dateLabel = useMemo(
    () =>
      now.toLocaleDateString(undefined, {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
      }),
    [now],
  );

  const onHiddenCorner = () => {
    if (prompting) {
      return;
    }
    if (!accessToken) {
      dispatch({type: 'LOGIN_REQUESTED'});
      if (navigationRef.isReady()) {
        navigationRef.navigate('Login');
      }
      return;
    }
    dispatch({type: 'CORNER_TAPPED'});
    setPrompting(true);
    void (async () => {
      const result = await promptParentFingerprint();
      setPrompting(false);
      if (result === 'succeeded' || result === 'unavailable') {
        dispatch({type: 'FINGERPRINT_SUCCEEDED'});
        return;
      }
      dispatch({type: 'FINGERPRINT_FAILED'});
    })();
  };

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="light-content" backgroundColor="#0B1220" />
      <View style={styles.glow} />
      <Pressable
        accessibilityLabel="Parent unlock"
        onPress={onHiddenCorner}
        style={styles.hiddenHit}
        hitSlop={8}
      />
      <View style={styles.center}>
        <Text style={styles.time}>{timeLabel}</Text>
        <Text style={styles.date}>{dateLabel}</Text>
        <Text style={styles.hint}>Clock Time</Text>
      </View>
    </SafeAreaView>
  );
}

function PasswordSheet() {
  const dispatch = useClockLockStore(s => s.dispatch);
  const email = useAuthStore(s => s.email);
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = () => {
    if (busy) {
      return;
    }
    setBusy(true);
    setError(null);
    void (async () => {
      const ok = await verifyParentUnlockPassword(password, email, {
        pinStatus: async () => {
          const {data} = await parentPinApi.status();
          return data.data;
        },
        verifyPin: async pin => {
          const {data} = await parentPinApi.verify(pin);
          return data.data;
        },
        login: async (loginEmail, loginPassword) => {
          const {data} = await authApi.login(loginEmail, loginPassword);
          const payload = data.data;
          if (payload?.accessToken && payload.refreshToken) {
            useAuthStore
              .getState()
              .setTokens(
                payload.accessToken,
                payload.refreshToken,
                payload.profile?.email,
                payload.profile?.emailVerified,
                payload.expiresInSeconds,
              );
          }
        },
      });
      setBusy(false);
      if (ok) {
        dispatch({type: 'PASSWORD_SUCCEEDED'});
        return;
      }
      setError('Incorrect password');
      dispatch({type: 'PASSWORD_FAILED'});
    })();
  };

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="light-content" backgroundColor="#0B1220" />
      <View style={styles.glow} />
      <View style={styles.passwordWrap}>
        <Text style={styles.passwordTitle}>Parent password</Text>
        <Text style={styles.passwordHint}>Enter your parent password to open the app.</Text>
        <TextInput
          style={styles.passwordInput}
          value={password}
          onChangeText={setPassword}
          placeholder="Password"
          placeholderTextColor="rgba(248,250,252,0.4)"
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          editable={!busy}
          onSubmitEditing={submit}
        />
        {error ? <Text style={styles.passwordError}>{error}</Text> : null}
        <Pressable style={styles.submitBtn} onPress={submit} disabled={busy}>
          {busy ? (
            <ActivityIndicator color="#F8FAFC" />
          ) : (
            <Text style={styles.submitText}>Continue</Text>
          )}
        </Pressable>
        <Pressable
          style={styles.cancelBtn}
          onPress={() => dispatch({type: 'PASSWORD_CANCELLED'})}
          disabled={busy}>
          <Text style={styles.cancelText}>Cancel</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  cover: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 50,
    elevation: 50,
  },
  root: {
    flex: 1,
    backgroundColor: '#0B1220',
  },
  glow: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#1E3A5F',
    opacity: 0.35,
  },
  hiddenHit: {
    position: 'absolute',
    top: 8,
    right: 8,
    width: 56,
    height: 56,
    zIndex: 10,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  time: {
    color: '#F8FAFC',
    fontSize: 72,
    fontWeight: '200',
    letterSpacing: 2,
    fontVariant: ['tabular-nums'],
  },
  date: {
    marginTop: 12,
    color: 'rgba(248,250,252,0.72)',
    fontSize: 18,
    fontWeight: '500',
  },
  hint: {
    marginTop: 28,
    color: 'rgba(248,250,252,0.4)',
    fontSize: 13,
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  passwordWrap: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 28,
  },
  passwordTitle: {
    color: '#F8FAFC',
    fontSize: 22,
    fontWeight: '600',
    marginBottom: 8,
  },
  passwordHint: {
    color: 'rgba(248,250,252,0.65)',
    fontSize: 15,
    marginBottom: 20,
  },
  passwordInput: {
    borderWidth: 1,
    borderColor: 'rgba(248,250,252,0.25)',
    borderRadius: 12,
    color: '#F8FAFC',
    fontSize: 16,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  passwordError: {
    marginTop: 10,
    color: '#FCA5A5',
    fontSize: 14,
  },
  submitBtn: {
    marginTop: 20,
    backgroundColor: '#334155',
    borderRadius: 12,
    alignItems: 'center',
    paddingVertical: 14,
  },
  submitText: {
    color: '#F8FAFC',
    fontWeight: '700',
    fontSize: 16,
  },
  cancelBtn: {
    marginTop: 12,
    alignItems: 'center',
    padding: 10,
  },
  cancelText: {
    color: 'rgba(248,250,252,0.55)',
    fontSize: 15,
  },
});
