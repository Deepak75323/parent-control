import {useCallback, useEffect, useRef} from 'react';

const DEFAULT_DELAY_MS = 450;

type Options = {
  enabled?: boolean;
  delayMs?: number;
  onLongPress: () => void;
};

export function useLongPress({enabled = true, delayMs = DEFAULT_DELAY_MS, onLongPress}: Options) {
  const timeoutRef = useRef<number | null>(null);
  const triggeredRef = useRef(false);

  const clear = useCallback(() => {
    if (timeoutRef.current != null) {
      window.clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  }, []);

  useEffect(() => clear, [clear]);

  const onTouchStart = useCallback(() => {
    if (!enabled) {
      return;
    }
    triggeredRef.current = false;
    clear();
    timeoutRef.current = window.setTimeout(() => {
      triggeredRef.current = true;
      onLongPress();
    }, delayMs);
  }, [clear, delayMs, enabled, onLongPress]);

  const onTouchEnd = useCallback(() => {
    clear();
  }, [clear]);

  const onTouchMove = useCallback(() => {
    clear();
  }, [clear]);

  const consumeLongPress = useCallback(() => triggeredRef.current, []);

  return {
    onTouchStart,
    onTouchEnd,
    onTouchMove,
    onTouchCancel: onTouchEnd,
    consumeLongPress,
  };
}
