import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  RefreshControl,
  ActivityIndicator,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {InstalledAppItem, UsageStatEntry, monitoringApi} from '../services/api';
import {formatUsageMs} from '../utils/formatUsage';

type Props = NativeStackScreenProps<RootStackParamList, 'ScreenTime'>;

type Tab = 'daily' | 'weekly';

type UsageRow = {
  packageName: string;
  appLabel: string;
  usageMs: number;
};

function aggregateUsage(entries: UsageStatEntry[], labelMap: Map<string, string>): UsageRow[] {
  const map = new Map<string, number>();
  entries.forEach(e => {
    map.set(e.packageName, Math.max(map.get(e.packageName) ?? 0, e.usageMs));
  });
  return Array.from(map.entries())
    .map(([pkg, ms]) => ({
      packageName: pkg,
      appLabel: labelMap.get(pkg) ?? pkg.split('.').pop() ?? pkg,
      usageMs: ms,
    }))
    .sort((a, b) => b.usageMs - a.usageMs);
}

function BarChart({rows, totalMs}: {rows: UsageRow[]; totalMs: number}) {
  const top5 = rows.slice(0, 5);
  const maxMs = top5[0]?.usageMs ?? 1;

  // Bar chart: 4 segments (0h, 6h, 12h, 18h markers)
  const BAR_COLORS = ['#7c3aed', '#9b59b6', '#a78bfa', '#c4b5fd', '#ddd6fe'];

  return (
    <View style={chart.wrap}>
      {/* Total */}
      <Text style={chart.totalMs}>{totalMs > 0 ? formatUsageMs(totalMs) : '0 min'}</Text>
      <Text style={chart.totalLabel}>total screen time today</Text>

      {/* Horizontal bars */}
      {top5.map((row, i) => {
        const pct = maxMs > 0 ? row.usageMs / maxMs : 0;
        return (
          <View key={row.packageName} style={chart.barRow}>
            <Text style={chart.barLabel} numberOfLines={1}>{row.appLabel}</Text>
            <View style={chart.barTrack}>
              <View style={[chart.barFill, {flex: pct, backgroundColor: BAR_COLORS[i] ?? '#7c3aed'}]} />
              <View style={{flex: 1 - pct}} />
            </View>
            <Text style={chart.barTime}>{formatUsageMs(row.usageMs)}</Text>
          </View>
        );
      })}

      {top5.length === 0 && (
        <Text style={chart.noData}>Your child has not used any apps yet</Text>
      )}
    </View>
  );
}

