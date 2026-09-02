import {type RefObject, useEffect, useId, useMemo, useRef, useState} from 'react';
import {
  CHAT_EMOJI_CATEGORIES,
  defaultEmojiCategoryId,
  loadRecentEmojis,
  rememberEmoji,
  type EmojiCategory,
} from '../../lib/chatEmojiCatalog';

type Props = {
  open: boolean;
  onClose: () => void;
  onSelect: (emoji: string) => void;
  ignoreCloseRef?: RefObject<HTMLElement | null>;
};

export function EmojiPicker({open, onClose, onSelect, ignoreCloseRef}: Props) {
  const menuId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const [recentEmojis, setRecentEmojis] = useState<string[]>(() => loadRecentEmojis());
  const [activeCategoryId, setActiveCategoryId] = useState(() =>
    defaultEmojiCategoryId(loadRecentEmojis()),
  );

  useEffect(() => {
    if (!open) {
      return;
    }
    const recent = loadRecentEmojis();
    setRecentEmojis(recent);
    setActiveCategoryId(defaultEmojiCategoryId(recent));
  }, [open]);

  const categories = useMemo(() => {
    const recentCategory: EmojiCategory | null =
      recentEmojis.length > 0
        ? {id: 'recent', label: 'Recent', icon: '🕘', emojis: recentEmojis}
        : null;
    return recentCategory ? [recentCategory, ...CHAT_EMOJI_CATEGORIES] : CHAT_EMOJI_CATEGORIES;
  }, [recentEmojis]);

  const activeCategory =
    categories.find(category => category.id === activeCategoryId) ?? categories[0];

  useEffect(() => {
    if (!open) {
      return;
    }
    function handlePointer(event: MouseEvent) {
      const target = event.target as Node;
      if (rootRef.current?.contains(target) || ignoreCloseRef?.current?.contains(target)) {
        return;
      }
      onClose();
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('mousedown', handlePointer);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handlePointer);
      document.removeEventListener('keydown', handleKey);
    };
  }, [open, onClose, ignoreCloseRef]);

  if (!open || !activeCategory) {
    return null;
  }

  function handleSelect(emoji: string) {
    setRecentEmojis(rememberEmoji(emoji));
    onSelect(emoji);
  }

  return (
    <div
      className="chat-emoji-picker"
      id={menuId}
      ref={rootRef}
      role="dialog"
      aria-label="Choose emoji">
      <div className="chat-emoji-picker-tabs" role="tablist" aria-label="Emoji categories">
        {categories.map(category => (
          <button
            key={category.id}
            type="button"
            role="tab"
            aria-selected={category.id === activeCategory.id}
            aria-label={category.label}
            className={[
              'chat-emoji-picker-tab',
              category.id === activeCategory.id ? 'chat-emoji-picker-tab--active' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            onClick={() => setActiveCategoryId(category.id)}>
            {category.icon}
          </button>
        ))}
      </div>
      <div className="chat-emoji-picker-grid" role="tabpanel" aria-label={activeCategory.label}>
        {activeCategory.emojis.map((emoji, index) => (
          <button
            key={`${activeCategory.id}-${index}-${emoji}`}
            type="button"
            className="chat-emoji-picker-item"
            aria-label={`Insert ${emoji}`}
            onClick={() => handleSelect(emoji)}>
            {emoji}
          </button>
        ))}
      </div>
    </div>
  );
}
