import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  RefreshControl,
  ActivityIndicator,
  Image,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {
  deviceApi,
  DeviceSummary,
  InstalledAppItem,
  UsageStatEntry,
  monitoringApi,
} from '../services/api';
import {formatUsageMs} from '../utils/formatUsage';
import {friendlyPackageLabel, appDisplayName} from '../utils/appDisplayName';

type Props = NativeStackScreenProps<RootStackParamList, 'ScreenTime'>;

type Tab = 'daily' | 'weekly';

type UsageRow = {
  packageName: string;
  appLabel: string;
  usageMs: number;
  iconBase64?: string | null;
};

const AVATAR_COLORS = ['#7c3aed', '#9b59b6', '#a78bfa', '#6366f1', '#8b5cf6'];

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/** Parent-local YYYY-MM-DD from an Instant ISO string (avoids UTC date mismatch). */
function localDateKey(iso: string | undefined | null): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return iso.length >= 10 ? iso.slice(0, 10) : null;
  }
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function latestRecordedAt(entries: UsageStatEntry[]): string {
  return entries.reduce((max, e) => ((e.recordedAt ?? '') > max ? e.recordedAt ?? '' : max), '');
}

function aggregateUsage(
  entries: UsageStatEntry[],
  labelMap: Map<string, string>,
  iconMap: Map<string, string>,
): UsageRow[] {
  const SYSTEM_NOISE = new Set([
    'com.miui.home',
    'com.android.systemui',
    'com.google.android.permissioncontroller',
    'com.android.permissioncontroller',
    'com.android.launcher3',
    'com.android.settings',
    'com.miui.securitycenter',
  ]);
  // Newest ingest first (createdAt), so a corrected re-upload beats older inflated rows
  // that share the same end-of-day recordedAt stamp.
  const sorted = [...entries].sort((a, b) => {
    const ca = a.createdAt ?? a.recordedAt ?? '';
    const cb = b.createdAt ?? b.recordedAt ?? '';
    return cb.localeCompare(ca);
  });
  const map = new Map<string, number>();
  sorted.forEach(e => {
    if (SYSTEM_NOISE.has(e.packageName)) return;
    if (!map.has(e.packageName)) {
      map.set(e.packageName, e.usageMs);
    }
  });
  return Array.from(map.entries())
    .map(([pkg, ms]) => ({
      packageName: pkg,
      appLabel: labelMap.get(pkg) ?? friendlyPackageLabel(pkg),
      usageMs: ms,
      iconBase64: iconMap.get(pkg) ?? null,
    }))
    .sort((a, b) => b.usageMs - a.usageMs);
}

function AppIcon({
  label,
  iconBase64,
  index,
}: {
  label: string;
  iconBase64?: string | null;
  index: number;
}) {
  const letter = (label.trim().charAt(0) || '?').toUpperCase();
  const bg = AVATAR_COLORS[index % AVATAR_COLORS.length];
  if (iconBase64) {
    return (
      <Image
        source={{uri: `data:image/png;base64,${iconBase64}`}}
        style={styles.appIconImg}
        resizeMode="cover"
      />
    );
  }
  return (
    <View style={[styles.appIconFallback, {backgroundColor: bg}]}>
      <Text style={styles.appIconLetter}>{letter}</Text>
    </View>
  );
}

