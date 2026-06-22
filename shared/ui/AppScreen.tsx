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

// ── Design tokens ──────────────────────────────────────────────────────────
const C = {
  primary: '#4F46E5',
  primaryDark: '#3730A3',
  primaryLight: '#818CF8',
  primarySurface: '#EEF2FF',
  accent: '#7C3AED',
  success: '#059669',
  successLight: '#D1FAE5',
  warning: '#D97706',
  warningLight: '#FEF3C7',
  danger: '#DC2626',
  dangerLight: '#FEE2E2',
  bg: '#F7F7FF',
  surface: '#FFFFFF',
  border: '#E8E8F0',
  borderLight: '#F1F0FF',
  textPrimary: '#0F172A',
  textSecondary: '#475569',
  textTertiary: '#94A3B8',
  textOnDark: '#FFFFFF',
  textOnDarkSoft: '#C7D2FE',
};

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
      <View style={styles.heroBubble1} />
      <View style={styles.heroBubble2} />
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
      <View style={styles.heroBubble1} />
      <View style={styles.heroBubble2} />
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
      <Text style={[styles.chevron, danger && {color: C.danger}]}>›</Text>
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
        placeholderTextColor={C.textTertiary}
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
      <View style={[styles.statusBadgeDot, {backgroundColor: granted ? C.success : C.warning}]} />
      <Text style={[styles.statusBadgeText, {color: granted ? C.success : C.warning}]}>
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
}: {
  visible: boolean;
  title: string;
  body: string;
  confirmLabel: string;
  danger?: boolean;
  loading?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View style={styles.modalBackdrop}>
        <View style={styles.modalCard}>
          <View style={styles.modalHandle} />
          <Text style={styles.modalTitle}>{title}</Text>
          <Text style={styles.modalBody}>{body}</Text>
          <View style={styles.modalActions}>
            <Pressable style={styles.modalCancel} onPress={onCancel} disabled={loading}>
              <Text style={styles.modalCancelText}>Cancel</Text>
            </Pressable>
            <Pressable
              style={[styles.modalConfirm, danger && styles.modalConfirmDanger]}
              onPress={onConfirm}
              disabled={loading}>
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
        <ActivityIndicator size="large" color={C.primary} />
      </View>
      {label ? <Text style={styles.loadingLabel}>{label}</Text> : null}
    </SafeAreaView>
  );
}

