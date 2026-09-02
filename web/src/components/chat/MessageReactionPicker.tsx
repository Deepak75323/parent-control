import type {RefObject} from 'react';
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
  return (
    <ComposeAnchorPortal open={open} anchorRef={anchorRef} className="chat-reaction-picker-portal">
      <div className="chat-reaction-picker" role="toolbar" aria-label="React to message">
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
  );
}