function BarChart({rows, totalMs}: {rows: UsageRow[]; totalMs: number}) {
  const top5 = rows.slice(0, 5);
  const maxMs = top5[0]?.usageMs ?? 1;
  const BAR_COLORS = ['#7c3aed', '#9b59b6', '#a78bfa', '#c4b5fd', '#ddd6fe'];

  return (
    <View style={chart.wrap}>
      <Text style={chart.totalMs}>{totalMs > 0 ? formatUsageMs(totalMs) : '0 min'}</Text>
      <Text style={chart.totalLabel}>total screen time today</Text>

      {top5.map((row, i) => {
        const pct = maxMs > 0 ? row.usageMs / maxMs : 0;
        return (
          <View key={row.packageName} style={chart.barRow}>
            <Text style={chart.barLabel} numberOfLines={1}>
              {row.appLabel}
            </Text>
            <View style={chart.barTrack}>
              <View
                style={[chart.barFill, {flex: pct, backgroundColor: BAR_COLORS[i] ?? '#7c3aed'}]}
              />
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

type WeekDayRow = {
  day: string;
  dateStr: string;
  date: Date;
  dateOffset: number;
  totalMs: number;
};

function WeeklyChart({
  weekRows,
  onSelectDay,
}: {
  weekRows: WeekDayRow[];
  onSelectDay: (dateOffset: number) => void;
}) {
  const maxMs = Math.max(...weekRows.map(r => r.totalMs), 1);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  return (
    <View style={chart.weekWrap}>
      {weekRows.map(row => {
        const heightPct = row.totalMs > 0 ? Math.max(row.totalMs / maxMs, 0.04) : 0;
        const isToday = row.date.getTime() === today.getTime();
        const isFuture = row.dateOffset > 0;
        return (
          <Pressable
            key={row.dateStr}
            style={chart.dayCol}
            disabled={isFuture}
            onPress={() => onSelectDay(row.dateOffset)}>
            <Text style={chart.dayTime} numberOfLines={1}>
              {row.totalMs > 0 ? formatUsageMs(row.totalMs) : ''}
            </Text>
            <View style={chart.dayBarTrack}>
              <View style={{flex: 1 - heightPct}} />
              <View
                style={[
                  chart.dayBar,
                  {
                    flex: heightPct > 0 ? heightPct : 0.015,
                    backgroundColor: isToday ? '#7c3aed' : row.totalMs > 0 ? '#a78bfa' : '#e2e8f0',
                    opacity: isFuture ? 0.35 : 1,
                  },
                ]}
              />
            </View>
            <Text style={[chart.dayLabel, isToday && chart.dayLabelToday]}>{row.day}</Text>
            <Text style={chart.dayDate}>{row.date.getDate()}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function formatWeekRange(start: Date, end: Date): string {
  const opts: Intl.DateTimeFormatOptions = {month: 'short', day: 'numeric'};
  return `${start.toLocaleDateString(undefined, opts)} – ${end.toLocaleDateString(undefined, opts)}`;
}

export function ScreenTimeScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [tab, setTab] = useState<Tab>('daily');
  const [usageEntries, setUsageEntries] = useState<UsageStatEntry[]>([]);
  const [labelMap, setLabelMap] = useState<Map<string, string>>(new Map());
  const [iconMap, setIconMap] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [dateOffset, setDateOffset] = useState(0);
  const [device, setDevice] = useState<DeviceSummary | null>(null);
  const [lastSyncedAt, setLastSyncedAt] = useState<string | null>(null);

  const applyApps = (apps: InstalledAppItem[]) => {
    const labels = new Map<string, string>();
    const icons = new Map<string, string>();
    apps.forEach(app => {
      labels.set(
        app.packageName,
        appDisplayName({appLabel: app.appLabel, packageName: app.packageName}),
      );
      if (app.iconBase64) {
        icons.set(app.packageName, app.iconBase64);
      }
    });
    setLabelMap(labels);
    setIconMap(icons);
  };

  const load = useCallback(async (opts?: {requestFreshSync?: boolean}) => {
    try {
      let usageContent: UsageStatEntry[] | null = null;

      if (opts?.requestFreshSync) {
        let previousLatest = '';
        try {
          const before = await monitoringApi.usageStats(deviceId, 0, 500);
          previousLatest = latestRecordedAt(before.data.data.content ?? []);
        } catch {
          // still request sync
        }

        await Promise.all([
          deviceApi.sendCommand(deviceId, 'SYNC_USAGE_STATS').catch(() => undefined),
          deviceApi.sendCommand(deviceId, 'SYNC_INSTALLED_APPS').catch(() => undefined),
        ]);

        for (let i = 0; i < 5; i++) {
          await sleep(1500);
          try {
            const poll = await monitoringApi.usageStats(deviceId, 0, 500);
            const content = poll.data.data.content ?? [];
            usageContent = content;
            const latest = latestRecordedAt(content);
            if (latest && (!previousLatest || latest > previousLatest)) {
              break;
            }
          } catch {
            // keep polling
          }
        }
        // Pull a second page so Weekly can show earlier days in the week.
        if (usageContent) {
          const page1 = await monitoringApi.usageStats(deviceId, 1, 500).catch(() => null);
          if (page1?.data.data.content?.length) {
            usageContent = [...usageContent, ...page1.data.data.content];
          }
        }
      }

      if (!usageContent) {
        const [page0, page1] = await Promise.all([
          monitoringApi.usageStats(deviceId, 0, 500),
          monitoringApi.usageStats(deviceId, 1, 500).catch(() => null),
        ]);
        usageContent = [
          ...(page0.data.data.content ?? []),
          ...(page1?.data.data.content ?? []),
        ];
      }

      const [appsRes, deviceRes] = await Promise.all([
        monitoringApi.installedApps(deviceId).catch(() => ({data: {data: [] as InstalledAppItem[]}})),
        deviceApi.get(deviceId).catch(() => null),
      ]);

      setUsageEntries(usageContent);
      setLastSyncedAt(latestRecordedAt(usageContent) || null);
      const apps = appsRes.data.data ?? [];
      applyApps(apps);
      setDevice(deviceRes?.data?.data ?? null);
      if (apps.length === 0) {
        void deviceApi.sendCommand(deviceId, 'SYNC_INSTALLED_APPS').catch(() => undefined);
      }
    } catch {
      // silent - show empty state
    } finally {
      setLoading(false);
    }
  }, [deviceId]);

  useEffect(() => {
    void load({requestFreshSync: true});
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load({requestFreshSync: true});
    setRefreshing(false);
  };

  const dateLabel = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + dateOffset);
    return `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}`;
  }, [dateOffset]);

  const dailyRows = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + dateOffset);
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const dayEntries = usageEntries.filter(e => localDateKey(e.recordedAt) === dateStr);
    const pool = dayEntries.length > 0 ? dayEntries : usageEntries;
    return aggregateUsage(pool, labelMap, iconMap);
  }, [usageEntries, labelMap, iconMap, dateOffset]);

  const dailyTotal = useMemo(() => dailyRows.reduce((s, r) => s + r.usageMs, 0), [dailyRows]);

  const weekRows = useMemo((): WeekDayRow[] => {
    const now = new Date();
    now.setHours(0, 0, 0, 0);
    return WEEKDAYS.map((day, i) => {
      const dayDiff = i - now.getDay();
      const target = new Date(now);
      target.setDate(now.getDate() + dayDiff);
      const dateStr = `${target.getFullYear()}-${String(target.getMonth() + 1).padStart(2, '0')}-${String(target.getDate()).padStart(2, '0')}`;
      const dayEntries = usageEntries.filter(e => localDateKey(e.recordedAt) === dateStr);
      const totalMs = aggregateUsage(dayEntries, labelMap, iconMap).reduce((s, r) => s + r.usageMs, 0);
      return {
        day,
        dateStr,
        date: target,
        dateOffset: dayDiff,
        totalMs,
      };
    });
  }, [usageEntries, labelMap, iconMap]);

  const weekTotal = useMemo(() => weekRows.reduce((s, r) => s + r.totalMs, 0), [weekRows]);

  const weekRangeLabel = useMemo(() => {
    if (weekRows.length === 0) return '';
    return formatWeekRange(weekRows[0].date, weekRows[6].date);
  }, [weekRows]);

  const weekAvgMs = useMemo(() => {
    const daysWithData = weekRows.filter(r => r.totalMs > 0 && r.dateOffset <= 0);
    if (daysWithData.length === 0) return 0;
    return Math.round(daysWithData.reduce((s, r) => s + r.totalMs, 0) / daysWithData.length);
  }, [weekRows]);

  const weeklyAppRows = useMemo(() => {
    const totals = new Map<string, {ms: number; label: string; icon?: string | null}>();
    for (const row of weekRows) {
      if (row.dateOffset > 0) continue;
      const dayEntries = usageEntries.filter(e => localDateKey(e.recordedAt) === row.dateStr);
      for (const app of aggregateUsage(dayEntries, labelMap, iconMap)) {
        const prev = totals.get(app.packageName);
        totals.set(app.packageName, {
          ms: (prev?.ms ?? 0) + app.usageMs,
          label: app.appLabel,
          icon: app.iconBase64,
        });
      }
    }
    return Array.from(totals.entries())
      .map(([packageName, v]) => ({
        packageName,
        appLabel: v.label,
        usageMs: v.ms,
        iconBase64: v.icon,
      }))
      .filter(r => r.usageMs > 0)
      .sort((a, b) => b.usageMs - a.usageMs);
  }, [weekRows, usageEntries, labelMap, iconMap]);

  const openDayFromWeek = (offset: number) => {
    if (offset > 0) return;
    setDateOffset(offset);
    setTab('daily');
  };

  const syncedHint = useMemo(() => {
    if (!lastSyncedAt) return null;
    const t = new Date(lastSyncedAt);
    if (Number.isNaN(t.getTime())) return null;
    return `Updated ${t.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})}`;
  }, [lastSyncedAt]);

  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <Pressable onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>‹ Back</Text>
        </Pressable>
        <Text style={styles.headerTitle}>Usage Report</Text>
        <Pressable
          onPress={() => {
            setLoading(true);
            void load({requestFreshSync: true});
          }}
          style={styles.refreshBtn}>
          <Text style={styles.refreshText}>↻</Text>
        </Pressable>
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}>
        {device?.permUsageStats === false ? (
          <View style={styles.permBanner}>
            <Text style={styles.permBannerTitle}>Usage Access is off on the child phone</Text>
            <Text style={styles.permBannerBody}>
              Screen time stays empty until Clock Time is allowed to view app usage (Settings →
              Special app access → Usage access).
            </Text>
          </View>
        ) : null}

        {syncedHint ? <Text style={styles.syncedHint}>{syncedHint}</Text> : null}

        <View style={styles.tabSwitch}>
          <Pressable
            style={[styles.tabBtn, tab === 'daily' && styles.tabBtnActive]}
            onPress={() => setTab('daily')}>
            <Text style={[styles.tabBtnText, tab === 'daily' && styles.tabBtnTextActive]}>
              Daily
            </Text>
          </Pressable>
          <Pressable
            style={[styles.tabBtn, tab === 'weekly' && styles.tabBtnActive]}
            onPress={() => setTab('weekly')}>
            <Text style={[styles.tabBtnText, tab === 'weekly' && styles.tabBtnTextActive]}>
              Weekly
            </Text>
          </Pressable>
        </View>

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
            <View style={styles.card}>
              <View style={styles.cardTitleRow}>
                <Text style={styles.cardIcon}>🕐</Text>
                <Text style={styles.cardTitle}>Screen Usage Time</Text>
              </View>
              <BarChart rows={dailyRows} totalMs={dailyTotal} />
              <Pressable
                style={styles.limitsBtn}
                onPress={() => navigation.navigate('Policies', {deviceId})}>
                <Text style={styles.limitsBtnText}>Screen Time Limits ›</Text>
              </Pressable>
            </View>

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
                    <AppIcon label={row.appLabel} iconBase64={row.iconBase64} index={i} />
                    <View style={styles.appInfo}>
                      <Text style={styles.appName}>{row.appLabel}</Text>
                    </View>
                    <Text style={styles.appTime}>{formatUsageMs(row.usageMs)}</Text>
                  </View>
                ))
              )}
            </View>
          </>
        ) : (
          <>
            <View style={styles.card}>
              <View style={styles.cardTitleRow}>
                <Text style={styles.cardIcon}>📅</Text>
                <Text style={styles.cardTitle}>This week</Text>
              </View>
              <Text style={styles.weekRange}>{weekRangeLabel}</Text>
              <Text style={chart.totalMs}>{weekTotal > 0 ? formatUsageMs(weekTotal) : '0 min'}</Text>
              <Text style={chart.totalLabel}>
                {weekAvgMs > 0
                  ? `total · avg ${formatUsageMs(weekAvgMs)} / day used`
                  : 'total screen time'}
              </Text>
              <WeeklyChart weekRows={weekRows} onSelectDay={openDayFromWeek} />
              <Text style={styles.weekHint}>Tap a day to see apps used that day</Text>
            </View>

            <View style={styles.card}>
              <View style={styles.cardTitleRow}>
                <Text style={styles.cardIcon}>📊</Text>
                <Text style={styles.cardTitle}>Each day</Text>
              </View>
              {weekRows.map(row => {
                const isFuture = row.dateOffset > 0;
                const isToday = row.dateOffset === 0;
                const maxMs = Math.max(...weekRows.map(r => r.totalMs), 1);
                const pct = row.totalMs > 0 ? row.totalMs / maxMs : 0;
                return (
                  <Pressable
                    key={row.dateStr}
                    style={[styles.weekDayRow, isFuture && {opacity: 0.4}]}
                    disabled={isFuture}
                    onPress={() => openDayFromWeek(row.dateOffset)}>
                    <View style={styles.weekDayLeft}>
                      <Text style={[styles.weekDayName, isToday && styles.weekDayNameToday]}>
                        {row.day}
                        {isToday ? ' · Today' : ''}
                      </Text>
                      <Text style={styles.weekDayDate}>
                        {row.date.toLocaleDateString(undefined, {month: 'short', day: 'numeric'})}
                      </Text>
                    </View>
                    <View style={styles.weekDayBarTrack}>
                      <View
                        style={[
                          styles.weekDayBarFill,
                          {width: `${Math.round(pct * 100)}%`},
                          isToday && {backgroundColor: '#7c3aed'},
                        ]}
                      />
                    </View>
                    <Text style={styles.appTime}>
                      {row.totalMs > 0 ? formatUsageMs(row.totalMs) : isFuture ? '—' : '0m'}
                    </Text>
                    {!isFuture ? <Text style={styles.weekDayChevron}>›</Text> : <View style={{width: 14}} />}
                  </Pressable>
                );
              })}
            </View>

            <View style={styles.card}>
              <View style={styles.cardTitleRow}>
                <Text style={styles.cardIcon}>📲</Text>
                <Text style={styles.cardTitle}>Top apps this week</Text>
              </View>
              {weeklyAppRows.length === 0 ? (
                <View style={styles.emptyApp}>
                  <Text style={styles.emptyAppText}>
                    No app usage recorded for this week yet. Open Daily and pull to refresh after
                    the child phone has synced.
                  </Text>
                </View>
              ) : (
                weeklyAppRows.slice(0, 10).map((row, i) => (
                  <View key={row.packageName} style={styles.appRow}>
                    <AppIcon label={row.appLabel} iconBase64={row.iconBase64} index={i} />
                    <View style={styles.appInfo}>
                      <Text style={styles.appName}>{row.appLabel}</Text>
                    </View>
                    <Text style={styles.appTime}>{formatUsageMs(row.usageMs)}</Text>
                  </View>
                ))
              )}
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
  barTrack: {
    flex: 1,
    height: 8,
    borderRadius: 4,
    flexDirection: 'row',
    backgroundColor: '#f1f5f9',
    overflow: 'hidden',
    marginHorizontal: 8,
  },
  barFill: {borderRadius: 4},
  barTime: {width: 52, fontSize: 12, fontWeight: '700', color: '#0f172a', textAlign: 'right'},
  noData: {fontSize: 14, color: '#94a3b8', textAlign: 'center', paddingVertical: 20},

  weekWrap: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    height: 140,
    marginTop: 12,
    marginBottom: 4,
  },
  dayCol: {flex: 1, alignItems: 'center', height: 140},
  dayBarTrack: {flex: 1, width: 26, flexDirection: 'column'},
  dayBar: {backgroundColor: '#7c3aed', borderRadius: 6, width: 26, minHeight: 3},
  dayLabel: {fontSize: 11, color: '#64748b', fontWeight: '600', marginTop: 6},
  dayLabelToday: {color: '#7c3aed', fontWeight: '800'},
  dayDate: {fontSize: 10, color: '#94a3b8', marginTop: 1},
  dayTime: {fontSize: 9, color: '#64748b', textAlign: 'center', minHeight: 12, fontWeight: '600'},
});

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#f8f8ff'},
  permBanner: {
    marginHorizontal: 16,
    marginTop: 12,
    marginBottom: 4,
    backgroundColor: '#FEF3C7',
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: '#FCD34D',
  },
  permBannerTitle: {fontSize: 14, fontWeight: '800', color: '#92400E', marginBottom: 4},
  permBannerBody: {fontSize: 13, color: '#78350F', lineHeight: 18},
  syncedHint: {
    fontSize: 12,
    color: '#94a3b8',
    textAlign: 'center',
    marginBottom: 8,
    marginTop: 4,
  },
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
  tabBtnActive: {
    backgroundColor: '#fff',
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.08,
    shadowRadius: 4,
    elevation: 2,
  },
  tabBtnText: {fontSize: 14, fontWeight: '600', color: '#94a3b8'},
  tabBtnTextActive: {color: '#7c3aed', fontWeight: '800'},

  dateNav: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
    gap: 20,
  },
  dateArrow: {padding: 8},
  dateArrowText: {fontSize: 24, fontWeight: '700', color: '#7c3aed'},
  dateLabel: {fontSize: 16, fontWeight: '700', color: '#0f172a', minWidth: 60, textAlign: 'center'},

  card: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 2,
  },
  cardTitleRow: {flexDirection: 'row', alignItems: 'center', marginBottom: 4},
  cardIcon: {fontSize: 18, marginRight: 8},
  cardTitle: {fontSize: 16, fontWeight: '800', color: '#0f172a'},

  limitsBtn: {marginTop: 12, paddingTop: 12, borderTopWidth: 1, borderTopColor: '#f1f5f9'},
  limitsBtnText: {fontSize: 14, fontWeight: '700', color: '#7c3aed'},

  emptyApp: {alignItems: 'center', paddingVertical: 24},
  emptyAppIcon: {fontSize: 36, marginBottom: 8},
  emptyAppText: {fontSize: 14, color: '#94a3b8', textAlign: 'center'},

  appRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: 0.5,
    borderBottomColor: '#f1f5f9',
  },
  appIconImg: {
    width: 36,
    height: 36,
    borderRadius: 10,
    marginRight: 12,
    backgroundColor: '#f1f5f9',
  },
  appIconFallback: {
    width: 36,
    height: 36,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  appIconLetter: {fontSize: 15, fontWeight: '800', color: '#fff'},
  appInfo: {flex: 1, marginRight: 8},
  appName: {fontSize: 15, fontWeight: '700', color: '#0f172a'},
  appTime: {fontSize: 14, fontWeight: '700', color: '#7c3aed'},

  dayDot: {width: 8, height: 8, borderRadius: 4, backgroundColor: '#7c3aed', marginRight: 12},
  dayName: {fontSize: 15, fontWeight: '600', color: '#0f172a', flex: 1},

  weekRange: {fontSize: 13, color: '#94a3b8', marginBottom: 8, marginTop: 2},
  weekHint: {fontSize: 12, color: '#94a3b8', textAlign: 'center', marginTop: 12},
  weekDayRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#f1f5f9',
    gap: 10,
  },
  weekDayLeft: {width: 88},
  weekDayName: {fontSize: 14, fontWeight: '700', color: '#0f172a'},
  weekDayNameToday: {color: '#7c3aed'},
  weekDayDate: {fontSize: 11, color: '#94a3b8', marginTop: 2},
  weekDayBarTrack: {
    flex: 1,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#f1f5f9',
    overflow: 'hidden',
  },
  weekDayBarFill: {height: 8, borderRadius: 4, backgroundColor: '#a78bfa'},
  weekDayChevron: {fontSize: 18, color: '#cbd5e1', fontWeight: '600', width: 14},
});
