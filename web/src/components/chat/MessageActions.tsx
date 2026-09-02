import {useEffect, useId, useLayoutEffect, useRef, useState} from 'react';
import {createPortal} from 'react-dom';

export type MessageAction = {
  id: string;
  label: string;
  icon?: string;
  danger?: boolean;
  disabled?: boolean;
  onSelect: () => void;
};

type Props = {
  actions: MessageAction[];
  disabled?: boolean;
};

export function MessageActions({actions, disabled = false}: Props) {
  const [open, setOpen] = useState(false);
  const [menuStyle, setMenuStyle] = useState<{top: number; left: number} | null>(null);
  const menuId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  useLayoutEffect(() => {
    if (!open) {
      setMenuStyle(null);
      return;
    }
    function updatePosition() {
      const trigger = triggerRef.current;
      if (!trigger) {
        return;
      }
      const rect = trigger.getBoundingClientRect();
      const menuWidth = 180;
      const horizontalPadding = 8;
      const left = Math.min(
        Math.max(horizontalPadding, rect.right - menuWidth),
        window.innerWidth - menuWidth - horizontalPadding,
      );
      setMenuStyle({
        top: rect.bottom + 6,
        left,
      });
    }
    updatePosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    function handlePointer(event: MouseEvent | TouchEvent) {
      const target = event.target;
      if (!(target instanceof Node)) {
        return;
      }
      if (rootRef.current?.contains(target)) {
        return;
      }
      if (target instanceof Element && target.closest('.chat-message-dropdown-portal')) {
        return;
      }
      setOpen(false);
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handlePointer);
    document.addEventListener('touchstart', handlePointer, {passive: true});
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handlePointer);
      document.removeEventListener('touchstart', handlePointer);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);

  if (actions.length === 0) {
    return null;
  }

  const menu =
    open && menuStyle
      ? createPortal(
          <div
            className="chat-message-dropdown chat-message-dropdown-portal"
            id={menuId}
            role="menu"
            style={{top: menuStyle.top, left: menuStyle.left}}>
            {actions.map(action => (
              <button
                key={action.id}
                type="button"
                role="menuitem"
                className={`chat-message-dropdown-item${action.danger ? ' danger' : ''}`}
                disabled={action.disabled}
                onClick={() => {
                  setOpen(false);
                  action.onSelect();
                }}>
                {action.icon ? (
                  <span className="chat-message-dropdown-icon" aria-hidden>
                    {action.icon}
                  </span>
                ) : null}
                {action.label}
              </button>
            ))}
          </div>,
          document.body,
        )
      : null;

  return (
    <div className="chat-message-actions-menu" ref={rootRef}>
      <button
        ref={triggerRef}
        type="button"
        className="chat-message-overflow"
        aria-label="Message actions"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        disabled={disabled}
        onClick={() => setOpen(prev => !prev)}>
        <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor" aria-hidden>
          <circle cx="8" cy="3" r="1.5" />
          <circle cx="8" cy="8" r="1.5" />
          <circle cx="8" cy="13" r="1.5" />
        </svg>
      </button>
      {menu}
    </div>
  );
}
