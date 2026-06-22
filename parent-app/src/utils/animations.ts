import {useEffect, useRef} from 'react';
import {Animated, Easing} from 'react-native';

/** Fade + slide up on mount. Returns animated style. */
export function useFadeSlideIn(delay = 0) {
  const opacity = useRef(new Animated.Value(0)).current;
  const translateY = useRef(new Animated.Value(24)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(opacity, {
        toValue: 1,
        duration: 450,
        delay,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.timing(translateY, {
        toValue: 0,
        duration: 450,
        delay,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
    ]).start();
  }, [opacity, translateY, delay]);

  return {opacity, transform: [{translateY}]};
}

/** Scale spring on press (use with Pressable). */
export function usePressScale(toScale = 0.96) {
  const scale = useRef(new Animated.Value(1)).current;
  const onPressIn = () =>
    Animated.spring(scale, {toValue: toScale, useNativeDriver: true, speed: 30, bounciness: 4}).start();
  const onPressOut = () =>
    Animated.spring(scale, {toValue: 1, useNativeDriver: true, speed: 30, bounciness: 4}).start();
  return {scale: [{scale}], onPressIn, onPressOut};
}

/** Infinite slow pulse — use for status rings. */
export function usePulse(minScale = 0.92, maxScale = 1.08, duration = 1400) {
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

/** Fade in opacity loop (shimmer / breathing). */
export function useOpacityPulse(min = 0.5, max = 1, duration = 900) {
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

/** Stagger fade-in for list items. */
export function useStaggerFadeIn(count: number, stagger = 60) {
  const anims = useRef(Array.from({length: count}, () => new Animated.Value(0))).current;
  useEffect(() => {
    Animated.stagger(
      stagger,
      anims.map(a =>
        Animated.timing(a, {toValue: 1, duration: 350, easing: Easing.out(Easing.quad), useNativeDriver: true}),
      ),
    ).start();
  }, [anims, stagger]);
  return anims;
}
