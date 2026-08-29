import React from 'react';
import {View, Text, StyleSheet, Pressable} from 'react-native';
import {STREAM_COLORS} from './layout';

export type StreamSource = 'SCREEN' | 'CAMERA' | 'AUDIO';

type Props = {
  active: StreamSource;
  onSelect: (type: StreamSource) => void;
  disabled?: boolean;
  screenDisabled?: boolean;
};

/** Camera-only build: Screen/Audio switcher options are omitted. */
const OPTIONS: {type: StreamSource; label: string; icon: string}[] = [
  {type: 'CAMERA', label: 'Camera', icon: '📷'},
];

export function StreamSourceSwitcher({active, onSelect, disabled, screenDisabled}: Props) {
  return (
    <View style={styles.row}>
      {OPTIONS.map(opt => {
        const isActive = active === opt.type;
        const isDisabled = disabled || (opt.type === 'SCREEN' && screenDisabled);
        return (
          <Pressable
            key={opt.type}
            style={[styles.chip, isActive && styles.chipActive, isDisabled && styles.chipDisabled]}
            onPress={() => onSelect(opt.type)}
            disabled={isDisabled}>
            <Text style={styles.chipIcon}>{opt.icon}</Text>
            <Text style={[styles.chipLabel, isActive && styles.chipLabelActive]}>{opt.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 8,
  },
  chip: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    paddingVertical: 8,
    paddingHorizontal: 6,
    borderRadius: 12,
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  chipActive: {
    backgroundColor: 'rgba(56, 189, 248, 0.28)',
    borderWidth: 1,
    borderColor: 'rgba(56, 189, 248, 0.55)',
  },
  chipDisabled: {
    opacity: 0.45,
  },
  chipIcon: {
    fontSize: 14,
  },
  chipLabel: {
    color: STREAM_COLORS.textMuted,
    fontSize: 12,
    fontWeight: '600',
  },
  chipLabelActive: {
    color: STREAM_COLORS.text,
  },
});
