export function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

export function pipDragBounds(panelWidth: number, panelHeightPx: number) {
  const viewport = typeof window !== 'undefined' ? window.visualViewport : null;
  const viewportLeft = viewport?.offsetLeft ?? 0;
  const viewportTop = viewport?.offsetTop ?? 0;
  const viewportWidth = viewport?.width ?? window.innerWidth;
  const viewportHeight = viewport?.height ?? window.innerHeight;
  const minX = viewportLeft + 8;
  const minY = viewportTop + 8;
  const maxX = Math.max(minX, viewportLeft + viewportWidth - panelWidth - 8);
  const maxY = Math.max(minY, viewportTop + viewportHeight - panelHeightPx - 8);
  return {minX, minY, maxX, maxY};
}

export function clampPipPosition(
  x: number,
  y: number,
  panelWidth: number,
  panelHeightPx: number,
): {x: number; y: number} {
  const {minX, minY, maxX, maxY} = pipDragBounds(panelWidth, panelHeightPx);
  return {
    x: clamp(x, minX, maxX),
    y: clamp(y, minY, maxY),
  };
}

export function isPipDragBlockedTarget(target: EventTarget | null): boolean {
  return Boolean(
    target instanceof Element &&
      target.closest(
        'button, a, input, select, textarea, .btn, .stream-pip-resize, [data-pip-no-drag]',
      ),
  );
}

type PipDragSessionOptions = {
  startX: number;
  startY: number;
  originX: number;
  originY: number;
  pointerId?: number;
  captureTarget?: HTMLElement | null;
  onMove: (clientX: number, clientY: number) => void;
  onEnd: () => void;
};

export type PipDragSession = {
  cleanup: () => void;
};

/** Attaches move/end listeners immediately (do not wait for a React effect). */
export function startPipDragSession(options: PipDragSessionOptions): PipDragSession {
  const state = {
    startX: options.startX,
    startY: options.startY,
  };
  let ended = false;

  const move = (clientX: number, clientY: number) => {
    options.onMove(clientX - state.startX, clientY - state.startY);
  };

  const onPointerMove = (event: PointerEvent) => {
    if (options.pointerId != null && event.pointerId !== options.pointerId) {
      return;
    }
    event.preventDefault();
    move(event.clientX, event.clientY);
  };

  const onTouchMove = (event: TouchEvent) => {
    if (event.touches.length !== 1) {
      return;
    }
    event.preventDefault();
    const touch = event.touches[0];
    move(touch.clientX, touch.clientY);
  };

  const end = () => {
    if (ended) {
      return;
    }
    ended = true;
    window.removeEventListener('pointermove', onPointerMove);
    window.removeEventListener('pointerup', end);
    window.removeEventListener('pointercancel', end);
    window.removeEventListener('touchmove', onTouchMove);
    window.removeEventListener('touchend', end);
    window.removeEventListener('touchcancel', end);
    document.body.classList.remove('stream-pip-dragging');
    if (options.captureTarget != null && options.pointerId != null) {
      try {
        options.captureTarget.releasePointerCapture(options.pointerId);
      } catch {
        /* ignore */
      }
    }
    options.onEnd();
  };

  document.body.classList.add('stream-pip-dragging');
  window.addEventListener('pointermove', onPointerMove, {passive: false});
  window.addEventListener('pointerup', end);
  window.addEventListener('pointercancel', end);
  window.addEventListener('touchmove', onTouchMove, {passive: false});
  window.addEventListener('touchend', end);
  window.addEventListener('touchcancel', end);

  if (options.captureTarget != null && options.pointerId != null) {
    try {
      options.captureTarget.setPointerCapture(options.pointerId);
    } catch {
      /* ignore */
    }
  }

  return {cleanup: end};
}
