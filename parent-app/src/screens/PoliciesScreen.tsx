import React, {useEffect, useMemo, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  Alert,
  FlatList,
  Pressable,
  TextInput,
  Switch,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {devicesApi, InstalledAppItem, monitoringApi, policiesApi, PolicySummary} from '../services/api';
import {
  APP_CATEGORIES,
  defaultWeeklySchedule,
  WEEKDAYS,
  WeeklySchedule,
} from '../utils/appCategories';
import {
  Card,
  FormField,
  HeroBlock,
  PrimaryBtn,
  ScreenShell,
} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'Policies'>;

export function PoliciesScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [policies, setPolicies] = useState<PolicySummary[]>([]);
  const [installedApps, setInstalledApps] = useState<InstalledAppItem[]>([]);
  const [appSearch, setAppSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [selectedPackages, setSelectedPackages] = useState<Set<string>>(new Set());
  const [limitMinutes, setLimitMinutes] = useState('60');
  const [weeklySchedule, setWeeklySchedule] = useState<WeeklySchedule>(() =>
    defaultWeeklySchedule(60),
  );
  const [useWeeklyLimits, setUseWeeklyLimits] = useState(false);

  const load = async () => {
    try {
      const [policyRes, appsRes] = await Promise.all([
        policiesApi.list(),
        monitoringApi.installedApps(deviceId).catch(() => ({data: {data: [] as InstalledAppItem[]}})),
      ]);
      setPolicies(policyRes.data.data.filter(p => p.deviceIds.includes(deviceId)));
      setInstalledApps(appsRes.data.data);
    } catch {
      Alert.alert('Error', 'Could not load policies');
    }
  };

  useEffect(() => {
    load();
  }, [deviceId]);

  useEffect(() => {
    const mins = parseInt(limitMinutes, 10) || 60;
    setWeeklySchedule(defaultWeeklySchedule(mins));
  }, [limitMinutes]);

  const filteredApps = useMemo(() => {
    const q = appSearch.trim().toLowerCase();
    return installedApps.filter(app => {
      const cat = (app.appCategory ?? 'OTHER').toUpperCase();
      if (categoryFilter !== 'ALL' && cat !== categoryFilter) return false;
      if (!q) return true;
      return app.appLabel.toLowerCase().includes(q) || app.packageName.toLowerCase().includes(q);
    });
  }, [installedApps, appSearch, categoryFilter]);

  const togglePackage = (pkg: string) => {
    setSelectedPackages(prev => {
      const next = new Set(prev);
      if (next.has(pkg)) next.delete(pkg);
      else next.add(pkg);
      return next;
    });
  };

  const selectCategoryApps = () => {
    if (categoryFilter === 'ALL') {
      Alert.alert('Pick a category', 'Choose Games, Social, etc. then tap Block category.');
      return;
    }
    const pkgs = filteredApps.map(a => a.packageName);
    setSelectedPackages(new Set(pkgs));
  };

  const createAppBlock = async () => {
    if (selectedPackages.size === 0) {
      Alert.alert('Select apps', 'Tap one or more apps from the list.');
      return;
    }
    const rules = Array.from(selectedPackages).map(packageName => {
      const app = installedApps.find(a => a.packageName === packageName);
      return {packageName, scheduleType: 'ALWAYS' as const};
    });
    try {
      await policiesApi.createAppBlock({
        name: `Block ${selectedPackages.size} app(s)`,
        deviceIds: [deviceId],
        rules,
      });
      await load();
      Alert.alert('Created', `${selectedPackages.size} app(s) blocked`);
      setSelectedPackages(new Set());
    } catch {
      Alert.alert('Error', 'Failed to create app block');
    }
  };

  const createCategoryBlock = async () => {
    if (categoryFilter === 'ALL') {
      Alert.alert('Select a category', 'Choose a category chip first.');
      return;
    }
    const label = APP_CATEGORIES.find(c => c.id === categoryFilter)?.label ?? categoryFilter;
    try {
      await policiesApi.createAppBlock({
        name: `Block ${label}`,
        deviceIds: [deviceId],
        rules: [
          {
            packageName: '*',
            scheduleType: 'ALWAYS',
            scheduleJson: {appCategory: categoryFilter},
          },
        ],
      });
      await load();
      Alert.alert('Created', `${label} category blocked (includes future installs)`);
    } catch {
      Alert.alert('Error', 'Failed to block category');
    }
  };

  const createScreenTime = async () => {
    if (selectedPackages.size === 0) {
      Alert.alert('Select apps', 'Choose at least one app for the limit.');
      return;
    }
    const mins = parseInt(limitMinutes, 10) || 60;
    try {
      for (const packageName of selectedPackages) {
        const app = installedApps.find(a => a.packageName === packageName);
        await policiesApi.createScreenTime({
          name: `Screen time ${app?.appLabel ?? packageName}`,
          deviceIds: [deviceId],
          packageName,
          dailyLimitMinutes: mins,
          actionOnLimit: 'BLOCK_APP',
          warnAtPercent: 80,
          weeklyScheduleJson: useWeeklyLimits ? weeklySchedule : undefined,
        });
      }
      await load();
      Alert.alert('Created', 'Screen time limits saved');
    } catch {
      Alert.alert('Error', 'Failed to create screen time policy');
    }
  };

  const updateDayLimit = (key: string, field: 'enabled' | 'limitMinutes', value: boolean | number) => {
    setWeeklySchedule(prev => ({
      ...prev,
      [key]: {...prev[key as keyof WeeklySchedule], [field]: value},
    }));
  };

  return (
    <ScreenShell backLabel="← Back to device" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Controls"
        title="Policies"
        subtitle="Select apps or categories — block instantly or set daily limits."
      />

      <Card>
        <PrimaryBtn title="Set geofence on map" onPress={() => navigation.navigate('Geofences', {deviceId})} />
        <View style={styles.spacer} />
        <Pressable style={styles.secondaryBtn} onPress={async () => {
          try {
            // Tell the child device to immediately re-upload its app inventory.
            await devicesApi.sendCommand(deviceId, 'SYNC_INSTALLED_APPS');
          } catch {
            // Command send failed (offline?) — fall through to just re-fetch cached list.
          }
          // Wait a few seconds for the child to respond, then reload.
          await new Promise(r => setTimeout(r, 4000));
          await load();
          Alert.alert('Refreshed', installedApps.length > 0
            ? `${installedApps.length} apps loaded from child device`
            : 'No apps yet — make sure the child app is running on the phone and try again.');
        }}>
          <Text style={styles.secondaryBtnText}>Refresh app list from child</Text>
        </Pressable>
      </Card>

      <Card>
        <Text style={styles.sectionTitle}>Category</Text>
        <View style={styles.chips}>
          {APP_CATEGORIES.map(cat => (
            <Pressable
              key={cat.id}
              style={[styles.chip, categoryFilter === cat.id && styles.chipActive]}
              onPress={() => setCategoryFilter(cat.id)}>
              <Text style={[styles.chipText, categoryFilter === cat.id && styles.chipTextActive]}>
                {cat.label}
              </Text>
            </Pressable>
          ))}
        </View>
      </Card>

      <Card>
        <Text style={styles.sectionTitle}>Apps on device ({selectedPackages.size} selected)</Text>
        <TextInput
          style={styles.searchInput}
          value={appSearch}
          onChangeText={setAppSearch}
          placeholder="Search apps…"
          placeholderTextColor="#94a3b8"
          autoCapitalize="none"
        />
        {installedApps.length === 0 ? (
          <Text style={styles.emptyApps}>
            App list syncs from the child phone. Open the child app once and wait a few minutes.
          </Text>
        ) : (
          <FlatList
            style={styles.appList}
            data={filteredApps}
            keyExtractor={item => item.packageName}
            nestedScrollEnabled
            keyboardShouldPersistTaps="handled"
            renderItem={({item}) => {
              const selected = selectedPackages.has(item.packageName);
              return (
                <Pressable
                  style={[styles.appRow, selected && styles.appRowSelected]}
                  onPress={() => togglePackage(item.packageName)}>
                  <Text style={styles.check}>{selected ? '☑' : '☐'}</Text>
                  <View style={styles.appText}>
                    <Text style={styles.appLabel}>{item.appLabel}</Text>
                    <Text style={styles.appPackage}>
                      {item.appCategory ?? 'OTHER'} · {item.packageName}
                    </Text>
                  </View>
                </Pressable>
              );
            }}
          />
        )}
        <View style={styles.rowBtns}>
          <Pressable style={styles.secondaryBtn} onPress={selectCategoryApps}>
            <Text style={styles.secondaryBtnText}>Select filtered</Text>
          </Pressable>
          <Pressable style={styles.secondaryBtn} onPress={() => setSelectedPackages(new Set())}>
            <Text style={styles.secondaryBtnText}>Clear</Text>
          </Pressable>
        </View>
      </Card>

      <Card>
        <Text style={styles.sectionTitle}>App blocking</Text>
        <PrimaryBtn title={`Block ${selectedPackages.size || ''} selected app(s)`} onPress={createAppBlock} />
        <View style={styles.spacer} />
        <PrimaryBtn title="Block entire category" onPress={createCategoryBlock} />
      </Card>

      <Card>
        <Text style={styles.sectionTitle}>Screen time limit</Text>
        <FormField
          label="Default daily limit (minutes)"
          value={limitMinutes}
          onChangeText={setLimitMinutes}
          keyboardType="number-pad"
        />
        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>Customize per weekday</Text>
          <Switch value={useWeeklyLimits} onValueChange={setUseWeeklyLimits} />
        </View>
        {useWeeklyLimits
          ? WEEKDAYS.map(day => (
              <View key={day.key} style={styles.dayRow}>
                <Switch
                  value={weeklySchedule[day.key].enabled}
                  onValueChange={v => updateDayLimit(day.key, 'enabled', v)}
                />
                <Text style={styles.dayLabel}>{day.label}</Text>
                <TextInput
                  style={styles.dayInput}
                  keyboardType="number-pad"
                  value={String(weeklySchedule[day.key].limitMinutes)}
                  onChangeText={t =>
                    updateDayLimit(day.key, 'limitMinutes', parseInt(t, 10) || 0)
                  }
                />
                <Text style={styles.dayMin}>min</Text>
              </View>
            ))
          : null}
        <PrimaryBtn title="Set limit for selected apps" onPress={createScreenTime} />
      </Card>

      <Text style={styles.listHeading}>Active policies ({policies.length})</Text>
      {policies.length === 0 ? (
        <Card>
          <Text style={styles.emptyPolicies}>No policies yet.</Text>
        </Card>
      ) : (
        policies.map(p => (
          <View key={p.id} style={styles.policyRow}>
            <Text style={styles.policyIcon}>🛡️</Text>
            <View style={styles.policyText}>
              <Text style={styles.policyName}>{p.name}</Text>
              <Text style={styles.policyMeta}>{p.policyType}</Text>
            </View>
          </View>
        ))
      )}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  sectionTitle: {fontSize: 15, fontWeight: '700', color: '#0f172a', marginBottom: 8},
  chips: {flexDirection: 'row', flexWrap: 'wrap', gap: 8},
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: '#f1f5f9',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  chipActive: {backgroundColor: '#eff6ff', borderColor: '#2563eb'},
  chipText: {fontSize: 13, color: '#475569', fontWeight: '600'},
  chipTextActive: {color: '#1d4ed8'},
  searchInput: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    color: '#0f172a',
    marginBottom: 8,
  },
  appList: {maxHeight: 220, marginBottom: 8},
  appRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 8,
    borderRadius: 10,
    marginBottom: 4,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    backgroundColor: '#f8fafc',
  },
  appRowSelected: {borderColor: '#2563eb', backgroundColor: '#eff6ff'},
  check: {fontSize: 18, marginRight: 10, color: '#2563eb'},
  appText: {flex: 1},
  appLabel: {fontSize: 15, fontWeight: '600', color: '#0f172a'},
  appPackage: {fontSize: 11, color: '#94a3b8', marginTop: 2},
  rowBtns: {flexDirection: 'row', gap: 8},
  secondaryBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 10,
    backgroundColor: '#f1f5f9',
    alignItems: 'center',
  },
  secondaryBtnText: {fontWeight: '600', color: '#475569', fontSize: 13},
  spacer: {height: 8},
  switchRow: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8},
  switchLabel: {fontSize: 14, color: '#334155', fontWeight: '600'},
  dayRow: {flexDirection: 'row', alignItems: 'center', marginBottom: 6, gap: 8},
  dayLabel: {flex: 1, fontSize: 14, color: '#334155'},
  dayInput: {
    width: 56,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 6,
    textAlign: 'center',
  },
  dayMin: {fontSize: 12, color: '#94a3b8', width: 28},
  emptyApps: {color: '#64748b', fontSize: 13, lineHeight: 18, marginVertical: 8},
  listHeading: {
    fontSize: 13,
    fontWeight: '600',
    color: '#64748b',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 10,
    marginTop: 4,
  },
  policyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    borderRadius: 14,
    padding: 14,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  policyIcon: {fontSize: 20, marginRight: 12},
  policyText: {flex: 1},
  policyName: {fontWeight: '600', color: '#0f172a'},
  policyMeta: {color: '#64748b', fontSize: 12, marginTop: 2},
  emptyPolicies: {color: '#64748b', fontSize: 14, textAlign: 'center'},
});
