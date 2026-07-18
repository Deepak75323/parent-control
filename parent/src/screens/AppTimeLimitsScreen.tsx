import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  FlatList,
  Alert,
  ActivityIndicator,
  Modal,
  TextInput,
  ScrollView,
  Image,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {
  InstalledAppItem,
  deviceApi,
  monitoringApi,
  policiesApi,
  PolicySummary,
  ScreenTimeBundleRule,
  UsageStatEntry,
} from '../services/api';
import {formatUsageMs} from '../utils/formatUsage';
import {appDisplayName} from '../utils/appDisplayName';

type Props = NativeStackScreenProps<RootStackParamList, 'AppTimeLimits'>;

const QUICK_LIMITS = [15, 30, 60, 90, 120];
const AVATAR_COLORS = ['#7c3aed', '#2563eb', '#059669', '#d97706', '#db2777', '#4f46e5'];

type LimitRow = {
  policy: PolicySummary;
  packageName: string | null;
  appLabel: string;
  iconBase64?: string | null;
  limitMin: number;
  usedMs: number;
};

function localDateKey(iso: string | undefined | null): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return iso.length >= 10 ? iso.slice(0, 10) : null;
  }
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function todayKey(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function usageMsForPackage(entries: UsageStatEntry[], packageName: string): number {
  const today = todayKey();
  const dayEntries = entries.filter(
    e => e.packageName === packageName && localDateKey(e.recordedAt) === today,
  );
  if (dayEntries.length === 0) return 0;
  const sorted = [...dayEntries].sort((a, b) => {
    const ca = a.createdAt ?? a.recordedAt ?? '';
    const cb = b.createdAt ?? b.recordedAt ?? '';
    return cb.localeCompare(ca);
  });
  return sorted[0]?.usageMs ?? 0;
}

function parseLimitFromName(name: string): number | null {
  const match = name.match(/(\d+)\s*min/i);
  return match ? parseInt(match[1], 10) : null;
}

function AppIcon({
  label,
  iconBase64,
  index,
  size = 44,
}: {
  label: string;
  iconBase64?: string | null;
  index: number;
  size?: number;
}) {
  const letter = (label.trim().charAt(0) || '?').toUpperCase();
  const bg = AVATAR_COLORS[index % AVATAR_COLORS.length];
  if (iconBase64) {
    return (
      <Image
        source={{uri: `data:image/png;base64,${iconBase64}`}}
        style={{width: size, height: size, borderRadius: size * 0.27}}
        resizeMode="cover"
      />
    );
  }
  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size * 0.27,
        backgroundColor: bg,
        alignItems: 'center',
        justifyContent: 'center',
      }}>
      <Text style={{color: '#fff', fontWeight: '800', fontSize: size * 0.4}}>{letter}</Text>
    </View>
  );
}

function LimitCard({
  row,
  index,
  onDelete,
}: {
  row: LimitRow;
  index: number;
  onDelete: () => void;
}) {
  const overLimit = row.usedMs >= row.limitMin * 60_000;
  const usedLabel = formatUsageMs(row.usedMs);
  const limitLabel =
    row.limitMin >= 60
      ? `${Math.floor(row.limitMin / 60)}h${row.limitMin % 60 ? ` ${row.limitMin % 60}m` : ''}`
      : `${row.limitMin}m`;
  const progress = Math.min(1, row.usedMs / Math.max(1, row.limitMin * 60_000));

  return (
    <View style={styles.limitCard}>
      <View style={styles.limitLeft}>
        <View style={styles.limitIconWrap}>
          <AppIcon label={row.appLabel} iconBase64={row.iconBase64} index={index} />
        </View>
        <View style={styles.limitInfo}>
          <Text style={styles.limitName} numberOfLines={1}>
            {row.appLabel}
          </Text>
          <View style={styles.limitMeta}>
            <Text style={[styles.limitTime, overLimit && styles.limitTimeOver]}>
              {usedLabel} / {limitLabel}
            </Text>
            <Text style={styles.limitSep}> · </Text>
            <View
              style={[
                styles.limitStatus,
                {
                  backgroundColor: overLimit
                    ? '#fee2e2'
                    : row.policy.enabled
                      ? '#dcfce7'
                      : '#f1f5f9',
                },
              ]}>
              <Text
                style={[
                  styles.limitStatusText,
                  {
                    color: overLimit
                      ? '#dc2626'
                      : row.policy.enabled
                        ? '#16a34a'
                        : '#94a3b8',
                  },
                ]}>
                {overLimit ? 'Over limit' : row.policy.enabled ? 'Active' : 'Paused'}
              </Text>
            </View>
          </View>
          <View style={styles.progressTrack}>
            <View
              style={[
                styles.progressFill,
                {
                  width: `${Math.round(progress * 100)}%`,
                  backgroundColor: overLimit ? '#dc2626' : '#7c3aed',
                },
              ]}
            />
          </View>
        </View>
      </View>
      <Pressable style={styles.deleteBtn} onPress={onDelete}>
        <Text style={styles.deleteBtnText}>🗑️</Text>
      </Pressable>
    </View>
  );
}

