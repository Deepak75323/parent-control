import React, {useCallback, useEffect, useState} from 'react';
import {StyleSheet, Switch, Text, View} from 'react-native';
import {nativeModules} from '../nativeModules';
import {Colors} from '../theme/design';

export type TamperProtectionKind = 'accessibility' | 'deviceAdmin';

type Props = {
  kind: TamperProtectionKind;
  /** When false, switch is disabled (e.g. permission not granted yet). */
  enabled?: boolean;
  compact?: boolean;
};

const COPY: Record<
  TamperProtectionKind,
  {label: string; hintOn: string; hintOff: string}
> = {
  accessibility: {
    label: 'Protect accessibility setting',
    hintOn: 'Turning off Clock Time in Settings needs a parent password or code.',
    hintOff: 'You can turn off Clock Time in Settings without a parent password.',
  },
  deviceAdmin: {
    label: 'Protect device admin setting',
    hintOn: 'Turning off device protection in Settings needs a parent password or code.',
    hintOff: 'You can turn off device protection in Settings without a parent password.',
  },
};

async function readEnabled(kind: TamperProtectionKind): Promise<boolean> {
  return kind === 'accessibility'
    ? nativeModules.isAccessibilityTamperProtectionEnabled()
    : nativeModules.isDeviceAdminTamperProtectionEnabled();
}

async function writeEnabled(kind: TamperProtectionKind, value: boolean): Promise<void> {
  if (kind === 'accessibility') {
    await nativeModules.setAccessibilityTamperProtectionEnabled(value);
    return;
  }
  await nativeModules.setDeviceAdminTamperProtectionEnabled(value);
}

export function TamperProtectionToggle({kind, enabled = true, compact = false}: Props) {
  const [on, setOn] = useState(kind === 'deviceAdmin');
  const [busy, setBusy] = useState(false);
  const copy = COPY[kind];

  useEffect(() => {
    let cancelled = false;
    void readEnabled(kind).then(value => {
      if (!cancelled) {
        setOn(value);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [kind]);

  const onToggle = useCallback(
    async (next: boolean) => {
      if (busy || !enabled) {
        return;
      }
      setBusy(true);
      setOn(next);
      try {
        await writeEnabled(kind, next);
      } catch {
        setOn(!next);
      } finally {
        setBusy(false);
      }
    },
    [busy, enabled, kind],
  );

  return (
    <View style={[styles.row, compact && styles.rowCompact, !enabled && styles.rowDisabled]}>
      <View style={styles.textCol}>
        <Text style={styles.label}>{copy.label}</Text>
        <Text style={styles.hint}>{on ? copy.hintOn : copy.hintOff}</Text>
      </View>
      <Switch
        value={on}
        onValueChange={next => void onToggle(next)}
        disabled={!enabled || busy}
        trackColor={{false: Colors.border, true: Colors.accentLight}}
        thumbColor={on ? Colors.primaryContainer : Colors.surfaceElevated}
        ios_backgroundColor={Colors.border}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 12,
    paddingHorizontal: 4,
  },
  rowCompact: {
    paddingVertical: 8,
    paddingHorizontal: 0,
  },
  rowDisabled: {
    opacity: 0.55,
  },
  textCol: {
    flex: 1,
  },
  label: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.textPrimary,
  },
  hint: {
    marginTop: 4,
    fontSize: 13,
    lineHeight: 18,
    color: Colors.textSecondary,
  },
});
