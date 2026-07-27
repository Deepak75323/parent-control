import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  Animated,
  Dimensions,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  View,
  Modal,
  ActivityIndicator,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {SafeMapView} from '../components/SafeMapView';
import {
  deviceApi,
  DeviceSummary,
  monitoringApi,
  UsageStatEntry,
  childLoginPasswordApi,
  timeExtensionApi,
  TimeExtensionRequestItem,
  policiesApi,
  BLOCK_ALL_APPS_POLICY_NAME,
} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {ConfirmModal, LoadingCenter} from '../components/AppScreen';
import {Colors, Typography, Spacing, Radii, Elevation, Layout} from '../theme/design';
import {useFadeSlideIn, usePressScale, useOpacityPulse} from '../utils/animations';
import {formatUsageMs} from '../utils/formatUsage';
import {useFocusEffect} from '@react-navigation/native';
import {
  isScreenShareCapableMerged,
  mergeScreenShareCapability,
  screenShareBlockedMessage,
  screenShareBlockedShortMessage,
} from '../utils/screenShareCapability';
import {getApiErrorMessage} from '../utils/apiError';
import {isDevicePresentOnline} from '../utils/devicePresence';
import {usePresenceClock} from '../hooks/usePresenceClock';
import {track} from '../services/telemetry';
import {TelemetryEvents} from '../services/telemetryEvents';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'DeviceDetail'>;

const {width: SW} = Dimensions.get('window');
const MAP_W = SW - Layout.screenPadding * 2 - Layout.gutter * 2;
const CONTENT_W = Math.min(SW, Layout.maxContentWidth);

function isRevoked(s?: string) {
  return (s ?? '').toUpperCase() === 'REVOKED';
}

// ─── Reusable UI primitives ──────────────────────────────────────────────────

function SurfaceCard({children, style}: {children: React.ReactNode; style?: object}) {
  return <View style={[sc.surfaceCard, style]}>{children}</View>;
}

function SectionTitle({title, badge}: {title: string; badge?: string}) {
  return (
    <View style={sc.sectionTitleRow}>
      <Text style={sc.sectionTitle}>{title}</Text>
      {badge ? (
        <View style={sc.newBadge}>
          <Text style={sc.newBadgeText}>{badge}</Text>
        </View>
      ) : null}
    </View>
  );
}

function IconCircle({icon, bg, color}: {icon: string; bg: string; color: string}) {
  return (
    <View style={[sc.iconCircle, {backgroundColor: bg}]}>
      <Text style={[sc.iconCircleText, {color}]}>{icon}</Text>
    </View>
  );
}

function MenuRow({icon, title, subtitle, onPress, last}: {
  icon: string; title: string; subtitle: string; onPress: () => void; last?: boolean;
}) {
  const press = usePressScale(0.98);
  return (
    <Animated.View style={{transform: press.scale}}>
      <Pressable
        style={[sc.menuRow, last && sc.menuRowLast]}
        onPress={onPress}
        onPressIn={press.onPressIn}
        onPressOut={press.onPressOut}>
        <Text style={sc.menuRowIcon}>{icon}</Text>
        <View style={sc.menuRowBody}>
          <Text style={sc.menuRowTitle}>{title}</Text>
          <Text style={sc.menuRowSub}>{subtitle}</Text>
        </View>
        <Text style={sc.menuRowChevron}>›</Text>
      </Pressable>
    </Animated.View>
  );
}

function HourlyBarChart({bars}: {bars: number[]}) {
  const max = Math.max(...bars, 1);
  return (
    <View style={sc.barChart}>
      {bars.map((h, i) => {
        const pct = h / max;
        const isPeak = h === max && h > 0;
        return (
          <View key={i} style={sc.barTrack}>
            <View
              style={[
                sc.barFill,
                {
                  height: `${Math.max(pct * 100, 8)}%`,
                  backgroundColor: isPeak ? Colors.primaryContainer : `${Colors.primaryContainer}33`,
                  opacity: isPeak ? 1 : 0.4 + pct * 0.4,
                },
              ]}
            />
          </View>
        );
      })}
    </View>
  );
}

function ActivityCard({deviceId, onPress}: {deviceId: string; onPress: () => void}) {
  const [total, setTotal] = useState('—');
  const [hourlyBars, setHourlyBars] = useState<number[]>([0, 0, 0, 0, 0, 0, 0]);

  useEffect(() => {
    monitoringApi.usageStats(deviceId, 0, 200).then(res => {
      const entries = res.data.data.content;
      if (!entries.length) return;
      const totalMs = entries.reduce((s: number, e: UsageStatEntry) => s + e.usageMs, 0);
      setTotal(formatUsageMs(totalMs));

      const hours = new Array(7).fill(0);
      const now = new Date();
      entries.forEach((e: UsageStatEntry) => {
        const d = new Date(e.recordedAt);
        if (d.toDateString() !== now.toDateString()) return;
        const h = d.getHours();
        const bucket = Math.min(Math.floor(h / 24 * 7), 6);
        hours[bucket] += e.usageMs;
      });
      setHourlyBars(hours);
    }).catch(() => {});
  }, [deviceId]);

  return (
    <Pressable onPress={onPress}>
      <SurfaceCard>
        <View style={sc.activityHeader}>
          <Text style={sc.sectionTitle}>Today&apos;s Activity</Text>
          <Text style={sc.activityLink}>View full report</Text>
        </View>
        <View style={sc.activityBody}>
          <View>
            <Text style={sc.activityLabel}>Screen Time</Text>
            <Text style={sc.activityTotal}>{total}</Text>
          </View>
          <HourlyBarChart bars={hourlyBars} />
        </View>
      </SurfaceCard>
    </Pressable>
  );
}

function ControlGridTile({icon, iconBg, iconColor, label, sub, status, onPress}: {
  icon: string; iconBg: string; iconColor: string; label: string; sub: string; status: string; onPress: () => void;
}) {
  const press = usePressScale(0.97);
  return (
    <Animated.View style={[sc.gridTileWrap, {transform: press.scale}]}>
      <Pressable style={sc.gridTile} onPress={onPress} onPressIn={press.onPressIn} onPressOut={press.onPressOut}>
        <IconCircle icon={icon} bg={iconBg} color={iconColor} />
        <Text style={sc.gridTileLabel}>{label}</Text>
        <Text style={sc.gridTileSub}>{sub}</Text>
        <View style={sc.gridTileFoot}>
          <Text style={sc.gridTileStatus}>{status}</Text>
          <Text style={sc.gridTileArrow}>→</Text>
        </View>
      </Pressable>
    </Animated.View>
  );
}

