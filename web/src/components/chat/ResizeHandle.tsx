import {useRef} from 'react';

type Props = {
  label: string;
  onDrag: (deltaX: number) => void;
  onDragEnd?: () => void;
  onDoubleClick?: () => void;
  disabled?: boolean;
};

export function ResizeHandle({label, onDrag, onDragEnd, onDoubleClick, disabled = false}: Props) {
  const draggingRef = useRef(false);

  function endDrag(target: EventTarget & Element, pointerId: number) {
    if (!draggingRef.current) {
      return;
    }
    draggingRef.current = false;
    target.releasePointerCapture(pointerId);
    document.body.classList.remove('chat-resizing');
    onDragEnd?.();
  }

  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label={label}
      className={`chat-resize-handle${disabled ? ' chat-resize-handle--disabled' : ''}`}
      onPointerDown={event => {
        if (disabled) {
          return;
        }
        event.preventDefault();
        draggingRef.current = true;
        event.currentTarget.setPointerCapture(event.pointerId);
        document.body.classList.add('chat-resizing');
      }}
      onPointerMove={event => {
        if (!draggingRef.current || disabled) {
          return;
        }
        if (event.movementX !== 0) {
          onDrag(event.movementX);
        }
      }}
      onPointerUp={event => endDrag(event.currentTarget, event.pointerId)}
      onPointerCancel={event => endDrag(event.currentTarget, event.pointerId)}
      onDoubleClick={() => {
        if (!disabled) {
          onDoubleClick?.();
        }
      }}
    />
  );
}
