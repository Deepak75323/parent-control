import {useEffect, useId, useRef, useState} from 'react';
import {Avatar} from '../Avatar';
import type {ChatRoomSummary} from '../../types/api';
import {isNotesServerName, NotesServerIcon} from './NotesServerIcon';

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
  showRoomsPanelToggle?: boolean;
  onOpenRoomsPanel?: () => void;
  showBack?: boolean;
  onBack?: () => void;
  showRoomsMenu?: boolean;
  onOpenRooms?: () => void;
  compact?: boolean;
  channelLabel?: string | null;
  onVoiceCall?: () => void;
  onVideoCall?: () => void;
  callDisabled?: boolean;
  /** DM-only partner presence label (Online / Last seen). */
  partnerPresenceLabel?: string | null;
  partnerOnline?: boolean;
};

export function ChatHeader({
  room,
  channelLabel = null,
  showInvite = false,
  onToggleInvite,
  onToggleSearch,
  searchOpen = false,
  menuActions,
  busy = false,
  infoPanelOpen = true,
  onOpenInfoPanel,
  showInfoPanelToggle = false,
  showRoomsPanelToggle = false,
  onOpenRoomsPanel,
  showBack = false,
  onBack,
  showRoomsMenu = false,
  onOpenRooms,
  compact = false,
  onVoiceCall,
  onVideoCall,
  callDisabled = false,
  partnerPresenceLabel = null,
  partnerOnline = false,
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
    <header className={`chat-thread-head${compact ? ' chat-thread-head--compact' : ''}`}>
      <div className="chat-thread-title">
        {showBack && onBack ? (
          <button
            type="button"
            className="chat-icon-btn chat-thread-back"
            aria-label="Back to rooms"
            title="Back to rooms"
            disabled={busy}
            onClick={onBack}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
        ) : null}
        {showRoomsMenu && onOpenRooms ? (
          <button
            type="button"
            className="chat-icon-btn chat-thread-rooms"
            aria-label="Open rooms"
            title="Rooms"
            disabled={busy}
            onClick={onOpenRooms}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
        ) : null}
        {isNotesServerName(room.name) ? (
          <span
            className={`chat-server-glyph chat-server-glyph--notes chat-server-glyph--header${
              compact ? '' : ' chat-server-glyph--lg'
            }`}
            aria-hidden>
            <NotesServerIcon size={compact ? 28 : 32} />
          </span>
        ) : (
          <Avatar name={room.name} seed={room.id} size={compact ? 'md' : 'lg'} />
        )}
        <div className="chat-thread-title-text">
          <h2>
            {channelLabel ? channelLabel : room.name}
          </h2>
          <div className="chat-thread-subtitle">
            <span className="chat-role-badge">Parent</span>
            {partnerPresenceLabel ? (
              <span
                className={`chat-partner-presence muted tiny${partnerOnline ? ' chat-partner-presence--online' : ''}`}
                aria-live="polite">
                <span
                  className={`chat-partner-presence-dot${partnerOnline ? ' online' : ''}`}
                  aria-hidden
                />
                {partnerPresenceLabel}
              </span>
            ) : (
              <span className="chat-thread-members muted tiny">
                {channelLabel ? `${room.name} · ` : ''}
                {room.memberCount} member{room.memberCount === 1 ? '' : 's'}
                {room.owner ? ' · you own this server' : ''}
              </span>
            )}
          </div>
        </div>
      </div>
      <div className="chat-thread-toolbar">
        {showRoomsPanelToggle && onOpenRoomsPanel ? (
          <button
            type="button"
            className="chat-icon-btn"
            aria-label="Show rooms panel"
            title="Show rooms"
            disabled={busy}
            onClick={onOpenRoomsPanel}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
              <path d="M4 6h16M4 12h16M4 18h10" />
            </svg>
          </button>
        ) : null}
        {showInfoPanelToggle && !infoPanelOpen && onOpenInfoPanel && !compact ? (
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
            title="Search"
            disabled={busy}
            onClick={onToggleSearch}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
              <circle cx="11" cy="11" r="7" />
              <path d="M20 20l-3.5-3.5" />
            </svg>
          </button>
        ) : null}
        {onVoiceCall ? (
          <button
            type="button"
            className="chat-icon-btn"
            aria-label="Start voice call"
            title="Voice call"
            disabled={busy || callDisabled}
            onClick={onVoiceCall}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
              <path d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2a1 1 0 0 1 1.01-.24c1.12.37 2.33.57 3.58.57a1 1 0 0 1 1 1V20a1 1 0 0 1-1 1A17 17 0 0 1 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1c0 1.25.2 2.46.57 3.58a1 1 0 0 1-.25 1.02l-2.2 2.19Z" />
            </svg>
          </button>
        ) : null}
        {onVideoCall ? (
          <button
            type="button"
            className="chat-icon-btn"
            aria-label="Start video call"
            title="Video call"
            disabled={busy || callDisabled}
            onClick={onVideoCall}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
              <path d="M17 10.5V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3.5l4 4v-11l-4 4Z" />
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
              {showRoomsPanelToggle && onOpenRoomsPanel ? (
                <button
                  type="button"
                  role="menuitem"
                  className="chat-header-dropdown-item"
                  onClick={() => {
                    setMenuOpen(false);
                    onOpenRoomsPanel();
                  }}>
                  Show rooms panel
                </button>
              ) : null}
              {showInfoPanelToggle && !infoPanelOpen && onOpenInfoPanel ? (
                <button
                  type="button"
                  role="menuitem"
                  className="chat-header-dropdown-item"
                  onClick={() => {
                    setMenuOpen(false);
                    onOpenInfoPanel();
                  }}>
                  {compact ? 'Parent chat help' : 'Show parent chat help'}
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
