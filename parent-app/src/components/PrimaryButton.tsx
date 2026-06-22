import React from 'react';
import {Pressable, StyleSheet, Text, ActivityIndicator} from 'react-native';

interface Props {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
}

export function PrimaryButton({title, onPress, loading, disabled}: Props) {
  return (
    <Pressable
      style={[styles.btn, (disabled || loading) && styles.disabled]}
      onPress={onPress}
      disabled={disabled || loading}>
      {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.text}>{title}</Text>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  btn: {
    backgroundColor: '#2563eb',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  disabled: {opacity: 0.6},
  text: {color: '#fff', fontSize: 16, fontWeight: '600'},
});
