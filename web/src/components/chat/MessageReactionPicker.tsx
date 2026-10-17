import {type RefObject, useEffect, useRef} from 'react';
import {createPortal} from 'react-dom';
import {ComposeAnchorPortal} from './ComposeAnchorPortal';
import {CHAT_EMOJI_CATEGORIES} from '../../lib/chatEmojiCatalog';
import {CHAT_QUICK_REACTION_EMOJIS} from '../../lib/chatReactionEmojis';

type Props = {
  open: boolean;
  anchorRef: RefObject<HTMLElement | null>;
  onClose: () => void;
  onSelect: (emoji: string) => void;
};

const MORE_EMOJIS = CHAT_EMOJI_CATEGORIES.flatMap(category => category.emojis).slice(0, 48);

export function MessageReactionPicker({open, anchorRef, onClose, onSelect}: Props) {
  const pickerRef = useRef<HTMLDivElement>(null);
  const openedAtRef = useRef(0);

  useEffect(() => {
    if (open) {
      openedAtRef.current = Date.now();
    }
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    function handlePointer(event: MouseEvent | TouchEvent) {
      if (Date.now() - openedAtRef.current < 120) {
        return;
      }
      const target = event.target;
      if (!(target instanceof Node)) {
        return;
      }
      if (pickerRef.current?.contains(target)) {
        return;
      }
      if (anchorRef.current?.contains(target)) {
        return;
      }
      onClose();
    }
    document.addEventListener('keydown', handleKey);
    document.addEventListener('mousedown', handlePointer);
    document.addEventListener('touchstart', handlePointer, {passive: true});
    return () => {
      document.removeEventListener('keydown', handleKey);
      document.removeEventListener('mousedown', handlePointer);
      document.removeEventListener('touchstart', handlePointer);
    };
  }, [open, onClose, anchorRef]);

  const backdrop =
    open &&
    createPortal(
      <button
        type="button"
        className="chat-reaction-picker-backdrop"
        aria-label="Dismiss reactions"
        onClick={onClose}
      />,
      document.body,
    );

  return (
    <>
      {backdrop}
      <ComposeAnchorPortal open={open} anchorRef={anchorRef} className="chat-reaction-picker-portal">
        <div ref={pickerRef} className="chat-reaction-picker" role="toolbar" aria-label="React to message">
        {CHAT_QUICK_REACTION_EMOJIS.map(emoji => (
          <button
            key={emoji}
            type="button"
            className="chat-reaction-picker-emoji"
            aria-label={`React with ${emoji}`}
            onClick={() => {
              onSelect(emoji);
              onClose();
            }}>
            {emoji}
          </button>
        ))}
        <details className="chat-reaction-picker-more">
          <summary className="chat-reaction-picker-more-toggle" aria-label="More reactions">
            +
          </summary>
          <div className="chat-reaction-picker-more-grid">
            {MORE_EMOJIS.map(emoji => (
              <button
                key={emoji}
                type="button"
                className="chat-reaction-picker-emoji chat-reaction-picker-emoji--compact"
                aria-label={`React with ${emoji}`}
                onClick={() => {
                  onSelect(emoji);
                  onClose();
                }}>
                {emoji}
              </button>
            ))}
          </div>
        </details>
        </div>
      </ComposeAnchorPortal>
    </>
  );
}
