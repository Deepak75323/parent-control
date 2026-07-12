import React, {useRef, useState} from 'react';
import {Animated, View, Text, TextInput, StyleSheet, TextInputProps} from 'react-native';
import {Colors, Layout, Radii, Spacing, Typography} from '../theme/design';

type Props = TextInputProps & {label: string};

export function AuthField({label, style, ...rest}: Props) {
  const borderAnim = useRef(new Animated.Value(0)).current;
  const borderColor = borderAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [Colors.border, Colors.primaryContainer],
  });

  return (
    <View style={s.wrap}>
      <Text style={s.label}>{label}</Text>
      <Animated.View style={[s.inputWrap, {borderColor}]}>
        <TextInput
          style={[s.input, style]}
          placeholderTextColor={Colors.textTertiary}
          onFocus={() => Animated.timing(borderAnim, {toValue: 1, duration: 180, useNativeDriver: false}).start()}
          onBlur={() => Animated.timing(borderAnim, {toValue: 0, duration: 180, useNativeDriver: false}).start()}
          {...rest}
        />
      </Animated.View>
    </View>
  );
}

const s = StyleSheet.create({
  wrap: {marginBottom: Layout.componentInternal},
  label: {...Typography.smallLabel, color: Colors.textSecondary, marginBottom: Spacing.sm},
  inputWrap: {borderWidth: 1, borderRadius: Radii.md, backgroundColor: Colors.background, minHeight: Layout.touchTargetMin, justifyContent: 'center'},
  input: {paddingHorizontal: Layout.componentInternal, paddingVertical: Spacing.md, fontSize: 15, color: Colors.textPrimary},
});
