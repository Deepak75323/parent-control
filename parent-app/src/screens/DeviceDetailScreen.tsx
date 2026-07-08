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
import {deviceApi, DeviceSummary, monitoringApi, UsageStatEntry, childLoginPasswordApi} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {ConfirmModal, LoadingCenter} from '../components/AppScreen';
import {Colors, Typography, Spacing, Radii, Elevation} from '../theme/design';
import {useFadeSlideIn, usePressScale, usePulse, useOpacityPulse} from '../utils/animations';
import {formatUsageMs} from '../utils/formatUsage';
import {useFocusEffect} from '@react-navigation/native';
import {
  isScreenShareCapableMerged,
  mergeScreenShareCapability,
  screenShareBlockedMessage,
} from '../utils/screenShareCapability';

type Props = NativeStackScreenProps<RootStackParamList, 'DeviceDetail'>;

const {width: SW} = Dimensions.get('window');
const MAP_W = SW - Spacing.base * 2 - Spacing.base * 2;

function isOnline(s?: string) { return (s ?? '').toUpperCase() === 'ONLINE'; }
function isRevoked(s?: string) { return (s ?? '').toUpperCase() === 'REVOKED'; }

// ─── Sub-components ────────────────────────────────────────────────────────

function SectionHeader({eyebrow, title, action, onAction}: {eyebrow: string; title: string; action?: string; onAction?: () => void}) {
  return (
    <View style={sc.sectionHeader}>
      <View>
        <Text style={sc.sectionEyebrow}>{eyebrow}</Text>
        <Text style={sc.sectionTitle}>{title}</Text>
      </View>
      {action ? (
        <Pressable onPress={onAction}>
          <Text style={sc.sectionAction}>{action}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

function MonitorTile({icon, label, desc, color, isLive, disabled, onPress}: {
  icon: string; label: string; desc: string; color: string; isLive: boolean; disabled?: boolean; onPress: () => void;
}) {
  const press = usePressScale(0.93);
  const anim = useFadeSlideIn(0);
  return (
    <Animated.View style={[{flex: 1}, {transform: press.scale}, anim, disabled && {opacity: 0.45}]}>
      <Pressable style={[sc.monTile, {backgroundColor: color}]} onPress={onPress} onPressIn={press.onPressIn} onPressOut={press.onPressOut} disabled={disabled}>
        <View style={sc.monTileTop}>
          <Text style={sc.monTileIcon}>{icon}</Text>
          {isLive && <View style={sc.livePill}><Text style={sc.livePillText}>LIVE</Text></View>}
        </View>
        <Text style={sc.monTileLabel}>{label}</Text>
        <Text style={sc.monTileDesc}>{desc}</Text>
      </Pressable>
    </Animated.View>
  );
}

function MiniBar({pct, color}: {pct: number; color: string}) {
  const w = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.timing(w, {toValue: pct, duration: 700, delay: 300, useNativeDriver: false}).start();
  }, [pct, w]);
  return (
    <View style={sc.miniBarTrack}>
      <Animated.View style={[sc.miniBarFill, {width: w.interpolate({inputRange: [0,1], outputRange: ['0%', '100%']}), backgroundColor: color}]} />
    </View>
  );
}

function UsagePreviewCard({deviceId, onPress}: {deviceId: string; onPress: () => void}) {
  const [rows, setRows] = useState<{label: string; pct: number; time: string}[]>([]);
  const [total, setTotal] = useState('—');

  useEffect(() => {
    monitoringApi.usageStats(deviceId, 0, 50).then(res => {
      const entries = res.data.data.content;
      if (!entries.length) return;
      const map = new Map<string, number>();
      entries.forEach((e: UsageStatEntry) => map.set(e.packageName, Math.max(map.get(e.packageName) ?? 0, e.usageMs)));
      const sorted = Array.from(map.entries()).sort((a, b) => b[1] - a[1]);
      const maxMs = sorted[0]?.[1] ?? 1;
      const totalMs = sorted.reduce((s, [, v]) => s + v, 0);
      setTotal(formatUsageMs(totalMs));
      setRows(sorted.slice(0, 3).map(([pkg, ms]) => ({
        label: pkg.split('.').pop() ?? pkg,
        pct: ms / maxMs,
        time: formatUsageMs(ms),
      })));
    }).catch(() => {});
  }, [deviceId]);

  const barColors = [Colors.primary, Colors.accent, Colors.info];

  return (
    <Pressable style={sc.usageCard} onPress={onPress}>
      <View style={sc.usageCardTop}>
        <View>
          <Text style={sc.sectionEyebrow}>TODAY'S ACTIVITY</Text>
          <Text style={sc.usageCardTitle}>Screen Time</Text>
        </View>
        <View style={sc.usageTotalWrap}>
          <Text style={sc.usageTotalNum}>{total}</Text>
          <Text style={sc.usageTotalLbl}>total</Text>
        </View>
      </View>
      {rows.length > 0 ? (
        <View style={sc.usageBars}>
          {rows.map((r, i) => (
            <View key={r.label} style={sc.usageBarRow}>
              <Text style={sc.usageBarLabel} numberOfLines={1}>{r.label}</Text>
              <MiniBar pct={r.pct} color={barColors[i] ?? Colors.primary} />
              <Text style={sc.usageBarTime}>{r.time}</Text>
            </View>
          ))}
        </View>
      ) : (
        <Text style={sc.usageEmpty}>No usage data yet · start using apps on the child device</Text>
      )}
      <View style={sc.usageCardFoot}>
        <Text style={sc.usageCardFootCta}>View full report  →</Text>
      </View>
    </Pressable>
  );
}

function PermissionsAlert({title, subtitle, onFix}: {title: string; subtitle: string; onFix: () => void}) {
  const anim = useFadeSlideIn(60);
  return (
    <Animated.View style={[sc.permAlert, anim]}>
      <View style={sc.permAlertIcon}><Text style={{fontSize: 18}}>⚠️</Text></View>
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

function ControlTile({icon, label, sub, color, onPress}: {icon: string; label: string; sub: string; color: string; onPress: () => void}) {
  const press = usePressScale(0.95);
  return (
    <Animated.View style={[sc.ctrlTileWrap, {transform: press.scale}]}>
      <Pressable style={sc.ctrlTile} onPress={onPress} onPressIn={press.onPressIn} onPressOut={press.onPressOut}>
        <View style={[sc.ctrlTileIcon, {backgroundColor: color + '18'}]}>
          <Text style={{fontSize: 22}}>{icon}</Text>
        </View>
        <Text style={sc.ctrlTileLabel}>{label}</Text>
        <Text style={sc.ctrlTileSub}>{sub}</Text>
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

  const live = useRealtimeStore(s => s.deviceStatuses[deviceId]);
  const liveLocation = useRealtimeStore(s => s.liveLocations[deviceId]);
  const deviceListVersion = useRealtimeStore(s => s.deviceListVersion);
  const setPendingPairingCode = useRealtimeStore(s => s.setPendingPairingCode);

  const headerAnim = useFadeSlideIn(0);
  const ringPulse = usePulse(0.88, 1.14, 1400);
  const dotBlink = useOpacityPulse(0.5, 1, 700);

  const loadDevice = useCallback(async () => {
    try { const r = await deviceApi.get(deviceId); setDevice(r.data.data); }
    catch { Alert.alert('Error', 'Could not load device'); }
    finally { setLoading(false); }
  }, [deviceId]);

  useEffect(() => { loadDevice(); }, [loadDevice, deviceListVersion]);

  useFocusEffect(
    useCallback(() => {
      void loadDevice();
    }, [loadDevice]),
  );

  const sendCommand = async (cmd: 'ENABLE_KIOSK' | 'DISABLE_KIOSK' | 'REVOKE_MONITORING', label: string) => {
    setCommandLoading(true);
    try { await deviceApi.sendCommand(deviceId, cmd); Alert.alert('Sent', `${label} command sent`); await loadDevice(); }
    catch { Alert.alert('Error', 'Command failed'); }
    finally { setCommandLoading(false); }
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
      if (!code) {
        throw new Error('Server returned an empty pairing code');
      }
      setActivePairingCode(code);
      setPairingExpiresAt(data.data.expiresAt ?? null);
      setPendingPairingCode(code, baseline, data.data.expiresAt ?? null);
      setSetupAgainVisible(false);
      Alert.alert(
        'Pairing code ready',
        'Share your family child login password and this pairing code with your child.',
      );
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

  const openScreenMonitoring = () => {
    if (!screenShareCapable) {
      Alert.alert('Screen capture unavailable', screenShareBlockedReason ?? 'Screen capture is unavailable.');
      return;
    }
    navigation.navigate('LiveMonitoring', {deviceId, initialStream: 'SCREEN'});
  };

  return (
    <SafeAreaView style={sc.root} edges={['top', 'bottom']}>
      <StatusBar barStyle="light-content" backgroundColor={Colors.primary} />

      {/* ── Hero Header ── */}
      <Animated.View style={[sc.hero, headerAnim]}>
        <View style={sc.heroBubble1} />
        <View style={sc.heroBubble2} />

        {/* Back */}
        <Pressable style={sc.backBtn} onPress={() => navigation.goBack()}>
          <Text style={sc.backIcon}>‹</Text>
        </Pressable>

        {/* Avatar + Info */}
        <View style={sc.heroBody}>
          <View style={sc.avatarWrap}>
            {online && <Animated.View style={[sc.avatarRing, {transform: ringPulse, opacity: 0.35}]} />}
            <View style={[sc.avatar, {backgroundColor: revoked ? '#DC2626' : online ? Colors.primary : Colors.offline}]}>
              <Text style={sc.avatarText}>{initials}</Text>
            </View>
            <View style={[sc.statusOrb, {backgroundColor: revoked ? '#DC2626' : online ? Colors.online : Colors.offline}]} />
          </View>

          <View style={sc.heroMeta}>
            <Text style={sc.heroDeviceName}>{deviceName}</Text>
            <View style={sc.heroStatusRow}>
              <Animated.View style={[sc.statusPill, {
                backgroundColor: revoked ? '#FEE2E2' : online ? '#DCFCE7' : '#F1F5F9',
              }, {opacity: revoked || !online ? 1 : dotBlink}]}>
                <View style={[sc.statusPillDot, {
                  backgroundColor: revoked ? '#DC2626' : online ? Colors.online : Colors.offline,
                }]} />
                <Text style={[sc.statusPillText, {
                  color: revoked ? '#DC2626' : online ? Colors.success : Colors.textTertiary,
                }]}>
                  {revoked ? 'Revoked' : online ? 'Online' : 'Offline'}
                </Text>
              </Animated.View>
              {battery != null ? (
                <View style={sc.batteryChip}>
                  <Text style={sc.batteryChipText}>🔋 {battery}%</Text>
                </View>
              ) : null}
              {network ? (
                <View style={sc.networkChip}>
                  <Text style={sc.networkChipText}>📶 {network}</Text>
                </View>
              ) : null}
            </View>
            {lastSeen ? <Text style={sc.lastSeenText}>Last seen {new Date(lastSeen).toLocaleTimeString()}</Text> : null}
          </View>
        </View>
      </Animated.View>

      <ScrollView contentContainerStyle={sc.scroll} showsVerticalScrollIndicator={false}>

        {revoked ? (
          <>
            <View style={sc.revokedBanner}>
              <Text style={sc.revokedBannerTitle}>Monitoring revoked</Text>
              <Text style={sc.revokedBannerBody}>
                Monitoring has stopped on this device. Generate a new pairing code so your child can set up again.
              </Text>
            </View>

            {activePairingCode ? (
              <View style={sc.pairingCard}>
                <Text style={sc.pairingLabel}>Active pairing code</Text>
                <Text style={sc.pairingCode}>{activePairingCode}</Text>
                <Text style={sc.pairingHint}>
                  Child steps: open Device Monitor → enter parent password → complete permissions → enter this code.
                  {pairingExpiresAt
                    ? ` Expires ${new Date(pairingExpiresAt).toLocaleTimeString()}.`
                    : ' Expires in 15 minutes.'}
                </Text>
              </View>
            ) : (
              <Pressable style={sc.setupAgainBtn} onPress={() => setSetupAgainVisible(true)}>
                <Text style={sc.setupAgainTitle}>Set up again</Text>
                <Text style={sc.setupAgainSub}>Generate pairing code and child password</Text>
              </Pressable>
            )}

            <View style={sc.section}>
              <SectionHeader eyebrow="DEVICE" title="Device Details" />
              <View style={sc.infoCard}>
                {[
                  {icon: '📶', label: 'Network', value: network ?? '—'},
                  {icon: '🛡️', label: 'Protection', value: device?.protectionLevel ?? 'Standard'},
                  ...(lastSeen ? [{icon: '🕐', label: 'Last Seen', value: new Date(lastSeen).toLocaleString()}] : []),
                ].map((row, i, arr) => (
                  <View key={row.label} style={[sc.infoRow, i === arr.length - 1 && {borderBottomWidth: 0}]}>
                    <Text style={{fontSize: 16, width: 26}}>{row.icon}</Text>
                    <Text style={sc.infoLabel}>{row.label}</Text>
                    <Text style={sc.infoValue}>{row.value}</Text>
                  </View>
                ))}
              </View>
            </View>

            <Pressable style={sc.passwordBtn} onPress={() => navigation.navigate('ChildLoginPassword')}>
              <Text style={sc.passwordText}>🔑  Family child login password</Text>
              <Text style={sc.passwordSub}>Same password on every child phone — change in Settings</Text>
            </Pressable>
          </>
        ) : (
          <>
        {showProjectionAlert ? (
          <PermissionsAlert
            title="Screen capture needs setup"
            subtitle="The child phone lost screen capture permission. Open Permissions on the child app to restore it."
            onFix={() =>
              Alert.alert(
                'Screen capture',
                screenShareBlockedReason ?? 'Screen capture is unavailable on the child phone.',
              )
            }
          />
        ) : null}

        {/* ── Live Monitoring ── */}
        <View style={sc.section}>
          <SectionHeader eyebrow="MONITORING" title="Live View" action="All options" onAction={() => navigation.navigate('LiveMonitoring', {deviceId})} />
          <View style={sc.monRow}>
            <MonitorTile
              icon="📺"
              label="Screen"
              desc={screenShareCapable ? 'Mirror device' : 'Unavailable'}
              color="#4F46E5"
              isLive={online && screenShareCapable}
              disabled={!screenShareCapable}
              onPress={openScreenMonitoring}
            />
            <MonitorTile icon="📷" label="Camera" desc="Remote view" color="#7C3AED" isLive={online} onPress={() => navigation.navigate('LiveMonitoring', {deviceId, initialStream: 'CAMERA'})} />
            <MonitorTile icon="🎧" label="Audio" desc="One-way listen" color="#2563EB" isLive={online} onPress={() => navigation.navigate('LiveMonitoring', {deviceId, initialStream: 'AUDIO'})} />
          </View>
        </View>

        {/* ── Usage Report ── */}
        <View style={sc.section}>
          <SectionHeader eyebrow="INSIGHTS" title="Activity Report" />
          <UsagePreviewCard deviceId={deviceId} onPress={() => navigation.navigate('ScreenTime', {deviceId})} />
        </View>

        {/* ── Block All Apps ── */}
        <View style={sc.section}>
          <SectionHeader eyebrow="CONTROLS" title="Device Controls" />
          <View style={sc.blockCard}>
            <View style={sc.blockCardLeft}>
              <View style={sc.blockIconWrap}>
                <Text style={{fontSize: 26}}>🔒</Text>
              </View>
              <View style={sc.blockTextWrap}>
                <Text style={sc.blockTitle}>Block All Apps</Text>
                <Text style={sc.blockSub}>
                  {blockAll || device?.kioskModeActive
                    ? '🔴 All apps are currently blocked'
                    : 'Only allowed apps will be accessible'}
                </Text>
              </View>
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
              trackColor={{false: Colors.border, true: Colors.primary}}
              thumbColor="#fff"
              disabled={commandLoading}
            />
          </View>
        </View>

        {/* ── Activity Grid ── */}
        <View style={sc.section}>
          <SectionHeader eyebrow="MANAGEMENT" title="App & Screen Time" />
          <View style={sc.ctrlGrid}>
            <ControlTile icon="⏱️" label="Screen Limits" sub="Set daily caps" color={Colors.primary} onPress={() => navigation.navigate('ScreenTime', {deviceId})} />
            <ControlTile icon="📲" label="App Limits" sub="Per-app timers" color={Colors.info} onPress={() => navigation.navigate('AppTimeLimits', {deviceId})} />
            <ControlTile icon="🛡️" label="App Rules" sub="Block/Allow apps" color={Colors.success} onPress={() => navigation.navigate('AppRules', {deviceId})} />
            <ControlTile icon="📋" label="Policies" sub="All policies" color={Colors.accent} onPress={() => navigation.navigate('Policies', {deviceId})} />
          </View>
        </View>

        {/* ── Live Location ── */}
        <View style={sc.section}>
          <SectionHeader eyebrow="SAFETY" title="Live Location" action="Full map" onAction={() => navigation.navigate('Location', {deviceId})} />
          {hasLoc ? (
            <View style={sc.mapWrap}>
              <SafeMapView
                style={{width: MAP_W, height: 170}}
                region={{latitude: locLat!, longitude: locLon!, latitudeDelta: 0.01, longitudeDelta: 0.01}}
                scrollEnabled={false}
                zoomEnabled={false}
                pitchEnabled={false}
                rotateEnabled={false}
                pointerEvents="none"
                fallbackLabel="Live location"
              />
              <View style={sc.mapOverlay}>
                <View style={sc.mapTag}>
                  <Text style={sc.mapTagText}>
                    {lastSeen ? `Updated ${new Date(lastSeen).toLocaleTimeString()}` : 'Live'}
                  </Text>
                </View>
                <Pressable style={sc.geofenceChip} onPress={() => navigation.navigate('Geofences', {deviceId})}>
                  <Text style={sc.geofenceChipText}>📍 Geofence</Text>
                </Pressable>
              </View>
            </View>
          ) : (
            <Pressable style={sc.noLocCard} onPress={() => navigation.navigate('Location', {deviceId})}>
              <Text style={{fontSize: 36}}>🗺️</Text>
              <View style={{marginLeft: 14}}>
                <Text style={sc.noLocTitle}>Location unavailable</Text>
                <Text style={sc.noLocSub}>Enable location on child device</Text>
              </View>
            </Pressable>
          )}
        </View>

        {/* ── Safety Grid ── */}
        <View style={sc.section}>
          <SectionHeader eyebrow="ALERTS" title="Safety & Alerts" />
          <View style={sc.ctrlGrid}>
            <ControlTile icon="📍" label="Geofences" sub="Safe zones" color={Colors.primary} onPress={() => navigation.navigate('Geofences', {deviceId})} />
            <ControlTile icon="📨" label="Requests" sub="Pending asks" color={Colors.warning} onPress={() => navigation.navigate('Requests')} />
            <ControlTile icon="🔔" label="Notifications" sub="App alerts" color={Colors.danger} onPress={() => navigation.navigate('Notifications', {deviceId})} />
            <ControlTile icon="📊" label="Usage Logs" sub="Activity log" color={Colors.info} onPress={() => navigation.navigate('ScreenTime', {deviceId})} />
          </View>
        </View>

        {/* ── Snapshot & Recording ── */}
        <View style={sc.section}>
          <SectionHeader eyebrow="CAPTURE" title="Snapshot & Recording" />
          <View style={sc.snapRow}>
            {[
              {icon: '🎥', label: 'Camera Recording'},
              {icon: '🖥️', label: 'Screen Recording'},
              {icon: '🎙️', label: 'Ambient Audio'},
            ].map(item => (
              <Pressable key={item.label} style={sc.snapTile} onPress={() => navigation.navigate('LiveMonitoring', {deviceId})}>
                <Text style={{fontSize: 28, marginBottom: 6}}>{item.icon}</Text>
                <Text style={sc.snapLabel}>{item.label}</Text>
              </Pressable>
            ))}
          </View>
        </View>

        {/* ── Device Info ── */}
        <View style={sc.section}>
          <SectionHeader eyebrow="DEVICE" title="Device Details" />
          <View style={sc.infoCard}>
            {[
              {icon: '📶', label: 'Network', value: network ?? '—'},
              {icon: '🛡️', label: 'Protection', value: device?.protectionLevel ?? 'Standard'},
              {icon: '🔑', label: 'Kiosk Mode', value: device?.kioskModeActive ? 'Active' : 'Off'},
              ...(lastSeen ? [{icon: '🕐', label: 'Last Seen', value: new Date(lastSeen).toLocaleString()}] : []),
            ].map((row, i, arr) => (
              <View key={row.label} style={[sc.infoRow, i === arr.length - 1 && {borderBottomWidth: 0}]}>
                <Text style={{fontSize: 16, width: 26}}>{row.icon}</Text>
                <Text style={sc.infoLabel}>{row.label}</Text>
                <Text style={sc.infoValue}>{row.value}</Text>
              </View>
            ))}
          </View>
        </View>

        <Pressable style={sc.passwordBtn} onPress={() => navigation.navigate('ChildLoginPassword')}>
          <Text style={sc.passwordText}>🔑  Family child login password</Text>
          <Text style={sc.passwordSub}>Same password on every child phone — change in Settings</Text>
        </Pressable>

        {/* ── Revoke ── */}
        <Pressable style={sc.revokeBtn} onPress={() => setRevokeVisible(true)} disabled={commandLoading}>
          <Text style={sc.revokeText}>⛔  Revoke Monitoring</Text>
          <Text style={sc.revokeSub}>Stops all monitoring on this device</Text>
        </Pressable>
          </>
        )}

        <View style={{height: 16}} />
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
        onConfirm={async () => { setRevokeVisible(false); await sendCommand('REVOKE_MONITORING', 'Revoke monitoring'); }}
      />
    </SafeAreaView>
  );
}

const sc = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.background},

  // Hero
  hero: {
    backgroundColor: Colors.primary,
    paddingTop: Spacing.sm,
    paddingBottom: Spacing.xl + Spacing.sm,
    paddingHorizontal: Spacing.base,
    overflow: 'visible',
    position: 'relative',
  },
  heroBubble1: {position: 'absolute', width: 280, height: 280, borderRadius: 140, backgroundColor: Colors.primaryDark, opacity: 0.4, top: -100, right: -60},
  heroBubble2: {position: 'absolute', width: 180, height: 180, borderRadius: 90, backgroundColor: Colors.primaryLight, opacity: 0.12, bottom: -60, left: -40},
  backBtn: {width: 38, height: 38, borderRadius: 12, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center', marginBottom: Spacing.md},
  backIcon: {fontSize: 24, color: '#fff', fontWeight: '700', lineHeight: 28},
  heroBody: {flexDirection: 'row', alignItems: 'center'},
  avatarWrap: {width: 62, height: 62, marginRight: Spacing.base, alignItems: 'center', justifyContent: 'center'},
  avatarRing: {position: 'absolute', width: 78, height: 78, borderRadius: 39, borderWidth: 1.5, borderColor: 'rgba(165,243,252,0.8)', top: -8, left: -8},
  avatar: {width: 58, height: 58, borderRadius: 29, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: 'rgba(255,255,255,0.3)'},
  avatarText: {fontSize: 22, fontWeight: '800', color: '#fff'},
  statusOrb: {position: 'absolute', bottom: 0, right: 0, width: 15, height: 15, borderRadius: 8, borderWidth: 2.5, borderColor: Colors.primary},
  heroMeta: {flex: 1},
  heroDeviceName: {fontSize: 20, fontWeight: '800', color: '#fff', letterSpacing: -0.3, marginBottom: 8},
  heroStatusRow: {flexDirection: 'row', gap: 6, flexWrap: 'wrap'},
  statusPill: {flexDirection: 'row', alignItems: 'center', borderRadius: Radii.pill, paddingHorizontal: 10, paddingVertical: 4},
  statusPillDot: {width: 6, height: 6, borderRadius: 3, marginRight: 5},
  statusPillText: {fontSize: 12, fontWeight: '700'},
  batteryChip: {backgroundColor: 'rgba(255,255,255,0.15)', borderRadius: Radii.pill, paddingHorizontal: 8, paddingVertical: 4},
  batteryChipText: {fontSize: 12, color: '#fff', fontWeight: '600'},
  networkChip: {backgroundColor: 'rgba(255,255,255,0.15)', borderRadius: Radii.pill, paddingHorizontal: 8, paddingVertical: 4},
  networkChipText: {fontSize: 12, color: '#fff', fontWeight: '600'},
  lastSeenText: {fontSize: 11, color: Colors.textOnDarkSoft, marginTop: 6},

  // Permission Alert
  permAlert: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.warningLight,
    borderRadius: Radii.md,
    marginHorizontal: Spacing.base,
    marginTop: Spacing.base,
    padding: Spacing.md,
    borderLeftWidth: 3,
    borderLeftColor: Colors.warning,
  },
  permAlertIcon: {width: 36, height: 36, borderRadius: 10, backgroundColor: '#FDE68A', alignItems: 'center', justifyContent: 'center', marginRight: Spacing.sm},
  permAlertBody: {flex: 1},
  permAlertTitle: {fontSize: 13, fontWeight: '700', color: Colors.warning},
  permAlertSub: {fontSize: 12, color: '#92400E', marginTop: 1},
  permAlertBtn: {backgroundColor: Colors.warning, borderRadius: Radii.sm, paddingHorizontal: 12, paddingVertical: 6},
  permAlertBtnText: {fontSize: 12, fontWeight: '800', color: '#fff'},

  // Section wrapper
  section: {marginHorizontal: Spacing.base, marginTop: Spacing.lg},
  sectionHeader: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: Spacing.md},
  sectionEyebrow: {...Typography.caption},
  sectionTitle: {...Typography.h3, marginTop: 2},
  sectionAction: {fontSize: 13, fontWeight: '700', color: Colors.primary},

  // Live monitoring
  monRow: {flexDirection: 'row', gap: Spacing.sm},
  monTile: {flex: 1, borderRadius: Radii.xl, padding: Spacing.md, minHeight: 120, justifyContent: 'space-between'},
  monTileTop: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start'},
  monTileIcon: {fontSize: 28},
  livePill: {backgroundColor: 'rgba(255,255,255,0.25)', borderRadius: Radii.pill, paddingHorizontal: 6, paddingVertical: 2},
  livePillText: {fontSize: 9, fontWeight: '800', color: '#fff', letterSpacing: 0.4},
  monTileLabel: {fontSize: 14, fontWeight: '800', color: '#fff', marginTop: Spacing.sm},
  monTileDesc: {fontSize: 11, color: 'rgba(255,255,255,0.7)', fontWeight: '500', marginTop: 2},

  // Usage Card
  usageCard: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Spacing.base,
    ...Elevation.card,
    borderWidth: 1,
    borderColor: Colors.borderLight,
  },
  usageCardTop: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: Spacing.md},
  usageCardTitle: {...Typography.h3},
  usageTotalWrap: {alignItems: 'flex-end'},
  usageTotalNum: {fontSize: 24, fontWeight: '900', color: Colors.primary, letterSpacing: -0.5},
  usageTotalLbl: {fontSize: 11, color: Colors.textTertiary, fontWeight: '600'},
  usageBars: {gap: Spacing.sm, marginBottom: Spacing.sm},
  usageBarRow: {flexDirection: 'row', alignItems: 'center', gap: 8},
  usageBarLabel: {width: 72, fontSize: 12, color: Colors.textSecondary, fontWeight: '600'},
  miniBarTrack: {flex: 1, height: 7, backgroundColor: Colors.primarySurface, borderRadius: 4, overflow: 'hidden'},
  miniBarFill: {height: 7, borderRadius: 4},
  usageBarTime: {width: 44, fontSize: 11, color: Colors.textTertiary, textAlign: 'right', fontWeight: '700'},
  usageEmpty: {fontSize: 13, color: Colors.textTertiary, textAlign: 'center', paddingVertical: Spacing.base, lineHeight: 19},
  usageCardFoot: {borderTopWidth: 1, borderTopColor: Colors.borderLight, paddingTop: Spacing.sm, marginTop: Spacing.sm},
  usageCardFootCta: {fontSize: 13, fontWeight: '700', color: Colors.primary},

  // Block All
  blockCard: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Spacing.base,
    flexDirection: 'row',
    alignItems: 'center',
    ...Elevation.card,
    borderWidth: 1,
    borderColor: Colors.borderLight,
  },
  blockCardLeft: {flex: 1, flexDirection: 'row', alignItems: 'center'},
  blockIconWrap: {width: 52, height: 52, borderRadius: Radii.lg, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginRight: Spacing.md},
  blockTextWrap: {flex: 1},
  blockTitle: {...Typography.h4},
  blockSub: {fontSize: 12, color: Colors.textTertiary, marginTop: 3, lineHeight: 17},

  // Control grid
  ctrlGrid: {flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm},
  ctrlTileWrap: {width: (SW - Spacing.base * 2 - Spacing.sm) / 2},
  ctrlTile: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Spacing.base,
    ...Elevation.card,
    borderWidth: 1,
    borderColor: Colors.borderLight,
  },
  ctrlTileIcon: {width: 46, height: 46, borderRadius: Radii.md, alignItems: 'center', justifyContent: 'center', marginBottom: Spacing.sm},
  ctrlTileLabel: {fontSize: 14, fontWeight: '700', color: Colors.textPrimary},
  ctrlTileSub: {fontSize: 12, color: Colors.textTertiary, marginTop: 2},

  // Map
  mapWrap: {borderRadius: Radii.xl, overflow: 'hidden', position: 'relative', ...Elevation.card},
  mapOverlay: {position: 'absolute', bottom: 12, left: 12, right: 12, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'},
  mapTag: {backgroundColor: 'rgba(0,0,0,0.5)', borderRadius: Radii.sm, paddingHorizontal: 8, paddingVertical: 4},
  mapTagText: {fontSize: 11, color: '#fff', fontWeight: '600'},
  geofenceChip: {backgroundColor: Colors.primary, borderRadius: Radii.sm, paddingHorizontal: 12, paddingVertical: 6},
  geofenceChipText: {fontSize: 12, color: '#fff', fontWeight: '700'},
  noLocCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Spacing.lg,
    ...Elevation.card,
    borderWidth: 1,
    borderColor: Colors.borderLight,
  },
  noLocTitle: {...Typography.h4},
  noLocSub: {fontSize: 12, color: Colors.textTertiary, marginTop: 2},

  // Snapshot
  snapRow: {flexDirection: 'row', gap: Spacing.sm},
  snapTile: {
    flex: 1,
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Spacing.base,
    alignItems: 'center',
    ...Elevation.card,
    borderWidth: 1,
    borderColor: Colors.borderLight,
  },
  snapLabel: {fontSize: 11, fontWeight: '700', color: Colors.textSecondary, textAlign: 'center'},

  // Info card
  infoCard: {backgroundColor: Colors.surface, borderRadius: Radii.xl, ...Elevation.card, borderWidth: 1, borderColor: Colors.borderLight, overflow: 'hidden'},
  infoRow: {flexDirection: 'row', alignItems: 'center', paddingHorizontal: Spacing.base, paddingVertical: Spacing.md, borderBottomWidth: 0.5, borderBottomColor: Colors.borderLight},
  infoLabel: {flex: 1, fontSize: 14, color: Colors.textSecondary, fontWeight: '500', marginLeft: Spacing.sm},
  infoValue: {fontSize: 14, fontWeight: '700', color: Colors.textPrimary},

  // Revoke / re-setup
  revokedBanner: {
    marginHorizontal: Spacing.base,
    marginTop: Spacing.base,
    backgroundColor: '#FEF2F2',
    borderRadius: Radii.md,
    padding: Spacing.base,
    borderWidth: 1,
    borderColor: '#FECACA',
  },
  revokedBannerTitle: {fontSize: 16, fontWeight: '800', color: '#B91C1C', marginBottom: 4},
  revokedBannerBody: {fontSize: 13, color: '#7F1D1D', lineHeight: 18},
  setupAgainBtn: {
    marginHorizontal: Spacing.base,
    marginTop: Spacing.lg,
    backgroundColor: Colors.primary,
    borderRadius: Radii.xl,
    padding: Spacing.base,
    alignItems: 'center',
    ...Elevation.card,
  },
  setupAgainTitle: {fontSize: 16, fontWeight: '800', color: '#fff'},
  setupAgainSub: {fontSize: 12, color: 'rgba(255,255,255,0.85)', marginTop: 4},
  pairingCard: {
    marginHorizontal: Spacing.base,
    marginTop: Spacing.lg,
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Spacing.base,
    borderWidth: 1,
    borderColor: Colors.borderLight,
    ...Elevation.card,
  },
  pairingLabel: {fontSize: 12, fontWeight: '700', color: Colors.textTertiary, marginBottom: 8},
  pairingCode: {
    fontSize: 28,
    fontWeight: '800',
    letterSpacing: 4,
    color: Colors.primary,
    textAlign: 'center',
    marginBottom: 8,
  },
  pairingHint: {fontSize: 12, color: Colors.textSecondary, lineHeight: 17},
  passwordBtn: {
    marginHorizontal: Spacing.base,
    marginTop: Spacing.lg,
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Spacing.base,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.borderLight,
    ...Elevation.card,
  },
  passwordText: {fontSize: 15, fontWeight: '700', color: Colors.textPrimary},
  passwordSub: {fontSize: 12, color: Colors.textTertiary, marginTop: 3, textAlign: 'center'},
  revokeBtn: {
    marginHorizontal: Spacing.base,
    marginTop: Spacing.lg,
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Spacing.base,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: '#FCA5A5',
    ...Elevation.card,
  },
  revokeText: {fontSize: 15, fontWeight: '700', color: Colors.danger},
  revokeSub: {fontSize: 12, color: '#F87171', marginTop: 3},

  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(15, 23, 42, 0.55)',
    justifyContent: 'center',
    padding: 24,
  },
  modalCard: {backgroundColor: '#fff', borderRadius: 16, padding: 20},
  modalTitle: {fontSize: 18, fontWeight: '700', color: '#0f172a', marginBottom: 8},
  modalBody: {fontSize: 13, color: '#64748b', lineHeight: 19, marginBottom: 16},
  modalInput: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 10,
    fontSize: 15,
    color: '#0f172a',
  },
  modalPrimary: {
    backgroundColor: Colors.primary,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 4,
  },
  modalPrimaryText: {color: '#fff', fontWeight: '700', fontSize: 15},
  modalCancel: {textAlign: 'center', color: '#64748b', fontWeight: '600', marginTop: 12},
  modalLink: {textAlign: 'center', color: '#2563eb', fontWeight: '600', marginTop: 12},

  scroll: {paddingBottom: Spacing.xxl},
});
