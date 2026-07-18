import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  TextInput,
  Switch,
  FlatList,
  Alert,
  ActivityIndicator,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {
  deviceApi,
  InstalledAppItem,
  monitoringApi,
  policiesApi,
} from '../services/api';
import {appDisplayName} from '../utils/appDisplayName';

type Props = NativeStackScreenProps<RootStackParamList, 'AppRules'>;

const CATEGORY_LABELS: Record<string, string> = {
  BUSINESS: 'Business',
  COMMUNICATION: 'Communication',
  EDUCATION: 'Education',
  ENTERTAINMENT: 'Entertainment',
  FINANCE: 'Finance',
  GAMES: 'Games',
  HEALTH_AND_FITNESS: 'Health & Fitness',
  MAPS_AND_NAVIGATION: 'Maps & Navigation',
  MUSIC_AND_AUDIO: 'Music & Audio',
  NEWS_AND_MAGAZINES: 'News & Magazines',
  PRODUCTIVITY: 'Productivity',
  SHOPPING: 'Shopping',
  SOCIAL: 'Social',
  TOOLS: 'Tools',
  VIDEO: 'Video',
  OTHER: 'Other',
  UNDEFINED: 'Other',
};

function catLabel(cat?: string) {
  return CATEGORY_LABELS[cat?.toUpperCase() ?? 'OTHER'] ?? 'Other';
}

type GroupedApps = {category: string; label: string; apps: InstalledAppItem[]};

function groupByCategory(apps: InstalledAppItem[]): GroupedApps[] {
  const map = new Map<string, InstalledAppItem[]>();
  apps.forEach(app => {
    const cat = (app.appCategory ?? 'OTHER').toUpperCase();
    const bucket = map.get(cat) ?? [];
    bucket.push(app);
    map.set(cat, bucket);
  });
  return Array.from(map.entries())
    .map(([cat, list]) => ({category: cat, label: catLabel(cat), apps: list.sort((a, b) => a.appLabel.localeCompare(b.appLabel))}))
    .sort((a, b) => a.label.localeCompare(b.label));
}

type RowItem =
  | {type: 'header'; category: string; label: string; total: number; blocked: number; expanded: boolean}
  | {type: 'app'; app: InstalledAppItem; isBlocked: boolean; isAllowed: boolean};

