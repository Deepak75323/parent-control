import {useEffect, useRef} from 'react';
import {Animated, Easing} from 'react-native';

export function useFadeSlideIn(delay = 0) {
  const opacity = useRef(new Animated.Value(0)).current;
  const translateY = useRef(new Animated.Value(28)).current;
  useEffect(() => {
    Animated.parallel([
      Animated.timing(opacity, {toValue: 1, duration: 500, delay, easing: Easing.out(Easing.cubic), useNativeDriver: true}),
      Animated.timing(translateY, {toValue: 0, duration: 500, delay, easing: Easing.out(Easing.cubic), useNativeDriver: true}),
    ]).start();
  }, [opacity, translateY, delay]);
  return {opacity, transform: [{translateY}]};
}

export function usePulse(minScale = 0.9, maxScale = 1.1, duration = 1500) {
  const anim = useRef(new Animated.Value(minScale)).current;
  useEffect(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(anim, {toValue: maxScale, duration, easing: Easing.inOut(Easing.ease), useNativeDriver: true}),
        Animated.timing(anim, {toValue: minScale, duration, easing: Easing.inOut(Easing.ease), useNativeDriver: true}),
      ]),
    ).start();
    return () => anim.stopAnimation();
  }, [anim, minScale, maxScale, duration]);
  return [{scale: anim}];
}

export function useOpacityPulse(min = 0.3, max = 1, duration = 1000) {
  const anim = useRef(new Animated.Value(min)).current;
  useEffect(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(anim, {toValue: max, duration, easing: Easing.inOut(Easing.ease), useNativeDriver: true}),
        Animated.timing(anim, {toValue: min, duration, easing: Easing.inOut(Easing.ease), useNativeDriver: true}),
      ]),
    ).start();
    return () => anim.stopAnimation();
  }, [anim, min, max, duration]);
  return anim;
}

export function usePressScale(toScale = 0.96) {
  const scale = useRef(new Animated.Value(1)).current;
  const onPressIn = () =>
    Animated.spring(scale, {toValue: toScale, useNativeDriver: true, speed: 30, bounciness: 4}).start();
  const onPressOut = () =>
    Animated.spring(scale, {toValue: 1, useNativeDriver: true, speed: 30, bounciness: 4}).start();
  return {scale: [{scale}], onPressIn, onPressOut};
}
