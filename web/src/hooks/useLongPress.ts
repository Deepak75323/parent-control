import {useCallback, useEffect, useRef} from 'react';

const DEFAULT_DELAY_MS = 450;
const MOVE_TOLERANCE_PX = 10;

type Options = {
  enabled?: boolean;
  delayMs?: number;
  onLongPress: () => void;
};

export function useLongPress({enabled = true, delayMs = DEFAULT_DELAY_MS, onLongPress}: Options) {
  const timeoutRef = useRef<number | null>(null);
  const triggeredRef = useRef(false);
  const startXRef = useRef(0);
  const startYRef = useRef(0);

  const clear = useCallback(() => {
    if (timeoutRef.current != null) {
      window.clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  }, []);

  useEffect(() => clear, [clear]);

  const begin = useCallback(
    (clientX: number, clientY: number) => {
      if (!enabled) {
        return;
      }
      triggeredRef.current = false;
      startXRef.current = clientX;
      startYRef.current = clientY;
      clear();
      timeoutRef.current = window.setTimeout(() => {
        triggeredRef.current = true;
        onLongPress();
      }, delayMs);
    },
    [clear, delayMs, enabled, onLongPress],
  );

  const movedTooFar = useCallback((clientX: number, clientY: number) => {
    return (
      Math.abs(clientX - startXRef.current) > MOVE_TOLERANCE_PX ||
      Math.abs(clientY - startYRef.current) > MOVE_TOLERANCE_PX
    );
  }, []);

  const onTouchStart = useCallback(
    (event: React.TouchEvent) => {
      const touch = event.touches[0];
      if (!touch) {
        return;
      }
      begin(touch.clientX, touch.clientY);
    },
    [begin],
  );

  const onTouchEnd = useCallback(() => {
    clear();
  }, [clear]);

  const onTouchMove = useCallback(
    (event: React.TouchEvent) => {
      const touch = event.touches[0];
      if (!touch) {
        return;
      }
      if (movedTooFar(touch.clientX, touch.clientY)) {
        clear();
      }
    },
    [clear, movedTooFar],
  );

  const onMouseDown = useCallback(
    (event: React.MouseEvent) => {
      if (event.button !== 0) {
        return;
      }
      begin(event.clientX, event.clientY);
    },
    [begin],
  );

  const onMouseUp = useCallback(() => {
    clear();
  }, [clear]);

  const onMouseLeave = useCallback(() => {
    clear();
  }, [clear]);

  const onMouseMove = useCallback(
    (event: React.MouseEvent) => {
      if (movedTooFar(event.clientX, event.clientY)) {
        clear();
      }
    },
    [clear, movedTooFar],
  );

  const onContextMenu = useCallback(
    (event: React.MouseEvent) => {
      if (!enabled) {
        return;
      }
      event.preventDefault();
      clear();
      triggeredRef.current = true;
      onLongPress();
    },
    [clear, enabled, onLongPress],
  );

  const consumeLongPress = useCallback(() => triggeredRef.current, []);

  return {
    onTouchStart,
    onTouchEnd,
    onTouchMove,
    onTouchCancel: onTouchEnd,
    onMouseDown,
    onMouseUp,
    onMouseLeave,
    onMouseMove,
    onContextMenu,
    consumeLongPress,
  };
}