export function AppTimeLimitsScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [policies, setPolicies] = useState<PolicySummary[]>([]);
  const [installedApps, setInstalledApps] = useState<InstalledAppItem[]>([]);
  const [screenTimeRules, setScreenTimeRules] = useState<ScreenTimeBundleRule[]>([]);
  const [usageEntries, setUsageEntries] = useState<UsageStatEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalVisible, setModalVisible] = useState(false);
  const [saving, setSaving] = useState(false);

  const [selectedApp, setSelectedApp] = useState<InstalledAppItem | null>(null);
  const [appSearch, setAppSearch] = useState('');
  const [limitMin, setLimitMin] = useState(60);
  const [step, setStep] = useState<'pick_app' | 'pick_time'>('pick_app');

  const load = useCallback(async () => {
    try {
      const [policiesRes, appsRes, bundleRes, usageRes] = await Promise.all([
        policiesApi.list('SCREEN_TIME').catch(() => ({data: {data: [] as PolicySummary[]}})),
        monitoringApi.installedApps(deviceId).catch(() => ({data: {data: [] as InstalledAppItem[]}})),
        policiesApi.deviceBundle(deviceId).catch(() => ({data: {data: {screenTime: []}}})),
        monitoringApi.usageStats(deviceId, 0, 100).catch(() => ({
          data: {data: {content: [] as UsageStatEntry[]}},
        })),
      ]);
      setPolicies(policiesRes.data.data.filter(p => p.deviceIds.includes(deviceId)));
      setInstalledApps(appsRes.data.data);
      setScreenTimeRules(bundleRes.data.data.screenTime ?? []);
      setUsageEntries(usageRes.data.data.content ?? []);
    } catch {
      Alert.alert('Error', 'Couldn’t load time limits. Check your connection and try again.');
    } finally {
      setLoading(false);
    }
  }, [deviceId]);

  useEffect(() => {
    load();
  }, [load]);

  const rows: LimitRow[] = useMemo(() => {
    const appByPkg = new Map(installedApps.map(a => [a.packageName, a]));
    const ruleByPolicy = new Map(
      screenTimeRules.filter(r => r.policyId).map(r => [r.policyId, r]),
    );
    return policies.map(policy => {
      const rule = ruleByPolicy.get(policy.id);
      const packageName = rule?.packageName ?? null;
      const app = packageName ? appByPkg.get(packageName) : undefined;
      const limitMin =
        rule?.dailyLimitMinutes ?? parseLimitFromName(policy.name) ?? 60;
      const appLabel = appDisplayName({
        appLabel:
          app?.appLabel ??
          (policy.name.replace(/\s*-\s*\d+\s*min.*/i, '').trim() || null),
        packageName,
        fallback: 'App',
      });
      return {
        policy,
        packageName,
        appLabel,
        iconBase64: app?.iconBase64,
        limitMin,
        usedMs: packageName ? usageMsForPackage(usageEntries, packageName) : 0,
      };
    });
  }, [policies, installedApps, screenTimeRules, usageEntries]);

  const openModal = () => {
    setStep('pick_app');
    setSelectedApp(null);
    setLimitMin(60);
    setAppSearch('');
    setModalVisible(true);
  };

  const saveLimit = async () => {
    if (!selectedApp) return;
    setSaving(true);
    try {
      await policiesApi.createScreenTime({
        name: `${appDisplayName({
          appLabel: selectedApp.appLabel,
          packageName: selectedApp.packageName,
        })} - ${limitMin} min`,
        deviceIds: [deviceId],
        packageName: selectedApp.packageName,
        dailyLimitMinutes: limitMin,
        actionOnLimit: 'BLOCK_APP',
        warnAtPercent: 80,
      });
      void deviceApi.sendCommand(deviceId, 'SYNC_POLICIES').catch(() => undefined);
      await load();
      setModalVisible(false);
      Alert.alert(
        'Saved',
        `${limitMin} min/day limit set for ${appDisplayName({
          appLabel: selectedApp.appLabel,
          packageName: selectedApp.packageName,
        })}`,
      );
    } catch {
      Alert.alert('Error', 'Couldn’t save this limit. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const deletePolicy = async (policyId: string) => {
    Alert.alert('Remove limit', 'Remove this time limit?', [
      {text: 'Cancel', style: 'cancel'},
      {
        text: 'Remove',
        style: 'destructive',
        onPress: async () => {
          try {
            await policiesApi.delete(policyId);
            void deviceApi.sendCommand(deviceId, 'SYNC_POLICIES').catch(() => undefined);
            await load();
          } catch {
            Alert.alert('Error', 'Couldn’t remove this limit. Please try again.');
          }
        },
      },
    ]);
  };

  const filteredApps = installedApps.filter(app => {
    const q = appSearch.trim().toLowerCase();
    if (!q) return true;
    return app.appLabel.toLowerCase().includes(q) || app.packageName.toLowerCase().includes(q);
  });

  const h = Math.floor(limitMin / 60);
  const m = limitMin % 60;
  const timeLabel = h > 0 ? `${h}h${m > 0 ? ` ${m}m` : ''}` : `${m}m`;

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>‹</Text>
        </Pressable>
        <Text style={styles.headerTitle}>App Time Limits</Text>
      </View>

      {loading ? (
        <ActivityIndicator color="#7c3aed" size="large" style={{marginTop: 60}} />
      ) : rows.length === 0 ? (
        <View style={styles.emptyState}>
          <View style={styles.emptyIllustration}>
            <Text style={styles.emptyIllustrationText}>⏱️</Text>
          </View>
          <Text style={styles.emptyTitle}>No time limits yet</Text>
          <Text style={styles.emptyBody}>
            Set a daily limit for an app. When the child reaches the limit, that app is blocked for
            the rest of the day.
          </Text>
          <Pressable style={styles.addBtn} onPress={openModal}>
            <Text style={styles.addBtnText}>Add limits</Text>
          </Pressable>
        </View>
      ) : (
        <>
          <FlatList
            contentContainerStyle={styles.listContent}
            data={rows}
            keyExtractor={r => r.policy.id}
            renderItem={({item, index}) => (
              <LimitCard
                row={item}
                index={index}
                onDelete={() => deletePolicy(item.policy.id)}
              />
            )}
          />
          <View style={styles.fab}>
            <Pressable style={styles.fabBtn} onPress={openModal}>
              <Text style={styles.fabText}>+ Add limits</Text>
            </Pressable>
          </View>
        </>
      )}

      <Modal
        visible={modalVisible}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={() => setModalVisible(false)}>
        <SafeAreaView style={styles.modal} edges={['top', 'bottom']}>
          <View style={styles.modalHeader}>
            <Pressable onPress={() => setModalVisible(false)}>
              <Text style={styles.modalCancel}>Cancel</Text>
            </Pressable>
            <Text style={styles.modalTitle}>
              {step === 'pick_app' ? 'Select App' : 'Set Time Limit'}
            </Text>
            {step === 'pick_time' ? (
              <Pressable onPress={saveLimit} disabled={saving}>
                <Text style={[styles.modalSave, saving && {opacity: 0.5}]}>
                  {saving ? 'Saving…' : 'Save'}
                </Text>
              </Pressable>
            ) : (
              <Text style={{width: 60}} />
            )}
          </View>

          {step === 'pick_app' ? (
            <>
              <View style={styles.modalSearch}>
                <Text style={styles.modalSearchIcon}>🔍</Text>
                <TextInput
                  style={styles.modalSearchInput}
                  placeholder="Search apps…"
                  placeholderTextColor="#94a3b8"
                  value={appSearch}
                  onChangeText={setAppSearch}
                />
              </View>
              <FlatList
                data={filteredApps}
                keyExtractor={a => a.packageName}
                contentContainerStyle={{paddingBottom: 20}}
                renderItem={({item, index}) => (
                  <Pressable
                    style={styles.modalAppRow}
                    onPress={() => {
                      setSelectedApp(item);
                      setStep('pick_time');
                    }}>
                    <View style={styles.modalAppIcon}>
                      <AppIcon
                        label={appDisplayName({
                          appLabel: item.appLabel,
                          packageName: item.packageName,
                        })}
                        iconBase64={item.iconBase64}
                        index={index}
                        size={36}
                      />
                    </View>
                    <Text style={styles.modalAppName}>
                      {appDisplayName({
                        appLabel: item.appLabel,
                        packageName: item.packageName,
                      })}
                    </Text>
                    <Text style={styles.modalAppChevron}>›</Text>
                  </Pressable>
                )}
                ListEmptyComponent={
                  <View style={styles.emptyState}>
                    <Text style={styles.emptyTitle}>No apps found</Text>
                    <Text style={styles.emptyBody}>Sync the child device first.</Text>
                  </View>
                }
              />
            </>
          ) : (
            <ScrollView contentContainerStyle={styles.timePickerContent}>
              <View style={styles.selectedAppCard}>
                <AppIcon
                  label={
                    selectedApp
                      ? appDisplayName({
                          appLabel: selectedApp.appLabel,
                          packageName: selectedApp.packageName,
                        })
                      : 'App'
                  }
                  iconBase64={selectedApp?.iconBase64}
                  index={0}
                  size={64}
                />
                <Text style={[styles.selectedAppName, {marginTop: 12}]}>
                  {selectedApp
                    ? appDisplayName({
                        appLabel: selectedApp.appLabel,
                        packageName: selectedApp.packageName,
                      })
                    : 'App'}
                </Text>
              </View>

              <Text style={styles.timePickerLabel}>Daily time limit</Text>
              <View style={styles.timeBig}>
                <Text style={styles.timeBigNum}>{timeLabel}</Text>
                <Text style={styles.timeBigSub}>per day</Text>
              </View>

              <Text style={styles.quickTitle}>Quick select</Text>
              <View style={styles.quickRow}>
                {QUICK_LIMITS.map(mins => (
                  <Pressable
                    key={mins}
                    style={[styles.quickChip, limitMin === mins && styles.quickChipActive]}
                    onPress={() => setLimitMin(mins)}>
                    <Text
                      style={[
                        styles.quickChipText,
                        limitMin === mins && styles.quickChipTextActive,
                      ]}>
                      {mins < 60 ? `${mins}m` : `${mins / 60}h`}
                    </Text>
                  </Pressable>
                ))}
              </View>

              <View style={styles.fineRow}>
                <Pressable
                  style={styles.fineBtn}
                  onPress={() => setLimitMin(prev => Math.max(15, prev - 15))}>
                  <Text style={styles.fineBtnText}>−15m</Text>
                </Pressable>
                <Pressable
                  style={styles.fineBtn}
                  onPress={() => setLimitMin(prev => Math.min(480, prev + 15))}>
                  <Text style={styles.fineBtnText}>+15m</Text>
                </Pressable>
              </View>

              <Text style={styles.timeLimitNote}>
                The app will be blocked when the daily limit is reached.
              </Text>
            </ScrollView>
          )}
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#f5f5fa'},
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  backBtn: {paddingRight: 12},
  backText: {fontSize: 28, color: '#0f172a', lineHeight: 32},
  headerTitle: {flex: 1, fontSize: 18, fontWeight: '800', color: '#0f172a'},

  emptyState: {flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32},
  emptyIllustration: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: '#ede9fe',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 24,
  },
  emptyIllustrationText: {fontSize: 54},
  emptyTitle: {fontSize: 18, fontWeight: '800', color: '#0f172a', textAlign: 'center', marginBottom: 12},
  emptyBody: {fontSize: 14, color: '#64748b', textAlign: 'center', lineHeight: 22, marginBottom: 32},
  addBtn: {backgroundColor: '#7c3aed', borderRadius: 28, paddingHorizontal: 40, paddingVertical: 16},
  addBtnText: {color: '#fff', fontSize: 16, fontWeight: '800'},

  listContent: {padding: 16, paddingBottom: 100},
  limitCard: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 16,
    marginBottom: 10,
    flexDirection: 'row',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  limitLeft: {flex: 1, flexDirection: 'row', alignItems: 'center'},
  limitIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 12,
    overflow: 'hidden',
    marginRight: 12,
    backgroundColor: '#ede9fe',
  },
  limitInfo: {flex: 1},
  limitName: {fontSize: 15, fontWeight: '700', color: '#0f172a'},
  limitMeta: {flexDirection: 'row', alignItems: 'center', marginTop: 4, flexWrap: 'wrap'},
  limitTime: {fontSize: 13, fontWeight: '700', color: '#7c3aed'},
  limitTimeOver: {color: '#dc2626'},
  limitSep: {color: '#94a3b8', fontSize: 12},
  limitStatus: {paddingHorizontal: 8, paddingVertical: 2, borderRadius: 8},
  limitStatusText: {fontSize: 11, fontWeight: '700'},
  progressTrack: {
    height: 4,
    borderRadius: 2,
    backgroundColor: '#e2e8f0',
    marginTop: 8,
    overflow: 'hidden',
  },
  progressFill: {height: 4, borderRadius: 2},
  deleteBtn: {padding: 8},
  deleteBtnText: {fontSize: 20},

  fab: {position: 'absolute', bottom: 24, left: 16, right: 16},
  fabBtn: {backgroundColor: '#7c3aed', borderRadius: 16, paddingVertical: 16, alignItems: 'center'},
  fabText: {color: '#fff', fontSize: 16, fontWeight: '800'},

  modal: {flex: 1, backgroundColor: '#f5f5fa'},
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  modalCancel: {fontSize: 15, color: '#64748b', width: 60},
  modalTitle: {fontSize: 17, fontWeight: '800', color: '#0f172a'},
  modalSave: {fontSize: 15, fontWeight: '800', color: '#7c3aed', width: 60, textAlign: 'right'},

  modalSearch: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    margin: 12,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  modalSearchIcon: {fontSize: 16, marginRight: 8},
  modalSearchInput: {flex: 1, fontSize: 15, color: '#0f172a'},

  modalAppRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    marginHorizontal: 12,
    marginBottom: 2,
    borderRadius: 12,
    padding: 14,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 1,
  },
  modalAppIcon: {
    width: 36,
    height: 36,
    borderRadius: 8,
    overflow: 'hidden',
    marginRight: 12,
    backgroundColor: '#ede9fe',
  },
  modalAppName: {flex: 1, fontSize: 15, fontWeight: '600', color: '#0f172a'},
  modalAppChevron: {fontSize: 20, color: '#d1d5db'},

  timePickerContent: {padding: 24, alignItems: 'center'},
  selectedAppCard: {alignItems: 'center', marginBottom: 24},
  selectedAppName: {fontSize: 18, fontWeight: '800', color: '#0f172a'},
  timePickerLabel: {fontSize: 14, fontWeight: '600', color: '#64748b', marginBottom: 12},
  timeBig: {
    alignItems: 'center',
    backgroundColor: '#ede9fe',
    borderRadius: 20,
    paddingHorizontal: 40,
    paddingVertical: 20,
    marginBottom: 24,
  },
  timeBigNum: {fontSize: 48, fontWeight: '800', color: '#7c3aed'},
  timeBigSub: {fontSize: 14, color: '#7c3aed', fontWeight: '600'},
  quickTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#94a3b8',
    marginBottom: 10,
    alignSelf: 'flex-start',
  },
  quickRow: {flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 16},
  quickChip: {
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: '#e2e8f0',
    backgroundColor: '#fff',
  },
  quickChipActive: {backgroundColor: '#7c3aed', borderColor: '#7c3aed'},
  quickChipText: {fontSize: 14, fontWeight: '700', color: '#475569'},
  quickChipTextActive: {color: '#fff'},
  fineRow: {flexDirection: 'row', gap: 16, marginBottom: 20},
  fineBtn: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 12,
    backgroundColor: '#f1f5f9',
    alignItems: 'center',
  },
  fineBtnText: {fontSize: 14, fontWeight: '700', color: '#475569'},
  timeLimitNote: {fontSize: 13, color: '#94a3b8', textAlign: 'center', lineHeight: 18, marginTop: 8},
});