export function AppRulesScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [apps, setApps] = useState<InstalledAppItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');
  const [blockNewApps, setBlockNewApps] = useState(false);
  const [expandedCats, setExpandedCats] = useState<Set<string>>(new Set());
  const [pendingBlocks, setPendingBlocks] = useState<Set<string>>(new Set());
  const [syncingApps, setSyncingApps] = useState(false);
  const [blockedFromPolicies, setBlockedFromPolicies] = useState<Set<string>>(new Set());

  const load = useCallback(async (opts?: {requestSyncIfEmpty?: boolean}) => {
    try {
      const [appsRes, bundleRes] = await Promise.all([
        monitoringApi.installedApps(deviceId).catch(() => ({data: {data: [] as InstalledAppItem[]}})),
        policiesApi.deviceBundle(deviceId).catch(() => null),
      ]);
      let list = appsRes.data.data ?? [];
      setApps(list);

      const blocked = new Set<string>();
      let blockNew = false;
      for (const rule of bundleRes?.data?.data?.appBlocks ?? []) {
        if (rule.enabled === false) continue;
        if (rule.packageName === '*' && rule.scheduleJson?.blockAllApps) continue;
        if (rule.packageName === '*_new' || rule.scheduleJson?.blockNewInstalls) {
          blockNew = true;
          continue;
        }
        if (rule.packageName && rule.packageName !== '*' && rule.packageName !== '__category__') {
          blocked.add(rule.packageName);
        }
      }
      setBlockedFromPolicies(blocked);
      setBlockNewApps(blockNew);

      if (list.length === 0 && opts?.requestSyncIfEmpty !== false) {
        setSyncingApps(true);
        try {
          await deviceApi.sendCommand(deviceId, 'SYNC_INSTALLED_APPS');
          await new Promise(r => setTimeout(r, 2500));
          const again = await monitoringApi.installedApps(deviceId).catch(() => null);
          list = again?.data?.data ?? [];
          setApps(list);
        } catch {
          // Child may be offline — empty state remains.
        } finally {
          setSyncingApps(false);
        }
      }
    } catch {
      Alert.alert('Error', 'Couldn’t load apps. Check your connection and try again.');
    } finally {
      setLoading(false);
    }
  }, [deviceId]);

  useEffect(() => {
    load();
  }, [load]);

  const allBlocked = useMemo(
    () => new Set([...blockedFromPolicies, ...pendingBlocks]),
    [blockedFromPolicies, pendingBlocks],
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return apps;
    return apps.filter(a => a.appLabel.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q));
  }, [apps, search]);

  const groups = useMemo(() => groupByCategory(filtered), [filtered]);

  const toggleCat = (cat: string) => {
    setExpandedCats(prev => {
      const next = new Set(prev);
      if (next.has(cat)) next.delete(cat);
      else next.add(cat);
      return next;
    });
  };

  const toggleBlock = (pkg: string) => {
    if (blockedFromPolicies.has(pkg) && !pendingBlocks.has(pkg)) {
      Alert.alert(
        'Already blocked',
        'This app is already blocked on the child phone. Open Policies to remove the rule if you want to allow it again.',
      );
      return;
    }
    setPendingBlocks(prev => {
      const next = new Set(prev);
      if (next.has(pkg)) next.delete(pkg);
      else next.add(pkg);
      return next;
    });
  };

  const applyBlocks = async () => {
    if (pendingBlocks.size === 0 && !blockNewApps) {
      Alert.alert('No changes', 'Select apps to block or enable Block New Apps.');
      return;
    }
    setSaving(true);
    try {
      if (pendingBlocks.size > 0) {
        const rules = Array.from(pendingBlocks).map(packageName => ({packageName, scheduleType: 'ALWAYS' as const}));
        await policiesApi.createAppBlock({name: `Block ${pendingBlocks.size} app(s)`, deviceIds: [deviceId], rules});
      }
      if (blockNewApps) {
        await policiesApi.createAppBlock({
          name: 'Block new installs',
          deviceIds: [deviceId],
          rules: [{packageName: '*_new', scheduleType: 'ALWAYS', scheduleJson: {blockNewInstalls: true}}],
        });
      }
      await load();
      Alert.alert('Saved', `Rules applied to child device.`);
      setPendingBlocks(new Set());
    } catch (e) {
      const msg =
        e && typeof e === 'object' && 'response' in e
          ? (e as {response?: {data?: {message?: string}}}).response?.data?.message?.trim()
          : undefined;
      Alert.alert(
        'Error',
        msg || 'Couldn’t apply rules. Check the child is paired and try again.',
      );
    } finally {
      setSaving(false);
    }
  };

  // Build flat list
  const rows: RowItem[] = useMemo(() => {
    const result: RowItem[] = [];
    groups.forEach(({category, label, apps: groupApps}) => {
      const expanded = expandedCats.has(category);
      const blockedInCat = groupApps.filter(a => allBlocked.has(a.packageName)).length;
      result.push({type: 'header', category, label, total: groupApps.length, blocked: blockedInCat, expanded});
      if (expanded) {
        groupApps.forEach(app => {
          result.push({type: 'app', app, isBlocked: allBlocked.has(app.packageName), isAllowed: false});
        });
      }
    });
    return result;
  }, [groups, expandedCats, allBlocked]);

  const renderRow = ({item}: {item: RowItem}) => {
    if (item.type === 'header') {
      return (
        <Pressable style={styles.catHeader} onPress={() => toggleCat(item.category)}>
          <View style={styles.catCircle}>
            <View style={[styles.catCheck, item.blocked > 0 && styles.catCheckBlocked]} />
          </View>
          <Text style={styles.catLabel}>
            {item.label}  <Text style={styles.catCount}>({item.blocked}/{item.total})</Text>
          </Text>
          <Text style={styles.chevron}>{item.expanded ? '∧' : '∨'}</Text>
        </Pressable>
      );
    }
    const {app, isBlocked} = item;
    return (
      <Pressable style={styles.appRow} onPress={() => toggleBlock(app.packageName)}>
        <View style={[styles.appCircle, isBlocked && styles.appCircleBlocked]}>
          {isBlocked && <Text style={styles.appCircleCheck}>✓</Text>}
        </View>
        <View style={styles.appInfo}>
          <Text style={styles.appName}>
            {appDisplayName({appLabel: app.appLabel, packageName: app.packageName})}
          </Text>
        </View>
        {item.isAllowed ? (
          <View style={styles.allowedChip}>
            <Text style={styles.allowedChipText}>Allowed</Text>
          </View>
        ) : isBlocked ? (
          <View style={styles.blockedChip}>
            <Text style={styles.blockedChipText}>Blocked</Text>
          </View>
        ) : null}
        <Text style={styles.appChevron}>›</Text>
      </Pressable>
    );
  };

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      {/* Header */}
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>‹</Text>
        </Pressable>
        <Text style={styles.headerTitle}>App Rules</Text>
        <Pressable style={styles.menuBtn}>
          <Text style={styles.menuText}>≡</Text>
        </Pressable>
      </View>

      {/* Search */}
      <View style={styles.searchWrap}>
        <Text style={styles.searchIcon}>🔍</Text>
        <TextInput
          style={styles.searchInput}
          placeholder="Search"
          placeholderTextColor="#94a3b8"
          value={search}
          onChangeText={setSearch}
        />
      </View>

      {/* Quick Settings */}
      <View style={styles.quickCard}>
        <Text style={styles.quickLabel}>Quick Settings</Text>
        <View style={styles.blockNewRow}>
          <View style={styles.blockNewLeft}>
            <Text style={styles.blockNewTitle}>Block New Apps</Text>
            <Text style={styles.blockNewSub}>Newly installed apps will be blocked</Text>
          </View>
          <Switch
            value={blockNewApps}
            onValueChange={setBlockNewApps}
            trackColor={{false: '#e2e8f0', true: '#7c3aed'}}
            thumbColor="#fff"
          />
        </View>
      </View>

      <Text style={styles.allAppsTitle}>All Apps</Text>

      {loading ? (
        <ActivityIndicator color="#7c3aed" size="large" style={{marginTop: 40}} />
      ) : apps.length === 0 ? (
        <View style={styles.emptyState}>
          <Text style={styles.emptyIcon}>📱</Text>
          <Text style={styles.emptyTitle}>
            {syncingApps ? 'Syncing apps…' : 'No apps found'}
          </Text>
          <Text style={styles.emptySub}>
            {syncingApps
              ? 'Asking the child phone for its app list. Keep Clock Time running and online.'
              : 'The child phone has not synced apps yet. Open Clock Time on the child, stay online, then tap Refresh.'}
          </Text>
          {!syncingApps ? (
            <Pressable
              style={styles.refreshEmptyBtn}
              onPress={() => {
                setLoading(true);
                void load({requestSyncIfEmpty: true});
              }}>
              <Text style={styles.refreshEmptyBtnText}>Refresh app list</Text>
            </Pressable>
          ) : (
            <ActivityIndicator color="#7c3aed" style={{marginTop: 16}} />
          )}
        </View>
      ) : (
        <FlatList
          style={styles.list}
          data={rows}
          keyExtractor={(item, i) =>
            item.type === 'header' ? `h-${item.category}` : `a-${item.app.packageName}-${i}`
          }
          renderItem={renderRow}
          contentContainerStyle={styles.listContent}
        />
      )}

      {/* Apply button */}
      {pendingBlocks.size > 0 && (
        <View style={styles.applyBar}>
          <Text style={styles.applyCount}>{pendingBlocks.size} app{pendingBlocks.size !== 1 ? 's' : ''} selected</Text>
          <Pressable
            style={[styles.applyBtn, saving && {opacity: 0.7}]}
            onPress={applyBlocks}
            disabled={saving}>
            <Text style={styles.applyBtnText}>{saving ? 'Applying…' : 'Apply Rules'}</Text>
          </Pressable>
        </View>
      )}
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
  menuBtn: {padding: 6},
  menuText: {fontSize: 22, color: '#0f172a'},

  searchWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    margin: 12,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  searchIcon: {fontSize: 16, marginRight: 8},
  searchInput: {flex: 1, fontSize: 15, color: '#0f172a'},

  quickCard: {
    backgroundColor: '#fff',
    marginHorizontal: 12,
    marginBottom: 12,
    borderRadius: 12,
    padding: 14,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  quickLabel: {fontSize: 13, fontWeight: '800', color: '#7c3aed', marginBottom: 10},
  blockNewRow: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  blockNewLeft: {flex: 1, marginRight: 12},
  blockNewTitle: {fontSize: 15, fontWeight: '700', color: '#0f172a'},
  blockNewSub: {fontSize: 12, color: '#64748b', marginTop: 2},

  allAppsTitle: {fontSize: 13, fontWeight: '800', color: '#7c3aed', paddingHorizontal: 16, marginBottom: 6},

  list: {flex: 1},
  listContent: {paddingBottom: 100},

  // Category header
  catHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginHorizontal: 12,
    marginBottom: 2,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 1,
  },
  catCircle: {width: 28, height: 28, borderRadius: 14, borderWidth: 2, borderColor: '#d1d5db', alignItems: 'center', justifyContent: 'center', marginRight: 12},
  catCheck: {width: 16, height: 16, borderRadius: 8},
  catCheckBlocked: {backgroundColor: '#7c3aed'},
  catLabel: {flex: 1, fontSize: 15, fontWeight: '700', color: '#0f172a'},
  catCount: {color: '#94a3b8', fontWeight: '400'},
  chevron: {fontSize: 14, color: '#94a3b8', fontWeight: '700'},

  // App row
  appRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginHorizontal: 12,
    marginBottom: 1,
    borderTopWidth: 0.5,
    borderTopColor: '#f1f5f9',
  },
  appCircle: {width: 26, height: 26, borderRadius: 13, borderWidth: 2, borderColor: '#d1d5db', alignItems: 'center', justifyContent: 'center', marginRight: 12},
  appCircleBlocked: {borderColor: '#7c3aed', backgroundColor: '#7c3aed'},
  appCircleCheck: {color: '#fff', fontSize: 14, fontWeight: '800'},
  appInfo: {flex: 1},
  appName: {fontSize: 15, fontWeight: '600', color: '#0f172a'},
  allowedChip: {backgroundColor: '#dcfce7', paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10, marginRight: 6},
  allowedChipText: {fontSize: 11, fontWeight: '700', color: '#16a34a'},
  blockedChip: {backgroundColor: '#fee2e2', paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10, marginRight: 6},
  blockedChipText: {fontSize: 11, fontWeight: '700', color: '#dc2626'},
  appChevron: {fontSize: 20, color: '#d1d5db'},

  emptyState: {flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32, paddingTop: 60},
  emptyIcon: {fontSize: 52, marginBottom: 12},
  emptyTitle: {fontSize: 18, fontWeight: '700', color: '#0f172a', textAlign: 'center'},
  refreshEmptyBtn: {
    marginTop: 20,
    backgroundColor: '#7c3aed',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 12,
  },
  refreshEmptyBtnText: {color: '#fff', fontWeight: '700', fontSize: 14},
  emptySub: {fontSize: 14, color: '#64748b', textAlign: 'center', marginTop: 8, lineHeight: 20},

  applyBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: '#fff',
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: '#f1f5f9',
    shadowColor: '#000',
    shadowOffset: {width: 0, height: -2},
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 8,
  },
  applyCount: {flex: 1, fontSize: 14, fontWeight: '600', color: '#475569'},
  applyBtn: {backgroundColor: '#7c3aed', borderRadius: 12, paddingHorizontal: 20, paddingVertical: 12},
  applyBtnText: {color: '#fff', fontWeight: '800', fontSize: 14},
});