function PermissionsAlert({title, subtitle, onFix}: {title: string; subtitle: string; onFix: () => void}) {
  const anim = useFadeSlideIn(60);
  return (
    <Animated.View style={[sc.permAlert, anim]}>
      <View style={sc.permAlertIcon}><Text style={{fontSize: 18}}>⚠</Text></View>
      <View style={sc.permAlertBody}>
        <Text style={sc.permAlertTitle}>{title}</Text>
        <Text style={sc.permAlertSub}>{subtitle}</Text>
      </View>
      <Pressable style={sc.permAlertBtn} onPress={onFix}>
        <Text style={sc.permAlertBtnText}>Info</Text>
      </Pressable>
    </Animated.View>
  );
}

// ─── Main Screen ────────────────────────────────────────────────────────────

export function DeviceDetailScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [device, setDevice] = useState<DeviceSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [commandLoading, setCommandLoading] = useState(false);
  const [revokeVisible, setRevokeVisible] = useState(false);
  const [blockAll, setBlockAll] = useState(false);
  const [blockAllPolicyId, setBlockAllPolicyId] = useState<string | null>(null);
  const [setupAgainVisible, setSetupAgainVisible] = useState(false);
  const [setupGenerating, setSetupGenerating] = useState(false);
  const [activePairingCode, setActivePairingCode] = useState<string | null>(null);
  const [pairingExpiresAt, setPairingExpiresAt] = useState<string | null>(null);
  const [pendingRequests, setPendingRequests] = useState<TimeExtensionRequestItem[]>([]);
  const [ringing, setRinging] = useState(false);
  const ringTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const live = useRealtimeStore(s => s.deviceStatuses[deviceId]);
  const liveLocation = useRealtimeStore(s => s.liveLocations[deviceId]);
  const liveAlerts = useRealtimeStore(s => s.liveAlerts);
  const deviceListVersion = useRealtimeStore(s => s.deviceListVersion);
  const setPendingPairingCode = useRealtimeStore(s => s.setPendingPairingCode);
  const presenceNowMs = usePresenceClock(15_000);

  const heroAnim = useFadeSlideIn(0);
  const dotBlink = useOpacityPulse(0.5, 1, 700);

  const loadDevice = useCallback(async () => {
    try {
      const [r, bundleRes] = await Promise.all([
        deviceApi.get(deviceId),
        policiesApi.deviceBundle(deviceId).catch(() => null),
      ]);
      setDevice(r.data.data);
      const blockAllRule = bundleRes?.data?.data?.appBlocks?.find(
        rule =>
          rule.packageName === '*' &&
          rule.enabled !== false &&
          rule.scheduleJson?.blockAllApps === true,
      );
      setBlockAll(Boolean(blockAllRule) || r.data.data.kioskModeActive === true);
      setBlockAllPolicyId(blockAllRule?.policyId ?? null);
    } catch {
      appAlert('Error', 'Couldn’t load this device. Check your connection and try again.');
    } finally {
      setLoading(false);
    }
  }, [deviceId]);

  const loadRequests = useCallback(async () => {
    try {
      const r = await timeExtensionApi.listForDevice(deviceId);
      setPendingRequests((r.data.data ?? []).filter(req => req.status === 'PENDING'));
    } catch {
      setPendingRequests([]);
    }
  }, [deviceId]);

  useEffect(() => {
    void loadDevice();
    void loadRequests();
  }, [loadDevice, loadRequests, deviceListVersion]);

  useFocusEffect(
    useCallback(() => {
      void loadDevice();
      void loadRequests();
    }, [loadDevice, loadRequests]),
  );

  const sendCommand = async (
    cmd: 'ENABLE_KIOSK' | 'DISABLE_KIOSK' | 'REVOKE_MONITORING' | 'RING' | 'STOP_RING',
    label: string,
  ): Promise<boolean> => {
    setCommandLoading(true);
    try {
      await deviceApi.sendCommand(deviceId, cmd);
      if (cmd === 'REVOKE_MONITORING') {
        track(TelemetryEvents.DEVICE_REVOKE);
      }
      appAlert('Sent', `${label} command sent`);
      await loadDevice();
      return true;
    } catch (err) {
      appAlert(
        'Error',
        getApiErrorMessage(err, 'Couldn’t send that command. Please try again.'),
      );
      return false;
    } finally {
      setCommandLoading(false);
    }
  };

  const setBlockAllApps = async (enabled: boolean) => {
    setCommandLoading(true);
    try {
      if (enabled) {
        // Re-check server — stale policyId after a failed delete must not no-op.
        const bundleRes = await policiesApi.deviceBundle(deviceId).catch(() => null);
        const existing = bundleRes?.data?.data?.appBlocks?.find(
          rule =>
            rule.packageName === '*' &&
            rule.enabled !== false &&
            rule.scheduleJson?.blockAllApps === true,
        );
        if (existing?.policyId) {
          setBlockAllPolicyId(existing.policyId);
          setBlockAll(true);
          appAlert(
            'Block All Apps on',
            'Apps on the child phone are already blocked via Accessibility.',
          );
          return;
        }
        const created = await policiesApi.createAppBlock({
          name: BLOCK_ALL_APPS_POLICY_NAME,
          deviceIds: [deviceId],
          rules: [
            {
              packageName: '*',
              scheduleType: 'ALWAYS',
              scheduleJson: {blockAllApps: true},
            },
          ],
        });
        setBlockAllPolicyId(created.data.data.id);
        setBlockAll(true);
        appAlert(
          'Block All Apps on',
          'Apps on the child phone are blocked via Accessibility (Phone, Messages, and the home screen stay available). Open Clock Time and keep Accessibility on.',
        );
      } else {
        // Delete by known id and by name — covers stale/missing policyId in UI state.
        const ids = new Set<string>();
        if (blockAllPolicyId) {
          ids.add(blockAllPolicyId);
        }
        const list = await policiesApi.list('APP_BLOCK').catch(() => null);
        for (const p of list?.data?.data ?? []) {
          if (
            p.name === BLOCK_ALL_APPS_POLICY_NAME &&
            (p.deviceIds?.includes(deviceId) ?? true)
          ) {
            ids.add(p.id);
          }
        }
        const bundleRes = await policiesApi.deviceBundle(deviceId).catch(() => null);
        for (const rule of bundleRes?.data?.data?.appBlocks ?? []) {
          if (
            rule.packageName === '*' &&
            rule.scheduleJson?.blockAllApps === true &&
            rule.policyId
          ) {
            ids.add(rule.policyId);
          }
        }
        const results = await Promise.allSettled(
          [...ids].map(id => policiesApi.delete(id)),
        );
        const deleted = results.some(r => r.status === 'fulfilled');
        const hardFail =
          ids.size > 0 && results.every(r => r.status === 'rejected');
        if (hardFail) {
          const first = results.find(r => r.status === 'rejected') as
            | PromiseRejectedResult
            | undefined;
          throw first?.reason ?? new Error('Could not delete Block All Apps policy');
        }
        setBlockAllPolicyId(null);
        setBlockAll(false);
        if (device?.kioskModeActive && device.deviceOwnerActive) {
          await deviceApi.sendCommand(deviceId, 'DISABLE_KIOSK');
        }
        appAlert(
          'Block All Apps off',
          deleted
            ? 'Rule removed. If apps stay blocked for a few seconds, open Clock Time on the child so policies sync.'
            : 'No Block All rule found — apps should already be allowed (except per-app rules).',
        );
      }
      await loadDevice();
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Couldn’t update Block All Apps. Please try again.');
      // Re-sync from server — optimistic toggle must not stick after a failed API call.
      await loadDevice().catch(() => undefined);
      // Double-tap / already-created policy returns 409 "Resource already exists".
      if (
        enabled &&
        /already exists|conflict/i.test(msg)
      ) {
        setBlockAll(true);
        appAlert(
          'Block All Apps on',
          'Block All Apps is already active on this device.',
        );
        return;
      }
      appAlert('Error', msg);
    } finally {
      setCommandLoading(false);
    }
  };

  const generateSetupPairingCode = async () => {
    setSetupGenerating(true);
    try {
      const statusResp = await childLoginPasswordApi.status();
      if (!statusResp.data.data?.configured) {
        setSetupAgainVisible(false);
        appAlert(
          'Set family password first',
          'Go to Settings → Child login password, then generate a pairing code.',
          [
            {text: 'Cancel', style: 'cancel'},
            {text: 'Open settings', onPress: () => navigation.navigate('ChildLoginPassword')},
          ],
        );
        return;
      }
      const {data: devicesBefore} = await deviceApi.list();
      const baseline = devicesBefore.data ?? [];
      const {data} = await deviceApi.createPairingCode();
      const code = data.data?.code?.trim();
      if (!code) throw new Error('Server returned an empty pairing code');
      setActivePairingCode(code);
      setPairingExpiresAt(data.data.expiresAt ?? null);
      setPendingPairingCode(code, baseline, data.data.expiresAt ?? null);
      setSetupAgainVisible(false);
      appAlert('Pairing code ready', 'Share your family child login password and this pairing code with your child.');
    } catch {
      appAlert(
        'Error',
        'Couldn’t create a pairing code. Check your connection and try again.',
      );
    } finally {
      setSetupGenerating(false);
    }
  };

  const clearRingTimer = useCallback(() => {
    if (ringTimeoutRef.current) {
      clearTimeout(ringTimeoutRef.current);
      ringTimeoutRef.current = null;
    }
  }, []);

  useEffect(() => () => clearRingTimer(), [clearRingTimer]);

  const markRinging = useCallback(() => {
    setRinging(true);
    clearRingTimer();
    ringTimeoutRef.current = setTimeout(() => setRinging(false), 30_000);
  }, [clearRingTimer]);

  if (loading) return <LoadingCenter />;

  const status = live?.status ?? device?.status;
  const battery = live?.batteryLevel ?? device?.batteryLevel;
  const network = live?.networkType ?? device?.networkType;
  const lastSeen = live?.lastSeenAt ?? device?.lastSeenAt;
  const online = isDevicePresentOnline(status, lastSeen, presenceNowMs);
  const revoked = isRevoked(status);
  const locLat = liveLocation?.latitude ?? live?.latitude;
  const locLon = liveLocation?.longitude ?? live?.longitude;
  const hasLoc = locLat != null && locLon != null;
  const deviceName = device?.deviceName ?? 'Child Device';
  const initials = deviceName.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
  const accessibilityEnabled = device?.accessibilityEnabled !== false;
  const promptsManualCast = !accessibilityEnabled;
  const projectionDevice = mergeScreenShareCapability(live, device);
  const screenShareCapable = isScreenShareCapableMerged(live, device);
  const screenShareBlockedReason = screenShareCapable
    ? undefined
    : screenShareBlockedMessage(projectionDevice);
  const screenShareBlockedShort = screenShareCapable
    ? undefined
    : screenShareBlockedShortMessage(projectionDevice);
  const showProjectionAlert =
    device?.deviceOwnerActive === true && !screenShareCapable && online;
  const locationEmptyCopy = (() => {
    if (!online) {
      return {
        title: 'Waiting for location',
        sub: 'The child phone is offline. A map appears after it comes online and shares a GPS fix.',
        cta: 'Open location',
      };
    }
    if (device?.permLocation === false) {
      return {
        title: 'Location permission needed',
        sub: 'Grant Location to the child app in Android Settings, keep Location services on, then reopen the child app.',
        cta: 'How to fix',
      };
    }
    return {
      title: 'No recent location yet',
      sub: 'App location permission can be on while no GPS fix has been shared yet. Keep Location services enabled and wait outdoors or reopen the child app.',
      cta: 'View location',
    };
  })();

  const deviceAlerts = liveAlerts.filter(a => a.deviceId === deviceId);
  const alertCount = deviceAlerts.length + pendingRequests.length;
  const topAlert = pendingRequests[0]
    ? {title: 'Time extension request', message: `${deviceName} wants more screen time`}
    : deviceAlerts[0]
      ? {title: deviceAlerts[0].title, message: deviceAlerts[0].message ?? ''}
      : null;

  const missingEnforcementPerms =
    device != null &&
    (device.permAccessibility === false ||
      device.permUsageStats === false ||
      device.permOverlay === false);
  const missingEnforcementLabel = [
    device?.permAccessibility === false && 'Required device feature',
    device?.permUsageStats === false && 'App usage access',
    device?.permOverlay === false && 'Display over other apps',
  ]
    .filter(Boolean)
    .join(', ');

  const openRemoteView = (initialStream?: 'SCREEN' | 'CAMERA' | 'AUDIO') => {
    // Always open Live Monitoring for SCREEN — child will prompt Cast / auto-approve.
    // Capability flags are status hints only (see Live View tiles), not start gates.
    navigation.navigate('LiveMonitoring', {
      deviceId,
      initialStream: initialStream ?? 'SCREEN',
    });
  };

  const handleStopRing = () => {
    void (async () => {
      const ok = await sendCommand('STOP_RING', 'Stop ring');
      if (ok) {
        clearRingTimer();
        setRinging(false);
      }
    })();
  };

  const handleRing = () => {
    if (ringing) {
      appAlert('Stop ring?', 'Silence the alarm on the child phone now?', [
        {text: 'Cancel', style: 'cancel'},
        {text: 'Stop', style: 'destructive', onPress: handleStopRing},
      ]);
      return;
    }
    appAlert(
      'Ring device',
      'Play a loud alarm on this phone for about 30 seconds so you can find it?',
      [
        {text: 'Cancel', style: 'cancel'},
        {text: 'Stop ring', onPress: handleStopRing},
        {
          text: 'Ring',
          onPress: () => {
            void (async () => {
              const ok = await sendCommand('RING', 'Ring');
              if (ok) {
                markRinging();
              }
            })();
          },
        },
      ],
    );
  };

  const handleCamera = () => {
    openRemoteView('CAMERA');
  };

  return (
    <SafeAreaView style={sc.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="dark-content" backgroundColor={Colors.surface} />

      {/* Sticky app bar */}
      <View style={sc.appBar}>
        <Pressable style={sc.appBarBtn} onPress={() => navigation.goBack()}>
          <Text style={sc.appBarBack}>‹</Text>
        </Pressable>
        <Text style={sc.appBarTitle} numberOfLines={1}>{deviceName}</Text>
        <Pressable style={sc.appBarBtn} onPress={() => navigation.navigate('Settings')}>
          <Text style={sc.appBarSettings}>⚙</Text>
        </Pressable>
      </View>

      <ScrollView
        contentContainerStyle={[sc.scroll, {maxWidth: CONTENT_W, alignSelf: 'center', width: '100%'}]}
        showsVerticalScrollIndicator={false}>

        {revoked ? (
          <>
            <View style={sc.revokedBanner}>
              <Text style={sc.revokedBannerTitle}>Monitoring revoked</Text>
              <Text style={sc.revokedBannerBody}>
                Monitoring has stopped on this device. Generate a new pairing code so your child can set up again.
              </Text>
            </View>
            {activePairingCode ? (
              <SurfaceCard style={sc.revokedCard}>
                <Text style={sc.pairingLabel}>Active pairing code</Text>
                <Text style={sc.pairingCode}>{activePairingCode}</Text>
                <Text style={sc.pairingHint}>
                  Child steps: open Clock Time → enter parent password → complete permissions → enter this code.
                  {pairingExpiresAt ? ` Expires ${new Date(pairingExpiresAt).toLocaleTimeString()}.` : ' Expires in 15 minutes.'}
                </Text>
              </SurfaceCard>
            ) : (
              <Pressable style={sc.setupAgainBtn} onPress={() => setSetupAgainVisible(true)}>
                <Text style={sc.setupAgainTitle}>Set up again</Text>
                <Text style={sc.setupAgainSub}>Generate pairing code and child password</Text>
              </Pressable>
            )}
            <View style={sc.section}>
              <SectionTitle title="Device Details" />
              <SurfaceCard>
                {[
                  {icon: '📶', label: 'Network', value: network ?? '—'},
                  {icon: '🛡', label: 'Protection', value: device?.protectionLevel ?? 'Standard'},
                  ...(lastSeen ? [{icon: '🕐', label: 'Last Check-in', value: new Date(lastSeen).toLocaleString()}] : []),
                ].map((row, i, arr) => (
                  <View key={row.label} style={[sc.detailRow, i === arr.length - 1 && sc.detailRowLast]}>
                    <Text style={sc.detailIcon}>{row.icon}</Text>
                    <Text style={sc.detailLabel}>{row.label}</Text>
                    <Text style={sc.detailValue}>{row.value}</Text>
                  </View>
                ))}
              </SurfaceCard>
            </View>
          </>
        ) : (
          <>
            {/* Premium hero card */}
            <Animated.View style={[sc.heroCard, heroAnim]}>
              <View style={sc.heroGradientBase} />
              <View style={sc.heroGradientOverlay} />
              <View style={sc.heroDeco} />

              <View style={sc.heroTop}>
                <View style={sc.heroIdentity}>
                  <View style={sc.heroAvatar}>
                    <Text style={sc.heroAvatarText}>{initials}</Text>
                  </View>
                  <View style={sc.heroIdentityText}>
                    <Text style={sc.heroName} numberOfLines={1}>
                      {deviceName}
                    </Text>
                    <View style={sc.heroStatusRow}>
                      <Animated.View style={[sc.onlineDot, online && {opacity: dotBlink}]} />
                      <Text style={sc.heroStatusText}>{online ? 'Online' : 'Offline'}</Text>
                    </View>
                  </View>
                </View>
                <View style={sc.heroMeta}>
                  <View style={sc.heroMetaIcons}>
                    {network ? <Text style={sc.heroMetaIcon}>📶</Text> : null}
                    {battery != null ? <Text style={sc.heroMetaIcon}>🔋</Text> : null}
                    {battery != null ? <Text style={sc.heroMetaValue}>{battery}%</Text> : null}
                  </View>
                  {lastSeen ? (
                    <Text style={sc.heroLastSeen} numberOfLines={1}>
                      Last seen {new Date(lastSeen).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})}
                    </Text>
                  ) : null}
                </View>
              </View>

              <View style={sc.heroActions}>
                <Pressable
                  style={sc.heroActionBtn}
                  onPress={handleRing}
                  disabled={commandLoading}>
                  <Text style={sc.heroActionIcon}>{ringing ? '⏹' : '🔔'}</Text>
                  <Text style={sc.heroActionLabel}>{ringing ? 'Stop' : 'Ring'}</Text>
                </Pressable>
                <Pressable style={sc.heroActionBtn} onPress={handleCamera}>
                  <Text style={sc.heroActionIcon}>📷</Text>
                  <Text style={sc.heroActionLabel}>Camera</Text>
                </Pressable>
              </View>
            </Animated.View>

            {showProjectionAlert ? (
              <PermissionsAlert
                title="Screen sharing isn’t set up yet"
                subtitle="You can still tap Screen — the child phone may ask to allow screen sharing so live view can start."
                onFix={() =>
                  appAlert(
                    'Screen sharing',
                    'Tap Screen under Live View to start. On the child phone, allow screen sharing if asked.',
                  )
                }
              />
            ) : null}

            {missingEnforcementPerms ? (
              <PermissionsAlert
                title={`Permissions needed: ${missingEnforcementLabel}`}
                subtitle="App blocking and usage limits won’t work until these are turned on in Clock Time on the child phone."
                onFix={() =>
                  appAlert(
                    'Missing permissions',
                    `Ask your child to open Clock Time and finish: ${missingEnforcementLabel}.`,
                  )
                }
              />
            ) : null}

            {/* Alerts */}
            {topAlert ? (
              <View style={sc.section}>
                <SectionTitle title="Alerts" badge={alertCount > 0 ? `${alertCount} NEW` : undefined} />
                <Pressable onPress={() => navigation.navigate('Alerts')}>
                  <SurfaceCard style={sc.alertCard}>
                    <View style={sc.alertIconWrap}>
                      <Text style={sc.alertIcon}>📲</Text>
                    </View>
                    <View style={sc.alertBody}>
                      <Text style={sc.alertTitle}>{topAlert.title}</Text>
                      <Text style={sc.alertMessage}>{topAlert.message}</Text>
                    </View>
                    <Text style={sc.alertChevron}>›</Text>
                  </SurfaceCard>
                </Pressable>
              </View>
            ) : null}

            {/* Live view — only Screen needs cast; camera/audio stay available */}
            <View style={sc.section}>
              <Text style={sc.sectionTitle}>Live View</Text>
              <View style={sc.liveTilesRow}>
                <Pressable
                  style={[sc.liveTile, sc.liveTileScreen]}
                  onPress={() => openRemoteView('SCREEN')}>
                  <Text style={sc.liveTileIcon}>📺</Text>
                  <Text style={sc.liveTileLabel}>Screen</Text>
                  <Text style={sc.liveTileSub}>
                    {screenShareCapable
                      ? 'Remote view'
                      : promptsManualCast
                        ? 'May ask on child phone'
                        : 'Remote view'}
                  </Text>
                </Pressable>

                <Pressable
                  style={[sc.liveTile, sc.liveTileCamera]}
                  onPress={() => openRemoteView('CAMERA')}>
                  <Text style={sc.liveTileIcon}>📷</Text>
                  <Text style={sc.liveTileLabelOnDark}>Camera</Text>
                  <Text style={sc.liveTileSubOnDark}>Remote view</Text>
                </Pressable>

                <Pressable
                  style={[sc.liveTile, sc.liveTileAudio]}
                  onPress={() => openRemoteView('AUDIO')}>

                  <Text style={sc.liveTileIcon}>🎧</Text>
                  <Text style={sc.liveTileLabelOnDark}>Audio</Text>
                  <Text style={sc.liveTileSubOnDark}>One-way listen</Text>
                </Pressable>
              </View>
              {!screenShareCapable && promptsManualCast ? (
                <Text style={sc.liveViewHint}>
                  Open Clock Time on the child phone and finish the remaining permission steps for smoother screen sharing.
                </Text>
              ) : null}
            </View>

            {/* Activity */}
            <View style={sc.section}>
              <ActivityCard deviceId={deviceId} onPress={() => navigation.navigate('ScreenTime', {deviceId})} />
            </View>

            {/* Block All Apps — Accessibility lockdown (FlashGet-style), not Device Owner kiosk */}
            <View style={sc.section}>
              <SurfaceCard style={sc.blockCard}>
                <View style={sc.blockLeft}>
                  <IconCircle icon="⊘" bg={`${Colors.error}18`} color={Colors.error} />
                  <View style={sc.blockTextCol}>
                    <Text style={sc.blockTitle}>Block All Apps</Text>
                    <Text style={sc.blockHint}>
                      {device?.accessibilityEnabled === false
                        ? 'Turn on Accessibility in Clock Time on the child phone first.'
                        : 'Blocks apps via Accessibility. Phone, messages, and home stay available. Use App Rules for specific apps.'}
                    </Text>
                  </View>
                </View>
                <Switch
                  value={blockAll}
                  onValueChange={val => {
                    if (val && device?.accessibilityEnabled === false) {
                      appAlert(
                        'Accessibility required',
                        'Open Clock Time on the child phone → Permissions → turn on Accessibility, then try again.',
                      );
                      return;
                    }
                    if (val) {
                      appAlert(
                        'Block All Apps',
                        'Block every app on the child phone except Phone, Messages, and the home screen?',
                        [
                          {text: 'Cancel', style: 'cancel'},
                          {
                            text: 'Block',
                            onPress: () => {
                              setBlockAll(true);
                              void setBlockAllApps(true);
                            },
                          },
                        ],
                      );
                    } else {
                      appAlert(
                        'Allow apps again?',
                        'Turn off Block All Apps so the child can open apps again (per-app rules still apply).',
                        [
                          {text: 'Cancel', style: 'cancel'},
                          {
                            text: 'Allow apps',
                            onPress: () => {
                              setBlockAll(false);
                              void setBlockAllApps(false);
                            },
                          },
                        ],
                      );
                    }
                  }}
                  trackColor={{false: Colors.surfaceContainer, true: Colors.primaryContainer}}
                  thumbColor="#fff"
                  disabled={commandLoading}
                />
              </SurfaceCard>
            </View>

            {/* Control grid */}
            <View style={sc.section}>
              <View style={sc.controlGrid}>
                <ControlGridTile
                  icon="⏱"
                  iconBg="#DBEAFE"
                  iconColor="#2563EB"
                  label="Screen Limits"
                  sub="Daily usage caps"
                  status="Active"
                  onPress={() => navigation.navigate('ScreenTime', {deviceId})}
                />
                <ControlGridTile
                  icon="▦"
                  iconBg="#EDE9FE"
                  iconColor="#7C3AED"
                  label="App Limits"
                  sub="Specific app timers"
                  status="Manage"
                  onPress={() => navigation.navigate('AppTimeLimits', {deviceId})}
                />
                <ControlGridTile
                  icon="⚖"
                  iconBg="#FFEDD5"
                  iconColor="#EA580C"
                  label="App Rules"
                  sub="Schedule & groups"
                  status="Custom"
                  onPress={() => navigation.navigate('AppRules', {deviceId})}
                />
                <ControlGridTile
                  icon="📋"
                  iconBg="#CCFBF1"
                  iconColor="#0D9488"
                  label="Policies"
                  sub="Global device rules"
                  status={device?.protectionLevel ?? 'Strict'}
                  onPress={() => navigation.navigate('Policies', {deviceId})}
                />
              </View>
            </View>

            {/* Live Location */}
            <View style={sc.section}>
              <SectionTitle title="Live Location" />
              <SurfaceCard style={sc.locationCard}>
                {hasLoc ? (
                  <View style={sc.mapWrap} collapsable={false}>
                    <SafeMapView
                      style={{width: MAP_W, height: 180}}
                      region={{
                        latitude: locLat!,
                        longitude: locLon!,
                        latitudeDelta: 0.01,
                        longitudeDelta: 0.01,
                      }}
                      zoomEnabled
                      scrollEnabled
                      zoomControlEnabled
                      fallbackLabel="Live location"
                    />
                    <Pressable
                      style={sc.mapOpenBtn}
                      onPress={() => navigation.navigate('Location', {deviceId})}>
                      <Text style={sc.mapOpenBtnText}>Open location history</Text>
                    </Pressable>
                  </View>
                ) : (
                  <View style={sc.noLocWrap}>
                    <Text style={sc.noLocIcon}>📍</Text>
                    <Text style={sc.noLocTitle}>{locationEmptyCopy.title}</Text>
                    <Text style={sc.noLocSub}>{locationEmptyCopy.sub}</Text>
                    <Pressable
                      style={sc.noLocBtn}
                      onPress={() => navigation.navigate('Location', {deviceId})}>
                      <Text style={sc.noLocBtnText}>{locationEmptyCopy.cta}</Text>
                    </Pressable>
                  </View>
                )}
              </SurfaceCard>
            </View>

            {/* Child phone notifications */}
            <View style={sc.section}>
              <SectionTitle title="Messages & alerts" />
              <SurfaceCard>
                <MenuRow
                  icon="🔔"
                  title="Child notifications"
                  subtitle="WhatsApp, SMS, and other app alerts mirrored from this phone"
                  onPress={() => navigation.navigate('Notifications', {deviceId})}
                  last
                />
              </SurfaceCard>
            </View>

            {/* Live Location */}
            <View style={sc.section}>
              <SectionTitle title="Device Details" />
              <SurfaceCard>
                {[
                  {icon: '📶', label: 'Network', value: network ?? '—'},
                  {icon: '🛡', label: 'Protection', value: device?.protectionLevel ?? 'Standard'},
                  {icon: '▣', label: 'Kiosk Mode', value: device?.kioskModeActive ? 'On' : 'Off'},
                  ...(lastSeen ? [{icon: '🕐', label: 'Last Check-in', value: new Date(lastSeen).toLocaleString()}] : []),
                ].map((row, i, arr) => (
                  <View key={row.label} style={[sc.detailRow, i === arr.length - 1 && sc.detailRowLast]}>
                    <Text style={sc.detailIcon}>{row.icon}</Text>
                    <Text style={sc.detailLabel}>{row.label}</Text>
                    <Text style={sc.detailValue}>{row.value}</Text>
                  </View>
                ))}
              </SurfaceCard>
            </View>

            {/* Revoke */}
            <View style={sc.section}>
              <Pressable style={sc.revokeBtn} onPress={() => setRevokeVisible(true)} disabled={commandLoading}>
                <Text style={sc.revokeIcon}>⚠</Text>
                <Text style={sc.revokeText}>Revoke Monitoring</Text>
              </Pressable>
              <Text style={sc.revokeHint}>
                Stops monitoring on this phone and frees its plan seat so you can pair another child
                device. Re-pair this same phone later with a new pairing code if needed.
              </Text>
            </View>
          </>
        )}

        <View style={{height: Layout.sectionGap}} />
      </ScrollView>

      <Modal visible={setupAgainVisible} transparent animationType="fade">
        <View style={sc.modalBackdrop}>
          <View style={sc.modalCard}>
            <Text style={sc.modalTitle}>Set up again</Text>
            <Text style={sc.modalBody}>
              Generate a new pairing code. Your child uses your family child login password first, then this code.
            </Text>
            <Pressable
              style={[sc.modalPrimary, setupGenerating && {opacity: 0.7}]}
              onPress={() => void generateSetupPairingCode()}
              disabled={setupGenerating}>
              {setupGenerating ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={sc.modalPrimaryText}>Generate pairing code</Text>
              )}
            </Pressable>
            <Pressable onPress={() => navigation.navigate('ChildLoginPassword')}>
              <Text style={sc.modalLink}>Change family password</Text>
            </Pressable>
            <Pressable onPress={() => setSetupAgainVisible(false)}>
              <Text style={sc.modalCancel}>Cancel</Text>
            </Pressable>
          </View>
        </View>
      </Modal>

      <ConfirmModal
        visible={revokeVisible}
        title="Revoke monitoring"
        body="This stops all monitoring, clears consent on the child device, and frees this plan seat for another child phone."
        confirmLabel="Revoke"
        danger
        loading={commandLoading}
        onCancel={() => setRevokeVisible(false)}
        onConfirm={async () => {
          setRevokeVisible(false);
          await sendCommand('REVOKE_MONITORING', 'Revoke monitoring');
        }}
      />
    </SafeAreaView>
  );
}