function WeeklyChart({weekRows}: {weekRows: {day: string; totalMs: number}[]}) {
  const maxMs = Math.max(...weekRows.map(r => r.totalMs), 1);
  return (
    <View style={chart.weekWrap}>
      {weekRows.map(row => {
        const heightPct = row.totalMs / maxMs;
        return (
          <View key={row.day} style={chart.dayCol}>
            <Text style={chart.dayTime} numberOfLines={1}>
              {row.totalMs > 0 ? formatUsageMs(row.totalMs) : ''}
            </Text>
            <View style={chart.dayBarTrack}>
              <View style={{flex: 1 - heightPct}} />
              <View style={[chart.dayBar, {flex: heightPct > 0 ? heightPct : 0.01}]} />
            </View>
            <Text style={chart.dayLabel}>{row.day}</Text>
          </View>
        );
      })}
    </View>
  );
}

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export function ScreenTimeScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [tab, setTab] = useState<Tab>('daily');
  const [usageEntries, setUsageEntries] = useState<UsageStatEntry[]>([]);
  const [labelMap, setLabelMap] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [dateOffset, setDateOffset] = useState(0); // 0 = today, -1 = yesterday, etc.

  const load = useCallback(async () => {
    try {
      const [usageRes, appsRes] = await Promise.all([
        monitoringApi.usageStats(deviceId, 0, 200),
        monitoringApi.installedApps(deviceId).catch(() => ({data: {data: [] as InstalledAppItem[]}})),
      ]);
      const map = new Map<string, string>();
      appsRes.data.data.forEach(app => map.set(app.packageName, app.appLabel));
      setLabelMap(map);
      setUsageEntries(usageRes.data.data.content);
    } catch {
      // silent - show empty state
    } finally {
      setLoading(false);
    }
  }, [deviceId]);

  useEffect(() => {
    load();
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  // Date label
  const dateLabel = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + dateOffset);
    return `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}`;
  }, [dateOffset]);

  // Filter entries by selected date
  const dailyRows = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + dateOffset);
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const dayEntries = usageEntries.filter(e => e.recordedAt?.startsWith(dateStr));
    return aggregateUsage(dayEntries.length > 0 ? dayEntries : usageEntries, labelMap);
  }, [usageEntries, labelMap, dateOffset]);

  const dailyTotal = useMemo(() => dailyRows.reduce((s, r) => s + r.usageMs, 0), [dailyRows]);

  // Weekly usage
  const weekRows = useMemo(() => {
    return WEEKDAYS.map((day, i) => {
      const d = new Date();
      const dayDiff = i - d.getDay();
      const target = new Date(d);
      target.setDate(d.getDate() + dayDiff);
      const dateStr = `${target.getFullYear()}-${String(target.getMonth() + 1).padStart(2, '0')}-${String(target.getDate()).padStart(2, '0')}`;
      const dayEntries = usageEntries.filter(e => e.recordedAt?.startsWith(dateStr));
      const totalMs = aggregateUsage(dayEntries, labelMap).reduce((s, r) => s + r.usageMs, 0);
      return {day, totalMs};
    });
  }, [usageEntries, labelMap]);

  const weekTotal = useMemo(() => weekRows.reduce((s, r) => s + r.totalMs, 0), [weekRows]);

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      {/* Header */}
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>‹ Back</Text>
        </Pressable>
        <Text style={styles.headerTitle}>Usage Report</Text>
        <Pressable onPress={() => { setLoading(true); load(); }} style={styles.refreshBtn}>
          <Text style={styles.refreshText}>↻</Text>
        </Pressable>
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}>

        {/* Tab switcher */}
        <View style={styles.tabSwitch}>
          <Pressable
            style={[styles.tabBtn, tab === 'daily' && styles.tabBtnActive]}
            onPress={() => setTab('daily')}>
            <Text style={[styles.tabBtnText, tab === 'daily' && styles.tabBtnTextActive]}>Daily</Text>
          </Pressable>
          <Pressable
            style={[styles.tabBtn, tab === 'weekly' && styles.tabBtnActive]}
            onPress={() => setTab('weekly')}>
            <Text style={[styles.tabBtnText, tab === 'weekly' && styles.tabBtnTextActive]}>Weekly</Text>
          </Pressable>
        </View>

        {/* Date navigation (daily only) */}
        {tab === 'daily' && (
          <View style={styles.dateNav}>
            <Pressable style={styles.dateArrow} onPress={() => setDateOffset(o => o - 1)}>
              <Text style={styles.dateArrowText}>‹</Text>
            </Pressable>
            <Text style={styles.dateLabel}>{dateLabel}</Text>
            <Pressable
              style={styles.dateArrow}
              onPress={() => setDateOffset(o => Math.min(0, o + 1))}
              disabled={dateOffset >= 0}>
              <Text style={[styles.dateArrowText, dateOffset >= 0 && {opacity: 0.3}]}>›</Text>
            </Pressable>
          </View>
        )}

        {loading ? (
          <ActivityIndicator color="#7c3aed" size="large" style={{marginTop: 60}} />
        ) : tab === 'daily' ? (
          <>
            {/* Screen Usage Time card */}
            <View style={styles.card}>
              <View style={styles.cardTitleRow}>
                <Text style={styles.cardIcon}>🕐</Text>
                <Text style={styles.cardTitle}>Screen Usage Time</Text>
              </View>
              <BarChart rows={dailyRows} totalMs={dailyTotal} />
              <Pressable
                style={styles.limitsBtn}
                onPress={() => navigation.navigate('Policies', {deviceId})}>
                <Text style={styles.limitsBtnText}>Screen Time Limits  ›</Text>
              </Pressable>
            </View>

            {/* App Usage Time card */}
            <View style={styles.card}>
              <View style={styles.cardTitleRow}>
                <Text style={styles.cardIcon}>📲</Text>
                <Text style={styles.cardTitle}>App Usage Time</Text>
              </View>
              {dailyRows.length === 0 ? (
                <View style={styles.emptyApp}>
                  <Text style={styles.emptyAppIcon}>📱</Text>
                  <Text style={styles.emptyAppText}>Your child has not used any apps yet</Text>
                </View>
              ) : (
                dailyRows.map((row, i) => (
                  <View key={row.packageName} style={styles.appRow}>
                    <View style={[styles.appRank, {backgroundColor: ['#7c3aed', '#9b59b6', '#a78bfa', '#c4b5fd', '#ddd6fe'][Math.min(i, 4)] ?? '#f1f5f9'}]}>
                      <Text style={styles.appRankNum}>{i + 1}</Text>
                    </View>
                    <View style={styles.appInfo}>
                      <Text style={styles.appName}>{row.appLabel}</Text>
                      <Text style={styles.appPkg} numberOfLines={1}>{row.packageName}</Text>
                    </View>
                    <Text style={styles.appTime}>{formatUsageMs(row.usageMs)}</Text>
                  </View>
                ))
              )}
            </View>
          </>
        ) : (
          <>
            {/* Weekly chart card */}
            <View style={styles.card}>
              <View style={styles.cardTitleRow}>
                <Text style={styles.cardIcon}>📅</Text>
                <Text style={styles.cardTitle}>Weekly Screen Time</Text>
              </View>
              <Text style={chart.totalMs}>{weekTotal > 0 ? formatUsageMs(weekTotal) : '0 min'}</Text>
              <Text style={chart.totalLabel}>total this week</Text>
              <WeeklyChart weekRows={weekRows} />
            </View>

            {/* Per-day breakdown */}
            <View style={styles.card}>
              <View style={styles.cardTitleRow}>
                <Text style={styles.cardIcon}>📊</Text>
                <Text style={styles.cardTitle}>Daily Breakdown</Text>
              </View>
              {weekRows.map(row => (
                <View key={row.day} style={styles.appRow}>
                  <View style={styles.dayDot} />
                  <Text style={styles.dayName}>{row.day}</Text>
                  <Text style={styles.appTime}>{row.totalMs > 0 ? formatUsageMs(row.totalMs) : '—'}</Text>
                </View>
              ))}
            </View>
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const chart = StyleSheet.create({
  wrap: {paddingTop: 8},
  totalMs: {fontSize: 36, fontWeight: '800', color: '#7c3aed'},
  totalLabel: {fontSize: 13, color: '#94a3b8', marginBottom: 16},
  barRow: {flexDirection: 'row', alignItems: 'center', marginBottom: 10},
  barLabel: {width: 80, fontSize: 12, color: '#475569', fontWeight: '600'},
  barTrack: {flex: 1, height: 8, borderRadius: 4, flexDirection: 'row', backgroundColor: '#f1f5f9', overflow: 'hidden', marginHorizontal: 8},
  barFill: {borderRadius: 4},
  barTime: {width: 52, fontSize: 12, fontWeight: '700', color: '#0f172a', textAlign: 'right'},
  noData: {fontSize: 14, color: '#94a3b8', textAlign: 'center', paddingVertical: 20},

  weekWrap: {flexDirection: 'row', alignItems: 'flex-end', height: 100, marginTop: 16, marginBottom: 4},
  dayCol: {flex: 1, alignItems: 'center', height: 100},
  dayBarTrack: {flex: 1, width: 22, flexDirection: 'column'},
  dayBar: {backgroundColor: '#7c3aed', borderRadius: 4, width: 22},
  dayLabel: {fontSize: 11, color: '#64748b', fontWeight: '600', marginTop: 4},
  dayTime: {fontSize: 9, color: '#94a3b8', textAlign: 'center'},
});

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#f8f8ff'},
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 14,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  backBtn: {padding: 4},
  backText: {fontSize: 16, fontWeight: '700', color: '#7c3aed'},
  headerTitle: {fontSize: 18, fontWeight: '800', color: '#0f172a'},
  refreshBtn: {padding: 4},
  refreshText: {fontSize: 22, color: '#7c3aed'},

  scroll: {flex: 1},
  scrollContent: {padding: 16, paddingBottom: 32},

  tabSwitch: {
    flexDirection: 'row',
    backgroundColor: '#f1f5f9',
    borderRadius: 14,
    padding: 4,
    marginBottom: 14,
  },
  tabBtn: {flex: 1, paddingVertical: 9, borderRadius: 11, alignItems: 'center'},
  tabBtnActive: {backgroundColor: '#fff', shadowColor: '#000', shadowOffset: {width: 0, height: 1}, shadowOpacity: 0.08, shadowRadius: 4, elevation: 2},
  tabBtnText: {fontSize: 14, fontWeight: '600', color: '#94a3b8'},
  tabBtnTextActive: {color: '#7c3aed', fontWeight: '800'},

  dateNav: {flexDirection: 'row', alignItems: 'center', justifyContent: 'center', marginBottom: 14, gap: 20},
  dateArrow: {padding: 8},
  dateArrowText: {fontSize: 24, fontWeight: '700', color: '#7c3aed'},
  dateLabel: {fontSize: 16, fontWeight: '700', color: '#0f172a', minWidth: 60, textAlign: 'center'},

  card: {backgroundColor: '#fff', borderRadius: 16, padding: 16, marginBottom: 12, shadowColor: '#000', shadowOffset: {width: 0, height: 1}, shadowOpacity: 0.06, shadowRadius: 6, elevation: 2},
  cardTitleRow: {flexDirection: 'row', alignItems: 'center', marginBottom: 4},
  cardIcon: {fontSize: 18, marginRight: 8},
  cardTitle: {fontSize: 16, fontWeight: '800', color: '#0f172a'},

  limitsBtn: {marginTop: 12, paddingTop: 12, borderTopWidth: 1, borderTopColor: '#f1f5f9'},
  limitsBtnText: {fontSize: 14, fontWeight: '700', color: '#7c3aed'},

  emptyApp: {alignItems: 'center', paddingVertical: 24},
  emptyAppIcon: {fontSize: 36, marginBottom: 8},
  emptyAppText: {fontSize: 14, color: '#94a3b8', textAlign: 'center'},

  appRow: {flexDirection: 'row', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 0.5, borderBottomColor: '#f1f5f9'},
  appRank: {width: 28, height: 28, borderRadius: 8, alignItems: 'center', justifyContent: 'center', marginRight: 12},
  appRankNum: {fontSize: 13, fontWeight: '800', color: '#fff'},
  appInfo: {flex: 1, marginRight: 8},
  appName: {fontSize: 15, fontWeight: '700', color: '#0f172a'},
  appPkg: {fontSize: 11, color: '#94a3b8', marginTop: 1},
  appTime: {fontSize: 14, fontWeight: '700', color: '#7c3aed'},

  dayDot: {width: 8, height: 8, borderRadius: 4, backgroundColor: '#7c3aed', marginRight: 12},
  dayName: {fontSize: 15, fontWeight: '600', color: '#0f172a', flex: 1},
});
