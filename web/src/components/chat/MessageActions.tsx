import {useEffect, useId, useRef, useState} from 'react';

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
  const menuId = useId();
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    function handlePointer(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handlePointer);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handlePointer);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open]);

  if (actions.length === 0) {
    return null;
  }

  return (
    <div className="chat-message-actions-menu" ref={rootRef}>
      <button
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
      {open ? (
        <div className="chat-message-dropdown" id={menuId} role="menu">
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
              {action.icon ? <span className="chat-message-dropdown-icon" aria-hidden>{action.icon}</span> : null}
              {action.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
