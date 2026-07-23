import React from 'react';
import {Image, StyleSheet, Text, View} from 'react-native';

const AVATAR_COLORS = ['#7c3aed', '#9b59b6', '#a78bfa', '#6366f1', '#8b5cf6'];

type Props = {
  label: string;
  iconBase64?: string | null;
  index?: number;
  size?: number;
};

export function AppIcon({label, iconBase64, index = 0, size = 36}: Props) {
  const letter = (label.trim().charAt(0) || '?').toUpperCase();
  const bg = AVATAR_COLORS[index % AVATAR_COLORS.length];
  const dim = {width: size, height: size, borderRadius: Math.round(size * 0.28)};

  if (iconBase64) {
    return (
      <Image
        source={{uri: `data:image/png;base64,${iconBase64}`}}
        style={[styles.img, dim]}
        resizeMode="cover"
      />
    );
  }

  return (
    <View style={[styles.fallback, dim, {backgroundColor: bg}]}>
      <Text style={[styles.letter, {fontSize: Math.round(size * 0.42)}]}>{letter}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  img: {
    marginRight: 12,
    backgroundColor: '#f1f5f9',
  },
  fallback: {
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  letter: {
    fontWeight: '800',
    color: '#fff',
  },
});
