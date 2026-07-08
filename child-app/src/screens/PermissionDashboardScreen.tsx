import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Switch,
  Pressable,
  Modal,
  ActivityIndicator,
  Alert,
  AppState,
  AppStateStatus,
  NativeEventEmitter,
  NativeModules,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {useFocusEffect} from '@react-navigation/native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {nativeModules} from '../nativeModules';
import {restoreDeviceCredentials} from '../services/deviceStorage';
import {buildPermissionGroups, PERMISSION_DEFS} from '../services/permissionDashboard/catalog';
import {
  cancelActivePermissionGrant,
  grantSinglePermission,
  revokeOrManagePermission,
} from '../services/permissionDashboard/grantOrchestrator';
import {
  isPermissionStatusComplete,
  loadPermissionStatus,
} from '../services/permissionDashboard/permissionStatus';
import type {PermissionDef, PermissionId, PermissionStatusMap} from '../services/permissionDashboard/types';
import {flowLog} from '../services/flowLogger';

type Props = NativeStackScreenProps<ChildStackParamList, 'PermissionDashboard'>;

const C = {
  primary: '#7C3AED',
  primaryLight: '#EDE9FE',
  primaryMuted: '#A78BFA',
  bg: '#F5F3FF',
  card: '#FFFFFF',
  text: '#0F172A',
  sub: '#64748B',
  border: '#E9E5FF',
  success: '#059669',
  danger: '#dc2626',
  dangerBg: '#fef2f2',
};

const PERMISSION_POLL_MS = 1500;
const permissionEvents = new NativeEventEmitter(NativeModules.ChildMonitorNative);

