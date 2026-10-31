import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  ScrollView,
  TextInput,
  TextInputProps,
  ActivityIndicator,
  Modal,
  ViewStyle,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {Colors, Typography, Spacing, Radii, Elevation, Layout} from '../theme/design';

function HeroGradientLayers() {
  return (
    <>
      <View style={styles.heroGradientBase} />
      <View style={styles.heroGradientOverlay} />
      <View style={styles.heroBubble1} />
      <View style={styles.heroBubble2} />
    </>
  );
}

export function ScreenShell({
  children,
  backLabel,
  onBack,
  scroll = true,
}: {
  children: React.ReactNode;
  backLabel?: string;
  onBack?: () => void;
  scroll?: boolean;
}) {
  const body = scroll ? (
    <ScrollView
      contentContainerStyle={styles.scrollContent}
      showsVerticalScrollIndicator={false}
      keyboardShouldPersistTaps="handled">
      {children}
    </ScrollView>
  ) : (
    <View style={styles.flex}>{children}</View>
  );

  return (
    <SafeAreaView style={styles.root} edges={['bottom', 'top']}>
      {backLabel && onBack ? (
        <Pressable style={styles.backRow} onPress={onBack}>
          <View style={styles.backBtn}>
            <Text style={styles.backBtnText}>‹</Text>
          </View>
          <Text style={styles.backText}>{backLabel.replace(/^←\s*/, '')}</Text>
        </Pressable>
      ) : null}
      {body}
    </SafeAreaView>
  );
}

export function HeroBlock({
  eyebrow,
  title,
  subtitle,
  children,
}: {
  eyebrow?: string;
  title: string;
  subtitle?: string;
  children?: React.ReactNode;
}) {
  return (
    <View style={styles.hero}>
      <HeroGradientLayers />
      {eyebrow ? <Text style={styles.heroEyebrow}>{eyebrow}</Text> : null}
      <Text style={styles.heroTitle}>{title}</Text>
      {subtitle ? <Text style={styles.heroSub}>{subtitle}</Text> : null}
      {children}
    </View>
  );
}

export function HeroIconBlock({
  icon,
  title,
  subtitle,
  eyebrow,
}: {
  icon: string;
  title: string;
  subtitle?: string;
  eyebrow?: string;
}) {
  return (
    <View style={[styles.hero, styles.heroCentered]}>
      <HeroGradientLayers />
      <View style={styles.heroIconWrap}>
        <Text style={styles.heroIconEmoji}>{icon}</Text>
      </View>
      {eyebrow ? <Text style={styles.heroEyebrowCentered}>{eyebrow}</Text> : null}
      <Text style={styles.heroTitleCentered}>{title}</Text>
      {subtitle ? <Text style={styles.heroSubCentered}>{subtitle}</Text> : null}
    </View>
  );
}

export function Card({children, style}: {children: React.ReactNode; style?: ViewStyle}) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function SectionTitle({children}: {children: string}) {
  return <Text style={styles.sectionTitle}>{children}</Text>;
}

export function MenuRow({
  icon,
  title,
  subtitle,
  onPress,
  danger,
  last,
}: {
  icon: string;
  title: string;
  subtitle?: string;
  onPress: () => void;
  danger?: boolean;
  last?: boolean;
}) {
  return (
    <Pressable
      style={({pressed}) => [
        styles.menuRow,
        last && styles.menuRowLast,
        pressed && styles.menuRowPressed,
      ]}
      onPress={onPress}>
      <View style={[styles.menuIconWrap, danger && styles.menuIconWrapDanger]}>
        <Text style={styles.menuIcon}>{icon}</Text>
      </View>
      <View style={styles.menuText}>
        <Text style={[styles.menuTitle, danger && styles.dangerText]}>{title}</Text>
        {subtitle ? <Text style={styles.menuSub}>{subtitle}</Text> : null}
      </View>
      <Text style={[styles.chevron, danger && {color: Colors.danger}]}>›</Text>
    </Pressable>
  );
}