// ── Styles ─────────────────────────────────────────────────────────────────
export const appScreenStyles = StyleSheet.create({
  root: {flex: 1, backgroundColor: C.bg},
  flex: {flex: 1},
  scrollContent: {padding: 16, paddingBottom: 40},

  // Back
  backRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 10,
    paddingBottom: 6,
  },
  backBtn: {
    width: 32,
    height: 32,
    borderRadius: 10,
    backgroundColor: C.primarySurface,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 8,
  },
  backBtnText: {fontSize: 20, color: C.primary, fontWeight: '700', lineHeight: 26},
  backText: {fontSize: 14, fontWeight: '700', color: C.primary},

  // Hero
  hero: {
    backgroundColor: C.primary,
    borderRadius: 22,
    padding: 20,
    marginBottom: 16,
    overflow: 'hidden',
    position: 'relative',
  },
  heroBubble1: {position: 'absolute', width: 200, height: 200, borderRadius: 100, backgroundColor: C.primaryDark, opacity: 0.3, top: -70, right: -50},
  heroBubble2: {position: 'absolute', width: 130, height: 130, borderRadius: 65, backgroundColor: C.primaryLight, opacity: 0.12, bottom: -40, left: -30},
  heroCentered: {alignItems: 'center', padding: 28},
  heroIconWrap: {
    width: 64,
    height: 64,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.15)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
  },
  heroIconEmoji: {fontSize: 30},
  heroEyebrow: {color: C.textOnDarkSoft, fontSize: 11, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 4},
  heroEyebrowCentered: {color: C.textOnDarkSoft, fontSize: 11, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 6},
  heroTitle: {color: '#fff', fontSize: 22, fontWeight: '800', letterSpacing: -0.3},
  heroTitleCentered: {color: '#fff', fontSize: 24, fontWeight: '800', letterSpacing: -0.3, textAlign: 'center', marginBottom: 8},
  heroSub: {color: C.textOnDarkSoft, fontSize: 14, marginTop: 8, lineHeight: 21},
  heroSubCentered: {color: C.textOnDarkSoft, fontSize: 14, textAlign: 'center', lineHeight: 21},

  // Card
  card: {
    backgroundColor: C.surface,
    borderRadius: 20,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: C.borderLight,
    shadowColor: C.primary,
    shadowOffset: {width: 0, height: 3},
    shadowOpacity: 0.06,
    shadowRadius: 12,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 11,
    fontWeight: '700',
    color: C.textTertiary,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginBottom: 12,
    marginTop: 4,
  },

  // Menu row
  menuRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 13,
    paddingHorizontal: 4,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: C.borderLight,
  },
  menuRowLast: {borderBottomWidth: 0},
  menuRowPressed: {opacity: 0.7},
  menuIconWrap: {width: 38, height: 38, borderRadius: 11, backgroundColor: C.primarySurface, alignItems: 'center', justifyContent: 'center', marginRight: 12},
  menuIconWrapDanger: {backgroundColor: '#FEE2E2'},
  menuIcon: {fontSize: 18},
  menuText: {flex: 1},
  menuTitle: {fontSize: 15, fontWeight: '700', color: C.textPrimary},
  menuSub: {fontSize: 12, color: C.textTertiary, marginTop: 2},
  chevron: {fontSize: 22, color: C.border, fontWeight: '300'},
  dangerText: {color: C.danger},

  // Action tile
  actionTile: {
    flex: 1,
    alignSelf: 'stretch',
    backgroundColor: C.surface,
    borderRadius: 18,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: C.borderLight,
    minHeight: 90,
    justifyContent: 'center',
    shadowColor: C.primary,
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.05,
    shadowRadius: 8,
    elevation: 2,
  },
  actionTileAccent: {backgroundColor: C.primarySurface, borderColor: '#C7D2FE'},
  actionTileDanger: {backgroundColor: '#FEF2F2', borderColor: '#FECACA'},
  actionTileDisabled: {opacity: 0.45},
  actionTilePressed: {backgroundColor: '#F8F7FF'},
  actionIconWrap: {width: 44, height: 44, borderRadius: 13, backgroundColor: C.primarySurface, alignItems: 'center', justifyContent: 'center', marginBottom: 8},
  actionIconWrapAccent: {backgroundColor: '#DDD6FE'},
  actionIconWrapDanger: {backgroundColor: '#FEE2E2'},
  actionIcon: {fontSize: 22},
  actionLabel: {fontSize: 12, fontWeight: '700', color: C.textSecondary, textAlign: 'center'},

  // Form field
  fieldWrap: {marginBottom: 14},
  fieldLabel: {fontSize: 13, fontWeight: '700', color: C.textSecondary, marginBottom: 6},
  fieldInput: {
    borderWidth: 1.5,
    borderColor: C.border,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 13,
    fontSize: 15,
    color: C.textPrimary,
    backgroundColor: C.surface,
  },

  // Primary button
  primaryBtn: {
    backgroundColor: C.primary,
    borderRadius: 16,
    paddingVertical: 15,
    alignItems: 'center',
    marginTop: 8,
    shadowColor: C.primary,
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.3,
    shadowRadius: 12,
    elevation: 5,
  },
  primaryBtnDanger: {backgroundColor: C.danger, shadowColor: C.danger},
  primaryBtnDisabled: {opacity: 0.5, shadowOpacity: 0},
  primaryBtnPressed: {opacity: 0.88},
  primaryBtnText: {color: '#fff', fontWeight: '800', fontSize: 15, letterSpacing: 0.1},

  // Secondary button
  secondaryBtn: {
    backgroundColor: C.primarySurface,
    borderRadius: 14,
    paddingVertical: 13,
    alignItems: 'center',
    marginTop: 8,
    borderWidth: 1,
    borderColor: '#C7D2FE',
  },
  secondaryBtnPressed: {backgroundColor: '#E0E7FF'},
  secondaryBtnText: {color: C.primary, fontWeight: '700', fontSize: 14},

  // Link
  linkBtn: {marginTop: 14, paddingVertical: 8, alignItems: 'center'},
  linkBtnText: {color: C.primary, fontWeight: '700', fontSize: 14},

  // Error
  errorBox: {
    backgroundColor: '#FEF2F2',
    borderRadius: 13,
    padding: 12,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#FECACA',
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
  },
  errorIcon: {fontSize: 15, marginTop: 1},
  errorText: {color: '#991B1B', fontSize: 14, lineHeight: 20, flex: 1},

  // Note
  noteCard: {
    backgroundColor: '#FFFBEB',
    borderRadius: 14,
    padding: 14,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#FDE68A',
    borderLeftWidth: 3,
    borderLeftColor: C.warning,
  },
  noteTitle: {fontSize: 13, fontWeight: '700', color: '#92400E', marginBottom: 4},
  noteBody: {fontSize: 13, color: '#A16207', lineHeight: 19},

  // Progress
  progressBox: {
    backgroundColor: C.surface,
    borderRadius: 14,
    padding: 14,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: C.borderLight,
  },
  progressRow: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10},
  progressLabel: {fontSize: 13, fontWeight: '600', color: C.textSecondary},
  progressPct: {fontSize: 13, fontWeight: '800', color: C.primary},
  progressTrack: {height: 7, backgroundColor: C.primarySurface, borderRadius: 4, overflow: 'hidden'},
  progressFill: {height: '100%', backgroundColor: C.success, borderRadius: 4},

  // Status badge
  statusBadge: {flexDirection: 'row', alignItems: 'center', paddingHorizontal: 9, paddingVertical: 4, borderRadius: 100},
  statusBadgeOk: {backgroundColor: '#D1FAE5'},
  statusBadgePending: {backgroundColor: '#FEF3C7'},
  statusBadgeDot: {width: 6, height: 6, borderRadius: 3, marginRight: 5},
  statusBadgeText: {fontSize: 11, fontWeight: '700'},

  // Stat card
  statCard: {
    flex: 1,
    backgroundColor: C.surface,
    borderRadius: 18,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: C.borderLight,
    shadowColor: C.primary,
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.05,
    shadowRadius: 8,
    elevation: 2,
  },
  statValue: {fontSize: 26, fontWeight: '900', color: C.primary, letterSpacing: -0.5},
  statValueGreen: {color: C.success},
  statValueAlert: {color: C.danger},
  statLabel: {fontSize: 11, color: C.textTertiary, marginTop: 3, fontWeight: '600'},

  // Empty state
  emptyCard: {
    backgroundColor: C.surface,
    borderRadius: 22,
    padding: 32,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: C.borderLight,
    marginTop: 8,
  },
  emptyIconWrap: {width: 76, height: 76, borderRadius: 38, backgroundColor: C.primarySurface, alignItems: 'center', justifyContent: 'center', marginBottom: 14},
  emptyIcon: {fontSize: 34},
  emptyTitle: {fontSize: 17, fontWeight: '800', color: C.textPrimary},
  emptyBody: {fontSize: 13, color: C.textTertiary, textAlign: 'center', marginTop: 6, lineHeight: 20},

  // Modal
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(15, 23, 42, 0.6)',
    justifyContent: 'flex-end',
    padding: 0,
  },
  modalCard: {
    backgroundColor: C.surface,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    padding: 24,
    paddingTop: 12,
  },
  modalHandle: {width: 36, height: 4, borderRadius: 2, backgroundColor: C.border, alignSelf: 'center', marginBottom: 20},
  modalTitle: {fontSize: 18, fontWeight: '800', color: C.textPrimary, marginBottom: 8},
  modalBody: {fontSize: 14, color: C.textSecondary, lineHeight: 21, marginBottom: 24},
  modalActions: {flexDirection: 'row', gap: 12},
  modalCancel: {flex: 1, backgroundColor: C.primarySurface, borderRadius: 14, paddingVertical: 14, alignItems: 'center'},
  modalCancelText: {color: C.primary, fontWeight: '700', fontSize: 15},
  modalConfirm: {
    flex: 1.5,
    backgroundColor: C.primary,
    borderRadius: 14,
    paddingVertical: 14,
    alignItems: 'center',
    shadowColor: C.primary,
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 5,
  },
  modalConfirmDanger: {backgroundColor: C.danger, shadowColor: C.danger},
  modalConfirmText: {color: '#fff', fontWeight: '800', fontSize: 15},

  // Meta
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: C.borderLight,
  },
  metaLabel: {fontSize: 13, color: C.textSecondary, fontWeight: '500'},
  metaValue: {fontSize: 13, fontWeight: '700', color: C.textPrimary, maxWidth: '60%', textAlign: 'right'},

  // Loading
  loadingRoot: {flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: C.bg},
  loadingSpinnerWrap: {width: 70, height: 70, borderRadius: 20, backgroundColor: C.primarySurface, alignItems: 'center', justifyContent: 'center', marginBottom: 14},
  loadingLabel: {marginTop: 4, color: C.textTertiary, fontSize: 14, fontWeight: '500'},
});

const styles = appScreenStyles;
