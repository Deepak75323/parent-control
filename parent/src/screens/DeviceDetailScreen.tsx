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
  Alert,
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
} from '../utils/screenShareCapability';

type Props = NativeStackScreenProps<RootStackParamList, 'DeviceDetail'>;

const {width: SW} = Dimensions.get('window');
const MAP_W = SW - Layout.screenPadding * 2 - Layout.gutter * 2;
const CONTENT_W = Math.min(SW, Layout.maxContentWidth);

function isOnline(s?: string) {
  return (s ?? '').toUpperCase() === 'ONLINE';
}
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
  const [setupAgainVisible, setSetupAgainVisible] = useState(false);
  const [setupGenerating, setSetupGenerating] = useState(false);
  const [activePairingCode, setActivePairingCode] = useState<string | null>(null);
  const [pairingExpiresAt, setPairingExpiresAt] = useState<string | null>(null);
  const [pendingRequests, setPendingRequests] = useState<TimeExtensionRequestItem[]>([]);

  const live = useRealtimeStore(s => s.deviceStatuses[deviceId]);
  const liveLocation = useRealtimeStore(s => s.liveLocations[deviceId]);
  const liveAlerts = useRealtimeStore(s => s.liveAlerts);
  const deviceListVersion = useRealtimeStore(s => s.deviceListVersion);
  const setPendingPairingCode = useRealtimeStore(s => s.setPendingPairingCode);

  const heroAnim = useFadeSlideIn(0);
  const dotBlink = useOpacityPulse(0.5, 1, 700);

  const loadDevice = useCallback(async () => {
    try {
      const r = await deviceApi.get(deviceId);
      setDevice(r.data.data);
    } catch {
      Alert.alert('Error', 'Could not load device');
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

  const sendCommand = async (cmd: 'ENABLE_KIOSK' | 'DISABLE_KIOSK' | 'REVOKE_MONITORING', label: string) => {
    setCommandLoading(true);
    try {
      await deviceApi.sendCommand(deviceId, cmd);
      Alert.alert('Sent', `${label} command sent`);
      await loadDevice();
    } catch {
      Alert.alert('Error', 'Command failed');
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
        Alert.alert(
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
      Alert.alert('Pairing code ready', 'Share your family child login password and this pairing code with your child.');
    } catch {
      Alert.alert('Error', 'Could not create pairing code');
    } finally {
      setSetupGenerating(false);
    }
  };

  if (loading) return <LoadingCenter />;

  const status = live?.status ?? device?.status;
  const battery = live?.batteryLevel ?? device?.batteryLevel;
  const network = live?.networkType ?? device?.networkType;
  const lastSeen = live?.lastSeenAt ?? device?.lastSeenAt;
  const foregroundApp = live?.foregroundApp;
  const online = isOnline(status);
  const revoked = isRevoked(status);
  const locLat = liveLocation?.latitude ?? live?.latitude;
  const locLon = liveLocation?.longitude ?? live?.longitude;
  const hasLoc = locLat != null && locLon != null;
  const deviceName = device?.deviceName ?? 'Child Device';
  const initials = deviceName.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
  const projectionDevice = mergeScreenShareCapability(live, device);
  const screenShareCapable = isScreenShareCapableMerged(live, device);
  const screenShareBlockedReason = screenShareCapable
    ? undefined
    : screenShareBlockedMessage(projectionDevice);
  const showProjectionAlert =
    device?.deviceOwnerActive === true && !screenShareCapable && online;

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
    device?.permAccessibility === false && 'Accessibility',
    device?.permUsageStats === false && 'Usage Access',
    device?.permOverlay === false && 'Overlay',
  ]
    .filter(Boolean)
    .join(', ');

  const openRemoteView = () => {
    navigation.navigate('LiveMonitoring', {deviceId});
  };

  const handleRing = () => {
    Alert.alert(
      'Ring device',
      'This will play an alert sound on your child\'s device when the feature is available on their app version.',
    );
  };

  const handleLock = () => {
    if (device?.kioskModeActive) {
      sendCommand('DISABLE_KIOSK', 'Disable kiosk');
      return;
    }
    Alert.alert('Lock device', 'Enable kiosk mode to lock all apps?', [
      {text: 'Cancel', style: 'cancel'},
      {text: 'Lock', onPress: () => sendCommand('ENABLE_KIOSK', 'Enable kiosk')},
    ]);
  };

  const foregroundLabel = foregroundApp
    ? foregroundApp.split('.').pop() ?? 'Home Screen'
    : 'Home Screen';

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
                  Child steps: open Device Monitor → enter parent password → complete permissions → enter this code.
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
                  <View>
                    <Text style={sc.heroName}>{deviceName}</Text>
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
                    <Text style={sc.heroLastSeen}>Last seen {new Date(lastSeen).toLocaleTimeString()}</Text>
                  ) : null}
                </View>
              </View>

              <View style={sc.heroActions}>
                <Pressable style={sc.heroActionBtn} onPress={handleRing}>
                  <Text style={sc.heroActionIcon}>🔔</Text>
                  <Text style={sc.heroActionLabel}>Ring</Text>
                </Pressable>
                <Pressable style={sc.heroActionBtn} onPress={handleLock} disabled={commandLoading}>
                  <Text style={sc.heroActionIcon}>🔒</Text>
                  <Text style={sc.heroActionLabel}>Lock</Text>
                </Pressable>
              </View>
            </Animated.View>

            {showProjectionAlert ? (
              <PermissionsAlert
                title="Screen capture needs setup"
                subtitle="The child phone lost screen capture permission. Open Permissions on the child app to restore it."
                onFix={() => Alert.alert('Screen capture', screenShareBlockedReason ?? 'Screen capture is unavailable on the child phone.')}
              />
            ) : null}

            {missingEnforcementPerms ? (
              <PermissionsAlert
                title={`Protections missing: ${missingEnforcementLabel}`}
                subtitle="App blocking and usage limits won't work until the child grants these permissions."
                onFix={() => Alert.alert('Missing permissions', `The child device is missing: ${missingEnforcementLabel}.`)}
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

            {/* Remote view — screen, camera, audio, and recording live in one session */}
            <View style={sc.section}>
              <SectionTitle title="Remote View" />
              <Pressable
                style={[sc.liveScreenCard, !screenShareCapable && {opacity: 0.55}]}
                onPress={openRemoteView}>
                <View style={sc.liveScreenGradient} />
                <View style={sc.liveScreenContent}>
                  <View style={sc.liveScreenTop}>
                    <View style={sc.livePill}>
                      <View style={sc.livePillDot} />
                      <Text style={sc.livePillText}>Live</Text>
                    </View>
                    <Text style={sc.liveScreenIcon}>📺</Text>
                  </View>
                  <View>
                    <Text style={sc.liveScreenEyebrow}>Screen · camera · audio</Text>
                    <Text style={sc.liveScreenTitle}>{foregroundLabel}</Text>
                    {!screenShareCapable && screenShareBlockedReason ? (
                      <Text style={sc.liveScreenBlocked}>{screenShareBlockedReason}</Text>
                    ) : (
                      <Text style={sc.liveScreenSub}>Switch source and record from one view</Text>
                    )}
                  </View>
                </View>
              </Pressable>
            </View>

            {/* Activity */}
            <View style={sc.section}>
              <ActivityCard deviceId={deviceId} onPress={() => navigation.navigate('ScreenTime', {deviceId})} />
            </View>

            {/* Block All Apps */}
            <View style={sc.section}>
              <SurfaceCard style={sc.blockCard}>
                <View style={sc.blockLeft}>
                  <IconCircle icon="⊘" bg={`${Colors.error}18`} color={Colors.error} />
                  <Text style={sc.blockTitle}>Block All Apps</Text>
                </View>
                <Switch
                  value={blockAll || device?.kioskModeActive === true}
                  onValueChange={val => {
                    setBlockAll(val);
                    if (val) {
                      Alert.alert('Block All Apps', 'Enable kiosk mode to block all apps?', [
                        {text: 'Cancel', style: 'cancel', onPress: () => setBlockAll(false)},
                        {text: 'Block', onPress: () => sendCommand('ENABLE_KIOSK', 'Enable kiosk')},
                      ]);
                    } else {
                      sendCommand('DISABLE_KIOSK', 'Disable kiosk');
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
              <SurfaceCard style={sc.locationCard}>
                <Text style={[sc.sectionTitle, sc.locationTitle]}>Live Location</Text>
                {hasLoc ? (
                  <Pressable onPress={() => navigation.navigate('Location', {deviceId})}>
                    <View style={sc.mapWrap}>
                      <SafeMapView
                        style={{width: MAP_W, height: 180}}
                        region={{latitude: locLat!, longitude: locLon!, latitudeDelta: 0.01, longitudeDelta: 0.01}}
                        scrollEnabled={false}
                        zoomEnabled={false}
                        pitchEnabled={false}
                        rotateEnabled={false}
                        pointerEvents="none"
                        fallbackLabel="Live location"
                      />
                    </View>
                  </Pressable>
                ) : (
                  <View style={sc.noLocWrap}>
                    <View style={sc.noLocOverlay}>
                      <Text style={sc.noLocIcon}>📍</Text>
                      <Text style={sc.noLocTitle}>Location unavailable</Text>
                      <Text style={sc.noLocSub}>GPS is currently disabled on the child&apos;s device.</Text>
                      <Pressable style={sc.noLocBtn} onPress={() => navigation.navigate('Location', {deviceId})}>
                        <Text style={sc.noLocBtnText}>Enable Location</Text>
                      </Pressable>
                    </View>
                  </View>
                )}
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
                Revoking monitoring will immediately stop all data tracking and release device controls.
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
        body="This stops all monitoring and clears consent on the child device."
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
  heroTop: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', zIndex: 1},
  heroIdentity: {flexDirection: 'row', alignItems: 'center', gap: Layout.componentInternal},
  heroAvatar: {
    width: 64,
    height: 64,
    borderRadius: 32,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.3)',
    backgroundColor: Colors.surfaceContainer,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroAvatarText: {fontSize: 22, fontWeight: '700', color: Colors.primaryContainer},
  heroName: {...Typography.largeTitle, color: '#fff'},
  heroStatusRow: {flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginTop: Spacing.half},
  onlineDot: {width: Spacing.sm, height: Spacing.sm, borderRadius: Radii.xs, backgroundColor: Colors.tertiaryFixed},
  heroStatusText: {...Typography.smallLabel, color: 'rgba(255,255,255,0.8)'},
  heroMeta: {alignItems: 'flex-end', gap: Spacing.half},
  heroMetaIcons: {flexDirection: 'row', alignItems: 'center', gap: 6},
  heroMetaIcon: {fontSize: 14, color: 'rgba(255,255,255,0.9)'},
  heroMetaValue: {...Typography.smallLabel, color: 'rgba(255,255,255,0.9)'},
  heroLastSeen: {...Typography.smallLabel, color: 'rgba(255,255,255,0.6)'},
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
  liveScreenCard: {
    height: 192,
    borderRadius: Radii.xl,
    overflow: 'hidden',
    marginBottom: Layout.componentInternal,
    ...Elevation.card,
  },
  liveScreenGradient: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: Colors.primaryContainer,
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
  livePillDot: {width: Spacing.sm, height: Spacing.sm, borderRadius: Radii.xs, backgroundColor: Colors.error},
  livePillText: {...Typography.smallLabel, color: '#fff'},
  liveScreenIcon: {fontSize: 28},
  liveScreenEyebrow: {...Typography.smallLabel, color: 'rgba(255,255,255,0.7)'},
  liveScreenTitle: {...Typography.cardTitle, color: '#fff'},
  liveScreenSub: {...Typography.caption, color: 'rgba(255,255,255,0.75)', marginTop: 4},
  liveScreenBlocked: {...Typography.caption, color: '#FCA5A5', marginTop: 4, lineHeight: 18},
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
  blockCard: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Layout.componentInternal},
  blockLeft: {flexDirection: 'row', alignItems: 'center', gap: Spacing.md},
  blockTitle: {...Typography.cardTitle, fontSize: 16},
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
  locationCard: {padding: 0, overflow: 'hidden'},
  locationTitle: {padding: Layout.gutter, paddingBottom: Layout.componentInternal},
  mapWrap: {borderRadius: Radii.lg, overflow: 'hidden', marginHorizontal: Layout.gutter, marginBottom: Layout.gutter},
  noLocWrap: {aspectRatio: 16 / 9, backgroundColor: Colors.surfaceContainer, marginHorizontal: Layout.gutter, marginBottom: Layout.gutter, borderRadius: Radii.lg, alignItems: 'center', justifyContent: 'center'},
  noLocOverlay: {alignItems: 'center', padding: Layout.gutter, backgroundColor: 'rgba(255,255,255,0.92)', borderRadius: Radii.xl, borderWidth: 1, borderColor: Colors.surface, margin: Layout.componentInternal, ...Elevation.cardHigh},
  noLocIcon: {fontSize: 28, color: Colors.outline, marginBottom: Spacing.sm},
  noLocTitle: {...Typography.cardTitle, fontSize: 16, marginBottom: Spacing.half},
  noLocSub: {...Typography.smallLabel, color: Colors.textSecondary, textAlign: 'center', marginBottom: Layout.componentInternal},
  noLocBtn: {backgroundColor: Colors.primaryContainer, borderRadius: Radii.md, minHeight: Layout.touchTargetMin, paddingVertical: Spacing.md, paddingHorizontal: Layout.gutter, width: '100%', alignItems: 'center', justifyContent: 'center'},
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