export function ActionTile({
  icon,
  label,
  onPress,
  disabled,
  danger,
  accent,
}: {
  icon: string;
  label: string;
  onPress: () => void;
  disabled?: boolean;
  danger?: boolean;
  accent?: boolean;
}) {
  return (
    <Pressable
      style={({pressed}) => [
        styles.actionTile,
        accent && styles.actionTileAccent,
        danger && styles.actionTileDanger,
        disabled && styles.actionTileDisabled,
        pressed && !disabled && styles.actionTilePressed,
      ]}
      onPress={onPress}
      disabled={disabled}>
      <View style={[styles.actionIconWrap, accent && styles.actionIconWrapAccent, danger && styles.actionIconWrapDanger]}>
        <Text style={styles.actionIcon}>{icon}</Text>
      </View>
      <Text style={[styles.actionLabel, danger && styles.dangerText]}>{label}</Text>
    </Pressable>
  );
}

export function FormField({label, style, ...inputProps}: {label: string} & TextInputProps) {
  return (
    <View style={styles.fieldWrap}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        style={[styles.fieldInput, style]}
        placeholderTextColor={Colors.textTertiary}
        {...inputProps}
      />
    </View>
  );
}

export function PrimaryBtn({
  title,
  onPress,
  loading,
  disabled,
  danger,
}: {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  danger?: boolean;
}) {
  return (
    <Pressable
      style={({pressed}) => [
        styles.primaryBtn,
        danger && styles.primaryBtnDanger,
        (disabled || loading) && styles.primaryBtnDisabled,
        pressed && !disabled && styles.primaryBtnPressed,
      ]}
      onPress={onPress}
      disabled={disabled || loading}>
      {loading ? (
        <ActivityIndicator color="#fff" />
      ) : (
        <Text style={styles.primaryBtnText}>{title}</Text>
      )}
    </Pressable>
  );
}

export function SecondaryBtn({
  title,
  onPress,
  disabled,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <Pressable
      style={({pressed}) => [
        styles.secondaryBtn,
        disabled && styles.primaryBtnDisabled,
        pressed && !disabled && styles.secondaryBtnPressed,
      ]}
      onPress={onPress}
      disabled={disabled}>
      <Text style={styles.secondaryBtnText}>{title}</Text>
    </Pressable>
  );
}

export function LinkBtn({title, onPress}: {title: string; onPress: () => void}) {
  return (
    <Pressable style={styles.linkBtn} onPress={onPress}>
      <Text style={styles.linkBtnText}>{title}</Text>
    </Pressable>
  );
}

export function ErrorBox({message}: {message: string}) {
  return (
    <View style={styles.errorBox}>
      <Text style={styles.errorIcon}>⚠️</Text>
      <Text style={styles.errorText}>{message}</Text>
    </View>
  );
}

export function NoteCard({title, body}: {title: string; body: string}) {
  return (
    <View style={styles.noteCard}>
      <Text style={styles.noteTitle}>ℹ️  {title}</Text>
      <Text style={styles.noteBody}>{body}</Text>
    </View>
  );
}

export function ProgressBar({
  granted,
  total,
  label,
}: {
  granted: number;
  total: number;
  label?: string;
}) {
  const pct = total > 0 ? Math.round((granted / total) * 100) : 0;
  return (
    <View style={styles.progressBox}>
      <View style={styles.progressRow}>
        <Text style={styles.progressLabel}>{label ?? `${granted} of ${total} granted`}</Text>
        <Text style={styles.progressPct}>{pct}%</Text>
      </View>
      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, {width: `${pct}%`}]} />
      </View>
    </View>
  );
}

export function StatusBadge({granted}: {granted: boolean}) {
  return (
    <View style={[styles.statusBadge, granted ? styles.statusBadgeOk : styles.statusBadgePending]}>
      <View style={[styles.statusBadgeDot, {backgroundColor: granted ? Colors.success : Colors.warning}]} />
      <Text style={[styles.statusBadgeText, {color: granted ? Colors.success : Colors.warning}]}>
        {granted ? 'Granted' : 'Tap to grant'}
      </Text>
    </View>
  );
}

