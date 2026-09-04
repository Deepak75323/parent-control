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

const MENU_WIDTH = 180;
const MENU_ITEM_HEIGHT = 40;
const MENU_PADDING = 8;
const VIEWPORT_PAD = 8;

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
  const menuRef = useRef<HTMLDivElement>(null);

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
      const left = Math.min(
        Math.max(VIEWPORT_PAD, rect.right - MENU_WIDTH),
        window.innerWidth - MENU_WIDTH - VIEWPORT_PAD,
      );
      const estimatedHeight =
        menuRef.current?.offsetHeight ||
        actions.length * MENU_ITEM_HEIGHT + MENU_PADDING * 2;
      const spaceBelow = window.innerHeight - rect.bottom - VIEWPORT_PAD;
      const spaceAbove = rect.top - VIEWPORT_PAD;
      // Bottom-of-thread menus open upward so "Delete for everyone" stays visible.
      const openUpward = spaceBelow < estimatedHeight && spaceAbove > spaceBelow;
      const top = openUpward
        ? Math.max(VIEWPORT_PAD, rect.top - estimatedHeight - 6)
        : rect.bottom + 6;
      setMenuStyle({top, left});
    }
    updatePosition();
    // Re-measure after paint so real menu height flips correctly.
    const raf = window.requestAnimationFrame(updatePosition);
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);
    return () => {
      window.cancelAnimationFrame(raf);
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [open, actions.length]);

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
            ref={menuRef}
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
