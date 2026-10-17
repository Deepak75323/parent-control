import {useCallback, useRef, useState} from 'react';

const DEFAULT_THRESHOLD = 52;
const MAX_OFFSET = 72;

type Options = {
  enabled: boolean;
  onAction: () => void;
  threshold?: number;
};

/** Swipe a room row left to delete or leave (mobile / touch). */
export function useRoomSwipeAction({
  enabled,
  onAction,
  threshold = DEFAULT_THRESHOLD,
}: Options) {
  const [offset, setOffset] = useState(0);
  const [active, setActive] = useState(false);
  const startXRef = useRef(0);
  const startYRef = useRef(0);
  const axisRef = useRef<'x' | 'y' | null>(null);

  const reset = useCallback(() => {
    setOffset(0);
    setActive(false);
    axisRef.current = null;
  }, []);

  const onTouchStart = useCallback(
    (event: React.TouchEvent) => {
      if (!enabled) {
        return;
      }
      const touch = event.touches[0];
      if (!touch) {
        return;
      }
      startXRef.current = touch.clientX;
      startYRef.current = touch.clientY;
      axisRef.current = null;
      setActive(true);
    },
    [enabled],
  );

  const onTouchMove = useCallback(
    (event: React.TouchEvent) => {
      if (!enabled || !active) {
        return;
      }
      const touch = event.touches[0];
      if (!touch) {
        return;
      }
      const deltaX = touch.clientX - startXRef.current;
      const deltaY = touch.clientY - startYRef.current;

      if (!axisRef.current) {
        if (Math.abs(deltaX) < 8 && Math.abs(deltaY) < 8) {
          return;
        }
        axisRef.current = Math.abs(deltaX) > Math.abs(deltaY) ? 'x' : 'y';
      }

      if (axisRef.current !== 'x' || deltaX > 0) {
        return;
      }

      event.preventDefault();
      setOffset(Math.max(deltaX, -MAX_OFFSET));
    },
    [active, enabled],
  );

  const onTouchEnd = useCallback(() => {
    if (!enabled) {
      return;
    }
    if (offset <= -threshold) {
      onAction();
    }
    reset();
  }, [enabled, offset, onAction, reset, threshold]);

  const onTouchCancel = useCallback(() => {
    reset();
  }, [reset]);

  return {
    offset,
    swiping: active && offset < 0,
    onTouchStart,
    onTouchMove,
    onTouchEnd,
    onTouchCancel,
  };
}
