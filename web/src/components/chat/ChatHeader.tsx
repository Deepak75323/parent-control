import {useEffect, useId, useRef, useState} from 'react';
import {Avatar} from '../Avatar';
import type {ChatRoomSummary} from '../../types/api';

type HeaderAction = {
  id: string;
  label: string;
  danger?: boolean;
  disabled?: boolean;
  onSelect: () => void;
};

type Props = {
  room: ChatRoomSummary;
  showInvite?: boolean;
  onToggleInvite?: () => void;
  onToggleSearch?: () => void;
  searchOpen?: boolean;
  menuActions: HeaderAction[];
  busy?: boolean;
  infoPanelOpen?: boolean;
  onOpenInfoPanel?: () => void;
  showInfoPanelToggle?: boolean;
};

export function ChatHeader({
  room,
  showInvite = false,
  onToggleInvite,
  onToggleSearch,
  searchOpen = false,
  menuActions,
  busy = false,
  infoPanelOpen = true,
  onOpenInfoPanel,
  showInfoPanelToggle = false,
}: Props) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuId = useId();
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    function handlePointer(event: MouseEvent) {
      if (!menuRef.current?.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handlePointer);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handlePointer);
      document.removeEventListener('keydown', handleKey);
    };
  }, [menuOpen]);

  return (
    <header className="chat-thread-head">
      <div className="chat-thread-title">
        <Avatar name={room.name} seed={room.id} size="lg" />
        <div className="chat-thread-title-text">
          <h2>{room.name}</h2>
          <div className="chat-thread-subtitle">
            <span className="chat-role-badge">Parent</span>
            <span className="chat-thread-members muted tiny">
              {room.memberCount} member{room.memberCount === 1 ? '' : 's'}
              {room.owner ? ' · you own this room' : ''}
            </span>
          </div>
        </div>
      </div>
      <div className="chat-thread-toolbar">
        {showInfoPanelToggle && !infoPanelOpen && onOpenInfoPanel ? (
          <button
            type="button"
            className="chat-icon-btn"
            aria-label="Show parent chat help"
            title="Parent chat help"
            disabled={busy}
            onClick={onOpenInfoPanel}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
              <circle cx="12" cy="12" r="9" />
              <path d="M12 10v6M12 7h.01" />
            </svg>
          </button>
        ) : null}
        {onToggleSearch ? (
          <button
            type="button"
            className={`chat-icon-btn${searchOpen ? ' active' : ''}`}
            aria-label="Search messages"
            aria-pressed={searchOpen}
            disabled={busy}
            onClick={onToggleSearch}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
              <circle cx="11" cy="11" r="7" />
              <path d="M20 20l-3.5-3.5" />
            </svg>
          </button>
        ) : null}
        <div className="chat-header-menu" ref={menuRef}>
          <button
            type="button"
            className="chat-icon-btn"
            aria-label="More options"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            aria-controls={menuId}
            disabled={busy}
            onClick={() => setMenuOpen(prev => !prev)}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
              <circle cx="12" cy="5" r="2" />
              <circle cx="12" cy="12" r="2" />
              <circle cx="12" cy="19" r="2" />
            </svg>
          </button>
          {menuOpen ? (
            <div className="chat-header-dropdown" id={menuId} role="menu">
              {showInfoPanelToggle && !infoPanelOpen && onOpenInfoPanel ? (
                <button
                  type="button"
                  role="menuitem"
                  className="chat-header-dropdown-item"
                  onClick={() => {
                    setMenuOpen(false);
                    onOpenInfoPanel();
                  }}>
                  Show parent chat help
                </button>
              ) : null}
              {onToggleInvite ? (
                <button
                  type="button"
                  role="menuitem"
                  className="chat-header-dropdown-item"
                  onClick={() => {
                    setMenuOpen(false);
                    onToggleInvite();
                  }}>
                  {showInvite ? 'Hide invite' : 'Invite parent'}
                </button>
              ) : null}
              {menuActions.map(action => (
                <button
                  key={action.id}
                  type="button"
                  role="menuitem"
                  className={`chat-header-dropdown-item${action.danger ? ' danger' : ''}`}
                  disabled={action.disabled}
                  onClick={() => {
                    setMenuOpen(false);
                    action.onSelect();
                  }}>
                  {action.label}
                </button>
              ))}
            </div>
          ) : null}
        </div>
      </div>
    </header>
  );
}
