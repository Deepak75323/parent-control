import React, {useCallback, useEffect, useState} from 'react';
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
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {InstalledAppItem, monitoringApi, policiesApi, PolicySummary} from '../services/api';

type Props = NativeStackScreenProps<RootStackParamList, 'AppTimeLimits'>;

const QUICK_LIMITS = [15, 30, 60, 90, 120];

function LimitCard({policy, onDelete}: {policy: PolicySummary; onDelete: () => void}) {
  const hours = Math.floor(0);
  const mins = 0;
  // Parse limit from name like "Screen time YouTube - 60 min"
  const match = policy.name.match(/(\d+)\s*min/);
  const limitMin = match ? parseInt(match[1], 10) : 60;
  const h = Math.floor(limitMin / 60);
  const m = limitMin % 60;
  const timeLabel = h > 0 ? `${h}h ${m > 0 ? `${m}m` : ''}`.trim() : `${m}m`;

  return (
    <View style={styles.limitCard}>
      <View style={styles.limitLeft}>
        <View style={styles.limitIconWrap}>
          <Text style={styles.limitIcon}>⏱️</Text>
        </View>
        <View style={styles.limitInfo}>
          <Text style={styles.limitName} numberOfLines={1}>{policy.name}</Text>
          <View style={styles.limitMeta}>
            <Text style={styles.limitTime}>{timeLabel}</Text>
            <Text style={styles.limitSep}> · </Text>
            <View style={[styles.limitStatus, {backgroundColor: policy.enabled ? '#dcfce7' : '#f1f5f9'}]}>
              <Text style={[styles.limitStatusText, {color: policy.enabled ? '#16a34a' : '#94a3b8'}]}>
                {policy.enabled ? 'Active' : 'Paused'}
              </Text>
            </View>
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
  const [loading, setLoading] = useState(true);
  const [modalVisible, setModalVisible] = useState(false);
  const [saving, setSaving] = useState(false);

  // Form state
  const [selectedApp, setSelectedApp] = useState<InstalledAppItem | null>(null);
  const [appSearch, setAppSearch] = useState('');
  const [limitMin, setLimitMin] = useState(60);
  const [step, setStep] = useState<'pick_app' | 'pick_time'>('pick_app');

  const load = useCallback(async () => {
    try {
      const [policiesRes, appsRes] = await Promise.all([
        policiesApi.list('SCREEN_TIME').catch(() => ({data: {data: [] as PolicySummary[]}})),
        monitoringApi.installedApps(deviceId).catch(() => ({data: {data: [] as InstalledAppItem[]}})),
      ]);
      setPolicies(policiesRes.data.data.filter(p => p.deviceIds.includes(deviceId)));
      setInstalledApps(appsRes.data.data);
    } catch {
      Alert.alert('Error', 'Could not load limits');
    } finally {
      setLoading(false);
    }
  }, [deviceId]);

  useEffect(() => {
    load();
  }, [load]);

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
        name: `${selectedApp.appLabel} - ${limitMin} min`,
        deviceIds: [deviceId],
        packageName: selectedApp.packageName,
        dailyLimitMinutes: limitMin,
        actionOnLimit: 'BLOCK_APP',
        warnAtPercent: 80,
      });
      await load();
      setModalVisible(false);
      Alert.alert('Saved', `${limitMin} min/day limit set for ${selectedApp.appLabel}`);
    } catch {
      Alert.alert('Error', 'Could not save limit');
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
            await load();
          } catch {
            Alert.alert('Error', 'Could not remove limit');
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
      {/* Header */}
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>‹</Text>
        </Pressable>
        <Text style={styles.headerTitle}>App Time Limits</Text>
      </View>

      {loading ? (
        <ActivityIndicator color="#7c3aed" size="large" style={{marginTop: 60}} />
      ) : policies.length === 0 ? (
        /* Empty state */
        <View style={styles.emptyState}>
          <View style={styles.emptyIllustration}>
            <Text style={styles.emptyIllustrationText}>⏱️</Text>
          </View>
          <Text style={styles.emptyTitle}>No time limits yet</Text>
          <Text style={styles.emptyBody}>
            You can set the Available Time and Timespan Limits for apps on your child's device.{'\n'}
            Limited apps will be blocked during unavailable times.
          </Text>
          <Pressable style={styles.addBtn} onPress={openModal}>
            <Text style={styles.addBtnText}>Add limits</Text>
          </Pressable>
        </View>
      ) : (
        <>
          <FlatList
            contentContainerStyle={styles.listContent}
            data={policies}
            keyExtractor={p => p.id}
            renderItem={({item}) => (
              <LimitCard policy={item} onDelete={() => deletePolicy(item.id)} />
            )}
          />
          <View style={styles.fab}>
            <Pressable style={styles.fabBtn} onPress={openModal}>
              <Text style={styles.fabText}>+ Add limits</Text>
            </Pressable>
          </View>
        </>
      )}

      {/* Add Limit Modal */}
      <Modal visible={modalVisible} animationType="slide" presentationStyle="pageSheet" onRequestClose={() => setModalVisible(false)}>
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
                renderItem={({item}) => (
                  <Pressable
                    style={styles.modalAppRow}
                    onPress={() => {
                      setSelectedApp(item);
                      setStep('pick_time');
                    }}>
                    <View style={styles.modalAppIcon}>
                      <Text style={{fontSize: 20}}>📱</Text>
                    </View>
                    <Text style={styles.modalAppName}>{item.appLabel}</Text>
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
                <Text style={styles.selectedAppIcon}>📱</Text>
                <Text style={styles.selectedAppName}>{selectedApp?.appLabel}</Text>
              </View>

              <Text style={styles.timePickerLabel}>Daily time limit</Text>
              <View style={styles.timeBig}>
                <Text style={styles.timeBigNum}>{timeLabel}</Text>
                <Text style={styles.timeBigSub}>per day</Text>
              </View>

              {/* Quick limits */}
              <Text style={styles.quickTitle}>Quick select</Text>
              <View style={styles.quickRow}>
                {QUICK_LIMITS.map(mins => (
                  <Pressable
                    key={mins}
                    style={[styles.quickChip, limitMin === mins && styles.quickChipActive]}
                    onPress={() => setLimitMin(mins)}>
                    <Text style={[styles.quickChipText, limitMin === mins && styles.quickChipTextActive]}>
                      {mins < 60 ? `${mins}m` : `${mins / 60}h`}
                    </Text>
                  </Pressable>
                ))}
              </View>

              {/* Fine controls */}
              <View style={styles.fineRow}>
                <Pressable
                  style={styles.fineBtn}
                  onPress={() => setLimitMin(m => Math.max(15, m - 15))}>
                  <Text style={styles.fineBtnText}>−15m</Text>
                </Pressable>
                <Pressable
                  style={styles.fineBtn}
                  onPress={() => setLimitMin(m => Math.min(480, m + 15))}>
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

  // Empty state
  emptyState: {flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32},
  emptyIllustration: {width: 120, height: 120, borderRadius: 60, backgroundColor: '#ede9fe', alignItems: 'center', justifyContent: 'center', marginBottom: 24},
  emptyIllustrationText: {fontSize: 54},
  emptyTitle: {fontSize: 18, fontWeight: '800', color: '#0f172a', textAlign: 'center', marginBottom: 12},
  emptyBody: {fontSize: 14, color: '#64748b', textAlign: 'center', lineHeight: 22, marginBottom: 32},
  addBtn: {backgroundColor: '#7c3aed', borderRadius: 28, paddingHorizontal: 40, paddingVertical: 16},
  addBtnText: {color: '#fff', fontSize: 16, fontWeight: '800'},

  // List
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
  limitIconWrap: {width: 44, height: 44, borderRadius: 12, backgroundColor: '#ede9fe', alignItems: 'center', justifyContent: 'center', marginRight: 12},
  limitIcon: {fontSize: 22},
  limitInfo: {flex: 1},
  limitName: {fontSize: 15, fontWeight: '700', color: '#0f172a'},
  limitMeta: {flexDirection: 'row', alignItems: 'center', marginTop: 4},
  limitTime: {fontSize: 13, fontWeight: '700', color: '#7c3aed'},
  limitSep: {color: '#94a3b8', fontSize: 12},
  limitStatus: {paddingHorizontal: 8, paddingVertical: 2, borderRadius: 8},
  limitStatusText: {fontSize: 11, fontWeight: '700'},
  deleteBtn: {padding: 8},
  deleteBtnText: {fontSize: 20},

  // FAB
  fab: {position: 'absolute', bottom: 24, left: 16, right: 16},
  fabBtn: {backgroundColor: '#7c3aed', borderRadius: 16, paddingVertical: 16, alignItems: 'center'},
  fabText: {color: '#fff', fontSize: 16, fontWeight: '800'},

  // Modal
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
  modalAppIcon: {width: 36, height: 36, borderRadius: 8, backgroundColor: '#ede9fe', alignItems: 'center', justifyContent: 'center', marginRight: 12},
  modalAppName: {flex: 1, fontSize: 15, fontWeight: '600', color: '#0f172a'},
  modalAppChevron: {fontSize: 20, color: '#d1d5db'},

  // Time picker
  timePickerContent: {padding: 24, alignItems: 'center'},
  selectedAppCard: {alignItems: 'center', marginBottom: 24},
  selectedAppIcon: {fontSize: 48, marginBottom: 8},
  selectedAppName: {fontSize: 18, fontWeight: '800', color: '#0f172a'},
  timePickerLabel: {fontSize: 14, fontWeight: '600', color: '#64748b', marginBottom: 12},
  timeBig: {alignItems: 'center', backgroundColor: '#ede9fe', borderRadius: 20, paddingHorizontal: 40, paddingVertical: 20, marginBottom: 24},
  timeBigNum: {fontSize: 48, fontWeight: '800', color: '#7c3aed'},
  timeBigSub: {fontSize: 14, color: '#7c3aed', fontWeight: '600'},
  quickTitle: {fontSize: 13, fontWeight: '700', color: '#94a3b8', marginBottom: 10, alignSelf: 'flex-start'},
  quickRow: {flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 16},
  quickChip: {paddingHorizontal: 18, paddingVertical: 10, borderRadius: 20, borderWidth: 1.5, borderColor: '#e2e8f0', backgroundColor: '#fff'},
  quickChipActive: {backgroundColor: '#7c3aed', borderColor: '#7c3aed'},
  quickChipText: {fontSize: 14, fontWeight: '700', color: '#475569'},
  quickChipTextActive: {color: '#fff'},
  fineRow: {flexDirection: 'row', gap: 16, marginBottom: 20},
  fineBtn: {flex: 1, paddingVertical: 12, borderRadius: 12, backgroundColor: '#f1f5f9', alignItems: 'center'},
  fineBtnText: {fontSize: 14, fontWeight: '700', color: '#475569'},
  timeLimitNote: {fontSize: 13, color: '#94a3b8', textAlign: 'center', lineHeight: 18, marginTop: 8},
});