export function StatCard({
  value,
  label,
  tone,
}: {
  value: string | number;
  label: string;
  tone?: 'default' | 'green' | 'alert';
}) {
  return (
    <View style={styles.statCard}>
      <Text
        style={[
          styles.statValue,
          tone === 'green' && styles.statValueGreen,
          tone === 'alert' && styles.statValueAlert,
        ]}>
        {value}
      </Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

export function EmptyState({icon, title, body}: {icon: string; title: string; body?: string}) {
  return (
    <View style={styles.emptyCard}>
      <View style={styles.emptyIconWrap}>
        <Text style={styles.emptyIcon}>{icon}</Text>
      </View>
      <Text style={styles.emptyTitle}>{title}</Text>
      {body ? <Text style={styles.emptyBody}>{body}</Text> : null}
    </View>
  );
}

export function ConfirmModal({
  visible,
  title,
  body,
  confirmLabel,
  danger,
  loading,
  onCancel,
  onConfirm,
  passwordValue,
  onPasswordChange,
  passwordPlaceholder = 'Account password',
}: {
  visible: boolean;
  title: string;
  body: string;
  confirmLabel: string;
  danger?: boolean;
  loading?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  /** When set with onPasswordChange, shows a password field (e.g. revoke pairing). */
  passwordValue?: string;
  onPasswordChange?: (value: string) => void;
  passwordPlaceholder?: string;
}) {
  const passwordRequired = typeof onPasswordChange === 'function';
  const confirmDisabled = loading || (passwordRequired && !(passwordValue ?? '').trim());
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View style={styles.modalBackdrop}>
        <View style={styles.modalCard}>
          <View style={styles.modalHandle} />
          <Text style={styles.modalTitle}>{title}</Text>
          <Text style={styles.modalBody}>{body}</Text>
          {passwordRequired ? (
            <TextInput
              style={styles.modalPasswordInput}
              value={passwordValue}
              onChangeText={onPasswordChange}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              placeholder={passwordPlaceholder}
              editable={!loading}
            />
          ) : null}
          <View style={styles.modalActions}>
            <Pressable style={styles.modalCancel} onPress={onCancel} disabled={loading}>
              <Text style={styles.modalCancelText}>Cancel</Text>
            </Pressable>
            <Pressable
              style={[
                styles.modalConfirm,
                danger && styles.modalConfirmDanger,
                confirmDisabled && {opacity: 0.55},
              ]}
              onPress={onConfirm}
              disabled={confirmDisabled}>
              {loading ? (
                <ActivityIndicator color="#fff" size="small" />
              ) : (
                <Text style={styles.modalConfirmText}>{confirmLabel}</Text>
              )}
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

export function MetaRow({label, value}: {label: string; value: string}) {
  return (
    <View style={styles.metaRow}>
      <Text style={styles.metaLabel}>{label}</Text>
      <Text style={styles.metaValue}>{value}</Text>
    </View>
  );
}

export function LoadingCenter({label}: {label?: string}) {
  return (
    <SafeAreaView style={styles.loadingRoot}>
      <View style={styles.loadingSpinnerWrap}>
        <ActivityIndicator size="large" color={Colors.primaryContainer} />
      </View>
      {label ? <Text style={styles.loadingLabel}>{label}</Text> : null}
    </SafeAreaView>
  );
}

// ── Styles ─────────────────────────────────────────────────────────────────
export const appScreenStyles = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.background},
  flex: {flex: 1},
  scrollContent: {padding: Layout.screenPadding, paddingBottom: Layout.sectionGap},

  backRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Layout.screenPadding,
    paddingTop: Spacing.md,
    paddingBottom: Spacing.sm,
  },
  backBtn: {
    width: 36,
    height: 36,
    borderRadius: Radii.sm,
    backgroundColor: Colors.primarySurface,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: Spacing.sm,
  },
  backBtnText: {fontSize: 20, color: Colors.primaryContainer, fontWeight: '700', lineHeight: 26},
  backText: {...Typography.smallLabel, color: Colors.primaryContainer, fontWeight: '700'},

  hero: {
    borderRadius: Radii.xl,
    padding: Layout.gutter,
    marginBottom: Layout.componentInternal,
    overflow: 'hidden',
    position: 'relative',
    ...Elevation.hero,
  },
  heroGradientBase: {...StyleSheet.absoluteFillObject, backgroundColor: Colors.primaryContainer},
  heroGradientOverlay: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: '35%',
    backgroundColor: Colors.accent,
    opacity: 0.88,
  },
  heroBubble1: {position: 'absolute', width: 200, height: 200, borderRadius: 100, backgroundColor: 'rgba(255,255,255,0.08)', top: -70, right: -50},
  heroBubble2: {position: 'absolute', width: 130, height: 130, borderRadius: 65, backgroundColor: 'rgba(255,255,255,0.06)', bottom: -40, left: -30},
  heroCentered: {alignItems: 'center', padding: Layout.gutter},
  heroIconWrap: {
    width: 64,
    height: 64,
    borderRadius: Radii.lg,
    backgroundColor: 'rgba(255,255,255,0.18)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Layout.componentInternal,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.25)',
    zIndex: 1,
  },
  heroIconEmoji: {fontSize: 30},
  heroEyebrow: {...Typography.caption, color: Colors.textOnDarkSoft, marginBottom: Spacing.half, zIndex: 1},
  heroEyebrowCentered: {...Typography.caption, color: Colors.textOnDarkSoft, marginBottom: 6, zIndex: 1},
  heroTitle: {...Typography.sectionTitle, color: Colors.textOnDark, zIndex: 1},
  heroTitleCentered: {...Typography.sectionTitle, color: Colors.textOnDark, textAlign: 'center', marginBottom: Spacing.sm, zIndex: 1},
  heroSub: {...Typography.body, color: Colors.textOnDarkSoft, marginTop: Spacing.sm, zIndex: 1},
  heroSubCentered: {...Typography.body, color: Colors.textOnDarkSoft, textAlign: 'center', zIndex: 1},

  card: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Layout.componentInternal,
    marginBottom: Layout.componentInternal,
    borderWidth: 1,
    borderColor: Colors.border,
    ...Elevation.card,
  },
  sectionTitle: {...Typography.caption, marginBottom: Layout.componentInternal, marginTop: Spacing.half},

  menuRow: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: Layout.listRowHeight,
    paddingVertical: Layout.componentInternal,
    paddingHorizontal: Spacing.half,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: Colors.borderLight,
  },
  menuRowLast: {borderBottomWidth: 0},
  menuRowPressed: {opacity: 0.7},
  menuIconWrap: {width: 40, height: 40, borderRadius: Radii.md, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginRight: Layout.componentInternal},
  menuIconWrapDanger: {backgroundColor: Colors.dangerLight},
  menuIcon: {fontSize: 18},
  menuText: {flex: 1},
  menuTitle: {...Typography.cardTitle, fontSize: 15},
  menuSub: {...Typography.smallLabel, color: Colors.textTertiary, marginTop: 2},
  chevron: {fontSize: 22, color: Colors.outline, fontWeight: '300'},
  dangerText: {color: Colors.danger},

  actionTile: {
    flex: 1,
    alignSelf: 'stretch',
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Layout.componentInternal,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.border,
    minHeight: 90,
    justifyContent: 'center',
    ...Elevation.card,
  },
  actionTileAccent: {backgroundColor: Colors.primarySurface, borderColor: Colors.outlineVariant},
  actionTileDanger: {backgroundColor: Colors.dangerLight, borderColor: `${Colors.error}33`},
  actionTileDisabled: {opacity: 0.45},
  actionTilePressed: {backgroundColor: Colors.surfaceContainerLow},
  actionIconWrap: {width: 44, height: 44, borderRadius: Radii.md, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginBottom: Spacing.sm},
  actionIconWrapAccent: {backgroundColor: Colors.accentSurface},
  actionIconWrapDanger: {backgroundColor: Colors.dangerLight},
  actionIcon: {fontSize: 22},
  actionLabel: {...Typography.smallLabel, fontWeight: '700', textAlign: 'center'},

  fieldWrap: {marginBottom: Layout.componentInternal},
  fieldLabel: {...Typography.smallLabel, color: Colors.textSecondary, marginBottom: Spacing.sm},
  fieldInput: {
    borderWidth: 1,
    borderColor: Colors.border,
    borderRadius: Radii.md,
    paddingHorizontal: Layout.componentInternal,
    paddingVertical: Spacing.md,
    fontSize: 15,
    color: Colors.textPrimary,
    backgroundColor: Colors.background,
    minHeight: Layout.touchTargetMin,
  },

  primaryBtn: {
    backgroundColor: Colors.primaryContainer,
    borderRadius: Radii.md,
    minHeight: Layout.touchTargetMin,
    paddingVertical: Spacing.md,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.sm,
    ...Elevation.cardHigh,
  },
  primaryBtnDanger: {backgroundColor: Colors.danger, shadowColor: Colors.danger},
  primaryBtnDisabled: {opacity: 0.5, shadowOpacity: 0},
  primaryBtnPressed: {opacity: 0.9},
  primaryBtnText: {...Typography.cardTitle, color: Colors.textOnDark, fontSize: 15},

  secondaryBtn: {
    backgroundColor: Colors.primarySurface,
    borderRadius: Radii.md,
    minHeight: Layout.touchTargetMin,
    paddingVertical: Spacing.md,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.sm,
    borderWidth: 1.5,
    borderColor: Colors.primarySurface,
  },
  secondaryBtnPressed: {backgroundColor: Colors.accentSurface},
  secondaryBtnText: {color: Colors.primary, fontWeight: '700', fontSize: 14},

  linkBtn: {marginTop: Layout.componentInternal, paddingVertical: Spacing.sm, alignItems: 'center'},
  linkBtnText: {color: Colors.primaryContainer, fontWeight: '700', fontSize: 14},

  errorBox: {
    backgroundColor: `${Colors.error}1A`,
    borderRadius: Radii.md,
    padding: Layout.componentInternal,
    marginBottom: Layout.componentInternal,
    borderWidth: 1,
    borderColor: `${Colors.error}33`,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.sm,
  },
  errorIcon: {fontSize: 15, marginTop: 1},
  errorText: {color: Colors.error, ...Typography.body, flex: 1},

  noteCard: {
    backgroundColor: Colors.warningLight,
    borderRadius: Radii.md,
    padding: Layout.componentInternal,
    marginBottom: Layout.componentInternal,
    borderWidth: 1,
    borderColor: '#FDE68A',
    borderLeftWidth: 3,
    borderLeftColor: Colors.warning,
  },
  noteTitle: {fontSize: 13, fontWeight: '700', color: '#92400E', marginBottom: 4},
  noteBody: {fontSize: 13, color: '#A16207', lineHeight: 19},

  progressBox: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.md,
    padding: Layout.componentInternal,
    marginBottom: Layout.componentInternal,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  progressRow: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.md},
  progressLabel: {...Typography.smallLabel, fontWeight: '600'},
  progressPct: {...Typography.smallLabel, fontWeight: '800', color: Colors.primaryContainer},
  progressTrack: {height: 8, backgroundColor: Colors.primarySurface, borderRadius: Radii.xs, overflow: 'hidden'},
  progressFill: {height: '100%', backgroundColor: Colors.success, borderRadius: Radii.xs},

  statusBadge: {flexDirection: 'row', alignItems: 'center', paddingHorizontal: 9, paddingVertical: 4, borderRadius: Radii.pill},
  statusBadgeOk: {backgroundColor: Colors.successLight},
  statusBadgePending: {backgroundColor: Colors.warningLight},
  statusBadgeDot: {width: 6, height: 6, borderRadius: 3, marginRight: 5},
  statusBadgeText: {fontSize: 11, fontWeight: '700'},

  statCard: {
    flex: 1,
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Layout.componentInternal,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.border,
    ...Elevation.card,
  },
  statValue: {...Typography.display, fontSize: 26, color: Colors.primaryContainer},
  statValueGreen: {color: Colors.success},
  statValueAlert: {color: Colors.danger},
  statLabel: {...Typography.smallLabel, color: Colors.textTertiary, marginTop: 3},

  emptyCard: {
    backgroundColor: Colors.surface,
    borderRadius: Radii.xl,
    padding: Layout.gutter,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.border,
    marginTop: Spacing.sm,
    ...Elevation.card,
  },
  emptyIconWrap: {width: 76, height: 76, borderRadius: 38, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginBottom: Layout.componentInternal},
  emptyIcon: {fontSize: 34},
  emptyTitle: {...Typography.cardTitle},
  emptyBody: {...Typography.smallLabel, color: Colors.textTertiary, textAlign: 'center', marginTop: 6, lineHeight: 20},

  modalBackdrop: {flex: 1, backgroundColor: 'rgba(15, 23, 42, 0.6)', justifyContent: 'flex-end'},
  modalCard: {backgroundColor: Colors.surface, borderTopLeftRadius: Radii.xl, borderTopRightRadius: Radii.xl, padding: Layout.gutter, paddingTop: Spacing.md},
  modalHandle: {width: 36, height: 4, borderRadius: 2, backgroundColor: Colors.border, alignSelf: 'center', marginBottom: Layout.gutter},
  modalTitle: {...Typography.cardTitle, marginBottom: Spacing.sm},
  modalBody: {...Typography.body, marginBottom: Layout.gutter},
  modalPasswordInput: {
    borderWidth: 1,
    borderColor: Colors.border,
    borderRadius: Radii.md,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    fontSize: 15,
    color: Colors.textPrimary,
    backgroundColor: Colors.background,
    marginBottom: Layout.gutter,
  },
  modalActions: {flexDirection: 'row', gap: Layout.componentInternal},
  modalCancel: {flex: 1, backgroundColor: Colors.primarySurface, borderRadius: Radii.md, minHeight: Layout.touchTargetMin, alignItems: 'center', justifyContent: 'center'},
  modalCancelText: {color: Colors.primaryContainer, fontWeight: '700', fontSize: 15},
  modalConfirm: {
    flex: 1.5,
    backgroundColor: Colors.primaryContainer,
    borderRadius: Radii.md,
    minHeight: Layout.touchTargetMin,
    alignItems: 'center',
    justifyContent: 'center',
    ...Elevation.cardHigh,
  },
  modalConfirmDanger: {backgroundColor: Colors.danger, shadowColor: Colors.danger},
  modalConfirmText: {color: Colors.textOnDark, fontWeight: '800', fontSize: 15},

  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: Spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: Colors.borderLight,
  },
  metaLabel: {...Typography.smallLabel, fontWeight: '500'},
  metaValue: {...Typography.smallLabel, fontWeight: '700', color: Colors.textPrimary, maxWidth: '60%', textAlign: 'right'},

  loadingRoot: {flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: Colors.background},
  loadingSpinnerWrap: {width: 70, height: 70, borderRadius: Radii.lg, backgroundColor: Colors.primarySurface, alignItems: 'center', justifyContent: 'center', marginBottom: Layout.componentInternal},
  loadingLabel: {...Typography.body, color: Colors.textTertiary, marginTop: 4},
});

const styles = appScreenStyles;