const sc = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.background},

  // App bar — 64px, 24px horizontal padding
  appBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Layout.screenPadding,
    height: Layout.appBarHeight,
    backgroundColor: Colors.surface,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: Colors.border,
  },
  appBarBtn: {width: Layout.touchTargetMin, height: Layout.touchTargetMin, alignItems: 'center', justifyContent: 'center'},
  appBarBack: {fontSize: 28, color: Colors.primaryContainer, fontWeight: '600', lineHeight: 32},
  appBarTitle: {...Typography.sectionTitle, flex: 1, textAlign: 'center', marginHorizontal: Spacing.sm},
  appBarSettings: {fontSize: 20, color: Colors.textSecondary},

  // 24px screen padding, 40px section rhythm
  scroll: {
    paddingHorizontal: Layout.screenPadding,
    paddingTop: Layout.gutter,
    gap: Layout.sectionGap,
  },

  // Cards — 24px radius, 24px padding, 1px border
  surfaceCard: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    borderWidth: 1,
    borderColor: Colors.border,
    padding: Layout.gutter,
    ...Elevation.card,
  },

  // Section — 16px between title and content (space-y-4)
  section: {gap: Layout.componentInternal},
  sectionTitleRow: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  sectionTitle: {...Typography.sectionTitle},
  newBadge: {backgroundColor: Colors.error, borderRadius: Radii.pill, paddingHorizontal: Spacing.sm, paddingVertical: 2},
  newBadgeText: {color: '#fff', fontSize: 10, fontWeight: '700'},

  // Hero — 256px, 24px padding, 12px action buttons
  heroCard: {
    height: Layout.heroHeight,
    borderRadius: Radii.xl,
    padding: Layout.gutter,
    overflow: 'hidden',
    justifyContent: 'space-between',
    ...Elevation.hero,
  },
  heroGradientBase: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: Colors.primaryContainer,
  },
  heroGradientOverlay: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: '40%',
    backgroundColor: Colors.accent,
    opacity: 0.85,
  },
  heroDeco: {
    position: 'absolute',
    width: 192,
    height: 192,
    borderRadius: 96,
    backgroundColor: 'rgba(255,255,255,0.1)',
    top: -48,
    right: -48,
  },
  heroTop: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', zIndex: 1, gap: Spacing.md},
  heroIdentity: {flexDirection: 'row', alignItems: 'center', gap: Layout.componentInternal, flex: 1, minWidth: 0},
  heroIdentityText: {flex: 1, minWidth: 0},
  heroAvatar: {
    width: 64,
    height: 64,
    borderRadius: 32,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.3)',
    backgroundColor: Colors.surfaceContainer,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  heroAvatarText: {fontSize: 22, fontWeight: '700', color: Colors.primaryContainer},
  heroName: {...Typography.largeTitle, color: '#fff'},
  heroStatusRow: {flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginTop: Spacing.half},
  onlineDot: {width: Spacing.sm, height: Spacing.sm, borderRadius: Radii.xs, backgroundColor: Colors.tertiaryFixed},
  heroStatusText: {...Typography.smallLabel, color: 'rgba(255,255,255,0.8)'},
  heroMeta: {alignItems: 'flex-end', gap: Spacing.half, flexShrink: 0, maxWidth: '42%'},
  heroMetaIcons: {flexDirection: 'row', alignItems: 'center', gap: 6},
  heroMetaIcon: {fontSize: 14, color: 'rgba(255,255,255,0.9)'},
  heroMetaValue: {...Typography.smallLabel, color: 'rgba(255,255,255,0.9)'},
  heroLastSeen: {...Typography.smallLabel, color: 'rgba(255,255,255,0.6)', textAlign: 'right'},
  heroActions: {flexDirection: 'row', gap: Spacing.md, zIndex: 1},
  heroActionBtn: {
    flex: 1,
    minHeight: Layout.touchTargetMin,
    backgroundColor: 'rgba(255,255,255,0.2)',
    borderRadius: Radii.md,
    paddingVertical: Spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
  },
  heroActionIcon: {fontSize: 18},
  heroActionLabel: {...Typography.cardTitle, color: '#fff'},

  // Permission alert — 16px internal
  permAlert: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.warningLight,
    borderRadius: Radii.md,
    padding: Layout.componentInternal,
    borderLeftWidth: 3,
    borderLeftColor: Colors.warning,
  },
  permAlertIcon: {width: 36, height: 36, borderRadius: Radii.sm, backgroundColor: '#FDE68A', alignItems: 'center', justifyContent: 'center', marginRight: Spacing.sm},
  permAlertBody: {flex: 1},
  permAlertTitle: {fontSize: 13, fontWeight: '700', color: Colors.warning},
  permAlertSub: {fontSize: 12, color: '#92400E', marginTop: 1},
  permAlertBtn: {backgroundColor: Colors.warning, borderRadius: Radii.sm, paddingHorizontal: Spacing.md, paddingVertical: 6},
  permAlertBtnText: {fontSize: 12, fontWeight: '800', color: '#fff'},

  // Alert card — 16px internal padding
  alertCard: {flexDirection: 'row', alignItems: 'center', gap: Layout.componentInternal, padding: Layout.componentInternal},
  alertIconWrap: {backgroundColor: `${Colors.primaryContainer}1A`, padding: Spacing.md, borderRadius: Radii.pill},
  alertIcon: {fontSize: 20},
  alertBody: {flex: 1},
  alertTitle: {...Typography.cardTitle, fontSize: 16},
  alertMessage: {...Typography.body},
  alertChevron: {fontSize: 22, color: Colors.outline},

  // Live monitoring — 16px grid gap
  liveTilesRow: {flexDirection: 'row', gap: Spacing.md},
  liveTile: {
    flex: 1,
    minHeight: 112,
    borderRadius: Radii.xl,
    padding: Spacing.md,
    justifyContent: 'flex-end',
    overflow: 'hidden',
    ...Elevation.card,
  },
  liveTileScreen: {backgroundColor: '#EDE9FE'},
  liveTileCamera: {backgroundColor: Colors.primaryContainer},
  liveTileAudio: {backgroundColor: '#2563EB'},
  liveTileDisabled: {opacity: 0.72},
  liveTileBadge: {
    position: 'absolute',
    top: Spacing.sm,
    right: Spacing.sm,
    backgroundColor: 'rgba(255,255,255,0.22)',
    borderRadius: Radii.pill,
    paddingHorizontal: Spacing.sm,
    paddingVertical: 2,
  },
  liveTileBadgeText: {...Typography.smallLabel, color: '#fff', fontSize: 10, fontWeight: '800'},
  liveTileIcon: {fontSize: 22, marginBottom: Spacing.half},
  liveTileLabel: {...Typography.cardTitle, fontSize: 15, color: Colors.textPrimary},
  liveTileSub: {...Typography.smallLabel, color: Colors.textSecondary, marginTop: 2},
  liveTileLabelMuted: {color: Colors.textTertiary},
  liveTileLabelOnDark: {...Typography.cardTitle, fontSize: 15, color: '#fff'},
  liveTileSubOnDark: {...Typography.smallLabel, color: 'rgba(255,255,255,0.8)', marginTop: 2},
  liveViewHint: {
    ...Typography.smallLabel,
    color: Colors.textSecondary,
    marginTop: Spacing.md,
    lineHeight: 18,
  },
  liveScreenCard: {
    height: 192,
    borderRadius: Radii.xl,
    overflow: 'hidden',
    marginBottom: Layout.componentInternal,
    ...Elevation.card,
  },
  liveScreenCardMuted: {
    borderWidth: 1,
    borderColor: Colors.border,
  },
  liveScreenGradient: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: Colors.primaryContainer,
  },
  liveScreenGradientMuted: {
    backgroundColor: '#475569',
  },
  liveScreenContent: {
    flex: 1,
    padding: Layout.gutter,
    justifyContent: 'space-between',
    backgroundColor: 'rgba(0,0,0,0.08)',
  },
  liveScreenTop: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start'},
  livePill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: 'rgba(255,255,255,0.2)',
    borderRadius: Radii.pill,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.half,
  },
  setupPill: {
    backgroundColor: 'rgba(251, 191, 36, 0.25)',
  },
  livePillDot: {width: Spacing.sm, height: Spacing.sm, borderRadius: Radii.xs, backgroundColor: Colors.error},
  livePillText: {...Typography.smallLabel, color: '#fff'},
  liveScreenIcon: {fontSize: 28},
  liveScreenEyebrow: {...Typography.smallLabel, color: 'rgba(255,255,255,0.7)'},
  liveScreenTitle: {...Typography.cardTitle, color: '#fff'},
  liveScreenSub: {...Typography.caption, color: 'rgba(255,255,255,0.75)', marginTop: 4},
  liveScreenBlocked: {...Typography.caption, color: '#FDE68A', marginTop: 6, lineHeight: 18},
  monitorRow: {flexDirection: 'row', gap: Layout.componentInternal},
  monitorTile: {
    flex: 1,
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    borderWidth: 1,
    borderColor: Colors.border,
    padding: Layout.gutter,
    gap: Spacing.md,
    ...Elevation.card,
  },
  monitorTileTop: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'},
  monitorTileIcon: {fontSize: 24, color: Colors.primaryContainer},
  statusChipReady: {backgroundColor: `${Colors.primaryContainer}1A`, borderRadius: Radii.pill, paddingHorizontal: Spacing.sm, paddingVertical: 2},
  statusChipListen: {backgroundColor: `${Colors.tertiary}1A`, borderRadius: Radii.pill, paddingHorizontal: Spacing.sm, paddingVertical: 2},
  statusChipText: {fontSize: 10, fontWeight: '700', color: Colors.primary},
  monitorTileLabel: {...Typography.cardTitle, fontSize: 16},
  monitorTileSub: {...Typography.smallLabel, color: Colors.textTertiary},

  // Activity
  activityHeader: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Layout.gutter},
  activityLink: {...Typography.smallLabel, color: Colors.primaryContainer},
  activityBody: {flexDirection: 'row', alignItems: 'flex-end', gap: Layout.gutter},
  activityLabel: {...Typography.smallLabel, color: Colors.textSecondary, marginBottom: Spacing.half},
  activityTotal: {...Typography.number},
  barChart: {flex: 1, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', height: 96, paddingBottom: Spacing.sm},
  barTrack: {width: 12, height: '100%', backgroundColor: Colors.surfaceContainer, borderRadius: Radii.xs, justifyContent: 'flex-end', overflow: 'hidden'},
  barFill: {width: '100%', borderRadius: Radii.xs, minHeight: 4},

  // Block all — 16px internal
  blockCard: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.md, padding: Layout.componentInternal},
  blockLeft: {flexDirection: 'row', alignItems: 'center', gap: Spacing.md, flex: 1, minWidth: 0},
  blockTextCol: {flex: 1, minWidth: 0, gap: 2},
  blockTitle: {...Typography.cardTitle, fontSize: 16},
  blockHint: {...Typography.smallLabel, color: Colors.textSecondary},
  iconCircle: {width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center'},
  iconCircleText: {fontSize: 18, fontWeight: '700'},

  // Control grid — 16px gap, 24px tile padding
  controlGrid: {flexDirection: 'row', flexWrap: 'wrap', gap: Layout.componentInternal},
  gridTileWrap: {width: (SW - Layout.screenPadding * 2 - Layout.componentInternal) / 2},
  gridTile: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    borderWidth: 1,
    borderColor: Colors.border,
    padding: Layout.gutter,
    ...Elevation.card,
  },
  gridTileLabel: {...Typography.cardTitle, fontSize: 16, marginTop: Layout.componentInternal},
  gridTileSub: {...Typography.smallLabel, color: Colors.textTertiary, marginTop: Spacing.half},
  gridTileFoot: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: Layout.componentInternal},
  gridTileStatus: {...Typography.smallLabel, color: Colors.primaryContainer},
  gridTileArrow: {fontSize: 14, color: Colors.primaryContainer},

  // Location
  locationCard: {padding: Layout.gutter, overflow: 'hidden'},
  mapWrap: {borderRadius: Radii.lg, overflow: 'hidden'},
  mapOpenBtn: {
    marginTop: Spacing.sm,
    backgroundColor: Colors.primaryContainer,
    borderRadius: Radii.md,
    minHeight: Layout.touchTargetMin,
    paddingVertical: Spacing.md,
    paddingHorizontal: Layout.gutter,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mapOpenBtnText: {...Typography.cardTitle, color: '#fff', fontSize: 14},
  noLocWrap: {
    minHeight: 168,
    backgroundColor: Colors.surfaceContainer,
    borderRadius: Radii.lg,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Layout.gutter,
    paddingVertical: Layout.componentInternal,
  },
  noLocIcon: {fontSize: 28, marginBottom: Spacing.sm},
  noLocTitle: {...Typography.cardTitle, fontSize: 16, marginBottom: Spacing.half, textAlign: 'center'},
  noLocSub: {
    ...Typography.smallLabel,
    color: Colors.textSecondary,
    textAlign: 'center',
    marginBottom: Layout.componentInternal,
    lineHeight: 18,
  },
  noLocBtn: {
    backgroundColor: Colors.primaryContainer,
    borderRadius: Radii.md,
    minHeight: Layout.touchTargetMin,
    paddingVertical: Spacing.md,
    paddingHorizontal: Layout.gutter,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'stretch',
  },
  noLocBtnText: {...Typography.cardTitle, color: '#fff', fontSize: 14},

  // Menu rows — 72px height, 24px horizontal inset
  menuCard: {paddingVertical: 0, paddingHorizontal: Layout.gutter},
  menuRow: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: Layout.listRowHeight,
    paddingVertical: Layout.componentInternal,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: Colors.borderLight,
  },
  menuRowLast: {borderBottomWidth: 0},
  menuRowIcon: {fontSize: 22, color: Colors.textSecondary, width: 32, marginRight: Layout.componentInternal},
  menuRowBody: {flex: 1},
  menuRowTitle: {...Typography.cardTitle, fontSize: 15},
  menuRowSub: {...Typography.smallLabel, color: Colors.textTertiary},
  menuRowChevron: {fontSize: 22, color: Colors.outline},

  // Device details
  detailRow: {flexDirection: 'row', alignItems: 'center', paddingVertical: Layout.componentInternal, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: Colors.borderLight},
  detailRowLast: {borderBottomWidth: 0},
  detailIcon: {fontSize: 18, width: 28, color: Colors.outline},
  detailLabel: {flex: 1, ...Typography.body, fontWeight: '500', color: Colors.textPrimary, marginLeft: Spacing.sm},
  detailValue: {...Typography.body, fontWeight: '600'},

  // Revoke — 48px touch target
  revokeBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
    borderWidth: 2,
    borderColor: `${Colors.error}33`,
    backgroundColor: `${Colors.error}0D`,
    borderRadius: Radii.xl,
    minHeight: Layout.touchTargetMin,
    paddingVertical: Layout.componentInternal,
  },
  revokeIcon: {fontSize: 18, color: Colors.error},
  revokeText: {...Typography.cardTitle, color: Colors.error},
  revokeHint: {...Typography.smallLabel, color: Colors.textTertiary, textAlign: 'center', marginTop: Layout.componentInternal, paddingHorizontal: Layout.gutter},

  // Revoked state
  revokedBanner: {backgroundColor: Colors.dangerLight, borderRadius: Radii.md, padding: Layout.componentInternal, borderWidth: 1, borderColor: '#FECACA'},
  revokedBannerTitle: {fontSize: 16, fontWeight: '700', color: '#B91C1C', marginBottom: Spacing.half},
  revokedBannerBody: {fontSize: 13, color: '#7F1D1D', lineHeight: 18},
  revokedCard: {marginTop: Layout.componentInternal},
  setupAgainBtn: {backgroundColor: Colors.primaryContainer, borderRadius: Radii.xl, padding: Layout.componentInternal, alignItems: 'center', minHeight: Layout.touchTargetMin, justifyContent: 'center', ...Elevation.card},
  setupAgainTitle: {fontSize: 16, fontWeight: '700', color: '#fff'},
  setupAgainSub: {fontSize: 12, color: 'rgba(255,255,255,0.85)', marginTop: Spacing.half},
  pairingLabel: {...Typography.smallLabel, color: Colors.textTertiary, marginBottom: Spacing.sm},
  pairingCode: {fontSize: 28, fontWeight: '800', letterSpacing: 4, color: Colors.primary, textAlign: 'center', marginBottom: Spacing.sm},
  pairingHint: {fontSize: 12, color: Colors.textSecondary, lineHeight: 17},

  // Modal — 24px padding, 24px radius
  modalBackdrop: {flex: 1, backgroundColor: 'rgba(15, 23, 42, 0.55)', justifyContent: 'center', padding: Layout.gutter},
  modalCard: {backgroundColor: Colors.surface, borderRadius: Radii.xl, padding: Layout.gutter},
  modalTitle: {...Typography.cardTitle, marginBottom: Spacing.sm},
  modalBody: {...Typography.body, marginBottom: Layout.componentInternal},
  modalPrimary: {backgroundColor: Colors.primaryContainer, borderRadius: Radii.md, minHeight: Layout.touchTargetMin, paddingVertical: Spacing.md, alignItems: 'center', justifyContent: 'center', marginTop: Spacing.half},
  modalPrimaryText: {color: '#fff', fontWeight: '700', fontSize: 15},
  modalCancel: {textAlign: 'center', color: Colors.textTertiary, fontWeight: '600', marginTop: Spacing.md},
  modalLink: {textAlign: 'center', color: Colors.info, fontWeight: '600', marginTop: Spacing.md},
});
