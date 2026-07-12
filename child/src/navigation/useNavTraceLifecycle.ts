import {useCallback, useEffect} from 'react';
import {useFocusEffect} from '@react-navigation/native';
import {navTraceLifecycle, type NavLifecycle} from './navTrace';

/**
 * Maps React / React Navigation screen hooks to Android-style lifecycle logs.
 * Logging only — no navigation side effects.
 */
export function useNavTraceLifecycle(
  screen: string,
  extra?: () => Record<string, unknown>,
): void {
  useEffect(() => {
    navTraceLifecycle(screen, 'onCreate', extra?.());
    navTraceLifecycle(screen, 'onStart');
    return () => {
      navTraceLifecycle(screen, 'onPause');
      navTraceLifecycle(screen, 'onStop');
      navTraceLifecycle(screen, 'onDestroy');
    };
  }, [screen]);

  useFocusEffect(
    useCallback(() => {
      navTraceLifecycle(screen, 'onResume', extra?.());
      return () => navTraceLifecycle(screen, 'onPause', {blur: true});
    }, [screen]),
  );
}

export function logNavToolbarBack(screen: string, expectedDestination: string): void {
  navTraceLifecycle(screen, 'onNewIntent' as NavLifecycle, {
    toolbarBackClicked: true,
    expectedDestination,
  });
}