function explainAlert(title: string, body: string): Promise<void> {
  return new Promise(resolve => {
    Alert.alert(title, body, [{text: 'OK', onPress: () => resolve()}]);
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

function GroupCard({
  icon,
  title,
  description,
  children,
}: {
  icon: string;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <View style={styles.groupCard}>
      <View style={styles.groupHeader}>
        <View style={styles.groupIconWrap}>
          <Text style={styles.groupIcon}>{icon}</Text>
        </View>
        <View style={styles.groupHeaderText}>
          <Text style={styles.groupTitle}>{title}</Text>
          <Text style={styles.groupDesc}>{description}</Text>
        </View>
      </View>
      <View style={styles.groupBody}>{children}</View>
    </View>
  );
}

function PermissionRow({
  def,
  granted,
  known,
  busy,
  disabled,
  onTurnOn,
  onTurnOff,
}: {
  def: PermissionDef;
  granted: boolean;
  known: boolean;
  busy?: boolean;
  disabled?: boolean;
  onTurnOn?: () => void;
  onTurnOff?: () => void;
}) {
  const inactive = disabled || busy;
  return (
    <View style={[styles.permRow, known && !granted && styles.permRowBad]}>
      <Pressable
        style={({pressed}) => [
          styles.permLabelCol,
          pressed && !inactive && styles.permRowPressed,
        ]}
        onPress={inactive ? undefined : granted ? onTurnOff : onTurnOn}
        disabled={inactive}>
        <Text style={[styles.permLabel, granted && styles.permLabelOk]}>{def.label}</Text>
        {known ? (
          <View style={styles.statusRow}>
            <View
              style={[
                styles.statusDot,
                {backgroundColor: granted ? C.success : C.danger},
              ]}
            />
            <Text style={[styles.statusText, granted ? styles.statusOk : styles.statusBad]}>
              {granted ? 'Granted — tap to manage' : 'Not granted — tap to enable'}
            </Text>
          </View>
        ) : (
          <Text style={styles.statusChecking}>Checking…</Text>
        )}
      </Pressable>
      {busy ? (
        <ActivityIndicator size="small" color={C.primary} />
      ) : (
        <Switch
          value={granted}
          onValueChange={next => {
            if (inactive || !known) return;
            if (next) {
              onTurnOn?.();
            } else {
              onTurnOff?.();
            }
          }}
          disabled={inactive || !known}
          trackColor={{false: '#E2E8F0', true: C.primaryMuted}}
          thumbColor={granted ? C.primary : '#f8fafc'}
          ios_backgroundColor="#E2E8F0"
        />
      )}
    </View>
  );
}

export function PermissionDashboardScreen({navigation}: Props) {
  const [status, setStatus] = useState<PermissionStatusMap | null>(null);
  const [requiresAutoStart, setRequiresAutoStart] = useState(false);
  const [navigating, setNavigating] = useState(false);
  const [alreadyPaired, setAlreadyPaired] = useState(false);
  const [busyId, setBusyId] = useState<PermissionId | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      cancelActivePermissionGrant();
    };
  }, []);

  const explainCallbacks = useMemo(
    () => ({
      onExplain: async (id: PermissionId, title: string, body: string) => {
        const def = PERMISSION_DEFS[id];
        if (def.kind === 'runtime') {
          return;
        }
        await explainThenOpen(title, body);
      },
      onManualConfirm: async (_id: PermissionId, title: string, body: string) => {
        await explainAlert(title, body);
      },
    }),
    [],
  );

  const refresh = useCallback(async () => {
    if (!mountedRef.current) return;
    setRefreshing(true);
    try {
      const [requiresAutoStartNext, nextStatus, paired] = await Promise.all([
        nativeModules.requiresOemBackgroundSetup(),
        loadPermissionStatus(),
        restoreDeviceCredentials(),
      ]);
      if (!mountedRef.current) return;
      setRequiresAutoStart(requiresAutoStartNext);
      setStatus(nextStatus);
      setAlreadyPaired(!!paired);
    } catch (e) {
      flowLog('PERMISSION_FLOW', 'PermissionDashboard refresh failed', e);
    } finally {
      if (mountedRef.current) {
        setRefreshing(false);
      }
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void refresh();
      const poll = setInterval(() => void refresh(), PERMISSION_POLL_MS);
      return () => clearInterval(poll);
    }, [refresh]),
  );

  useEffect(() => {
    const onChange = (next: AppStateStatus) => {
      if (next === 'active') {
        void refresh();
      }
    };
    const appSub = AppState.addEventListener('change', onChange);
    const permSub = permissionEvents.addListener('permissionStateChanged', () => {
      void refresh();
    });
    return () => {
      appSub.remove();
      permSub.remove();
    };
  }, [refresh]);

  const handleTurnOn = useCallback(
    async (id: PermissionId) => {
      if (busyId || status?.[id]) {
        return;
      }
      setBusyId(id);
      try {
        await grantSinglePermission(id, explainCallbacks);
      } finally {
        setBusyId(null);
        void refresh();
      }
    },
    [busyId, explainCallbacks, refresh, status],
  );

  const handleTurnOff = useCallback(
    async (id: PermissionId) => {
      if (busyId || !status?.[id]) {
        return;
      }
      setBusyId(id);
      try {
        await revokeOrManagePermission(id);
        if (id === 'screenCapture' && mountedRef.current) {
          setStatus(prev => (prev ? {...prev, screenCapture: false} : prev));
        }
      } catch (e) {
        flowLog('PERMISSION_FLOW', 'revokeOrManage failed', {id, e});
        Alert.alert(
          'Could not open settings',
          `Unable to open ${PERMISSION_DEFS[id].label} settings on this device. Try opening Settings manually.`,
        );
      } finally {
        setBusyId(null);
        void refresh();
      }
    },
    [busyId, refresh, status],
  );

  const groups = useMemo(
    () => buildPermissionGroups(requiresAutoStart),
    [requiresAutoStart],
  );

  const allGranted = status ? isPermissionStatusComplete(status) : false;
  const grantedCount = status ? Object.values(status).filter(Boolean).length : 0;
  const totalCount = status ? Object.keys(status).length : 0;

  const continueAfterPermissions = useCallback(async () => {
    if (navigating) return;
    setNavigating(true);
    try {
      const paired = !!(await restoreDeviceCredentials());
      if (paired) {
        navigation.navigate('ServiceStatus');
      } else {
        navigation.navigate('Pairing');
      }
    } finally {
      setNavigating(false);
    }
  }, [navigating, navigation]);

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <View style={styles.hero}>
          <Text style={styles.heroEmoji}>📱🔐</Text>
          <Text style={styles.heroText}>
            Review and manage individual permissions. First-time setup uses the guided permission wizard.
          </Text>
        </View>

        {groups.map(group => (
          <GroupCard
            key={group.id}
            icon={group.icon}
            title={group.title}
            description={group.description}>
            {group.permissionIds.map((id, index) => {
              const permId = id as PermissionId;
              const def = PERMISSION_DEFS[permId];
              if (!def) return null;
              const granted = status?.[permId] ?? false;
              const known = status != null;
              return (
                <View key={id}>
                  {index > 0 ? <View style={styles.rowDivider} /> : null}
                  <PermissionRow
                    def={def}
                    granted={granted}
                    known={known}
                    busy={busyId === id}
                    onTurnOn={() => void handleTurnOn(permId)}
                    onTurnOff={() => void handleTurnOff(permId)}
                  />
                </View>
              );
            })}
          </GroupCard>
        ))}

        <Text style={styles.progressHint}>
          {status == null
            ? 'Loading permissions…'
            : `${grantedCount}/${totalCount} permissions granted`}
          {refreshing && status != null ? ' · updating' : ''}
        </Text>
      </ScrollView>

      {allGranted ? (
        <View style={styles.footer}>
          <Pressable
            style={({pressed}) => [styles.primaryBtn, pressed && styles.primaryBtnPressed]}
            onPress={() => void continueAfterPermissions()}
            disabled={navigating}>
            {navigating ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.primaryBtnText}>
                {alreadyPaired ? 'Back to monitoring' : 'Continue to pairing'}
              </Text>
            )}
          </Pressable>
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: C.bg},
  scroll: {padding: 16, paddingBottom: 24},
  hero: {alignItems: 'center', marginBottom: 20, paddingHorizontal: 8},
  heroEmoji: {fontSize: 48, marginBottom: 12},
  heroText: {
    fontSize: 15,
    color: C.sub,
    textAlign: 'center',
    lineHeight: 22,
  },
  groupCard: {
    backgroundColor: C.card,
    borderRadius: 20,
    padding: 16,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: C.border,
    shadowColor: C.primary,
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.06,
    shadowRadius: 10,
    elevation: 2,
  },
  groupHeader: {flexDirection: 'row', marginBottom: 12},
  groupIconWrap: {
    width: 40,
    height: 40,
    borderRadius: 12,
    backgroundColor: C.primaryLight,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  groupIcon: {fontSize: 20},
  groupHeaderText: {flex: 1},
  groupTitle: {fontSize: 16, fontWeight: '700', color: C.text},
  groupDesc: {fontSize: 13, color: C.sub, marginTop: 4, lineHeight: 18},
  groupBody: {borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: C.border, paddingTop: 4},
  permRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    paddingHorizontal: 4,
    borderRadius: 10,
  },
  permRowBad: {backgroundColor: C.dangerBg},
  permRowPressed: {opacity: 0.85},
  permLabelCol: {flex: 1, marginRight: 12, paddingVertical: 2},
  permLabel: {fontSize: 14, color: C.text, fontWeight: '500'},
  permLabelOk: {color: C.success},
  statusRow: {flexDirection: 'row', alignItems: 'center', marginTop: 4},
  statusDot: {width: 7, height: 7, borderRadius: 4, marginRight: 6},
  statusText: {fontSize: 12, fontWeight: '600'},
  statusOk: {color: C.success},
  statusBad: {color: C.danger},
  statusChecking: {fontSize: 12, color: C.sub, marginTop: 4},
  rowDivider: {height: StyleSheet.hairlineWidth, backgroundColor: C.border},
  progressHint: {
    textAlign: 'center',
    fontSize: 13,
    color: C.sub,
    marginTop: 4,
  },
  footer: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: C.border,
    backgroundColor: C.card,
  },
  primaryBtn: {
    backgroundColor: C.primary,
    borderRadius: 28,
    paddingVertical: 16,
    alignItems: 'center',
  },
  primaryBtnDisabled: {opacity: 0.7},
  primaryBtnPressed: {opacity: 0.9},
  primaryBtnText: {color: '#fff', fontSize: 16, fontWeight: '700'},
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(15, 23, 42, 0.55)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  modalCard: {
    backgroundColor: '#fff',
    borderRadius: 24,
    padding: 28,
    width: '100%',
    maxWidth: 340,
    alignItems: 'center',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: C.text,
    marginTop: 16,
    textAlign: 'center',
  },
  modalSub: {
    fontSize: 15,
    color: C.sub,
    marginTop: 8,
    textAlign: 'center',
    lineHeight: 21,
  },
  modalStep: {
    fontSize: 13,
    fontWeight: '700',
    color: C.primary,
    marginTop: 12,
  },
  modalHint: {
    fontSize: 12,
    color: C.sub,
    marginTop: 14,
    textAlign: 'center',
    lineHeight: 17,
  },
});
