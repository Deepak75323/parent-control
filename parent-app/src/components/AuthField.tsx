import React, {useRef, useState} from 'react';
import {Animated, View, Text, TextInput, StyleSheet, TextInputProps} from 'react-native';

type Props = TextInputProps & {label: string};

const C = {
  primary: '#4F46E5',
  border: '#E8E8F0',
  surface: '#FFFFFF',
  textPrimary: '#0F172A',
  textSecondary: '#475569',
  textTertiary: '#94A3B8',
};

export function AuthField({label, style, ...rest}: Props) {
  const borderAnim = useRef(new Animated.Value(0)).current;
  const borderColor = borderAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [C.border, C.primary],
  });

  return (
    <View style={s.wrap}>
      <Text style={s.label}>{label}</Text>
      <Animated.View style={[s.inputWrap, {borderColor}]}>
        <TextInput
          style={[s.input, style]}
          placeholderTextColor={C.textTertiary}
          onFocus={() => Animated.timing(borderAnim, {toValue: 1, duration: 180, useNativeDriver: false}).start()}
          onBlur={() => Animated.timing(borderAnim, {toValue: 0, duration: 180, useNativeDriver: false}).start()}
          {...rest}
        />
      </Animated.View>
    </View>
  );
}

const s = StyleSheet.create({
  wrap: {marginBottom: 14},
  label: {fontSize: 13, fontWeight: '700', color: C.textSecondary, marginBottom: 7},
  inputWrap: {borderWidth: 1.5, borderRadius: 16, backgroundColor: C.surface},
  input: {paddingHorizontal: 16, paddingVertical: 13, fontSize: 15, color: C.textPrimary},
});
