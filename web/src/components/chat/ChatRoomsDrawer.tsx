import {useEffect, useRef, type ReactNode} from 'react';
import {createPortal} from 'react-dom';
import {lockBodyScroll} from '../../lib/scrollLock';

type Props = {
  open: boolean;
  onClose: () => void;
  onBackToApp?: () => void;
  children: ReactNode;
};

export function ChatRoomsDrawer({open, onClose, onBackToApp, children}: Props) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    return lockBodyScroll();
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    closeButtonRef.current?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open || typeof document === 'undefined') {
    return null;
  }

  return createPortal(
    <div className="chat-rooms-overlay" role="dialog" aria-modal="true" aria-label="Chat rooms">
      <button
        type="button"
        className="chat-rooms-backdrop"
        aria-label="Close rooms"
        onClick={onClose}
      />
      <aside className="chat-rooms-drawer" aria-label="Chat rooms">
        <div className="chat-rooms-drawer-head">
          {onBackToApp ? (
            <button
              type="button"
              className="chat-rooms-drawer-back"
              aria-label="Back to app"
              onClick={onBackToApp}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                <path d="M15 18l-6-6 6-6" />
              </svg>
              <span>App</span>
            </button>
          ) : (
            <span />
          )}
          <span className="chat-rooms-drawer-title">Rooms</span>
          <button
            ref={closeButtonRef}
            type="button"
            className="chat-rooms-drawer-close"
            aria-label="Close rooms"
            onClick={onClose}>
            ×
          </button>
        </div>
        <div className="chat-rooms-drawer-body">{children}</div>
      </aside>
    </div>,
    document.body,
  );
}
