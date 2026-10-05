import {type RefObject, useEffect, useId, useMemo, useRef, useState} from 'react';
import {
  CHAT_EMOJI_CATEGORIES,
  defaultEmojiCategoryId,
  loadRecentEmojis,
  rememberEmoji,
  type EmojiCategory,
} from '../../lib/chatEmojiCatalog';
import {
  MERGED_EMOJI_FILTER_TAGS,
  addCustomMergedEmoji,
  allMergedEmojiCollections,
  filterMergedEmojiItems,
  loadCustomMergedEmojis,
  previewMergedEmoji,
  removeCustomMergedEmoji,
  type MergedEmojiArt,
} from '../../lib/chatMergedEmojiCatalog';
import {CHAT_MESSAGE_MAX_LENGTH} from '../../lib/chatMessageLimits';

type PickerMode = 'emoji' | 'merged' | 'add';

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
  const [mode, setMode] = useState<PickerMode>('emoji');
  const [mergedTag, setMergedTag] = useState<string>('all');
  const [customItems, setCustomItems] = useState<MergedEmojiArt[]>(() => loadCustomMergedEmojis());
  const [addLabel, setAddLabel] = useState('');
  const [addArt, setAddArt] = useState('');
  const [addError, setAddError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const recent = loadRecentEmojis();
    setRecentEmojis(recent);
    setActiveCategoryId(defaultEmojiCategoryId(recent));
    setCustomItems(loadCustomMergedEmojis());
    setMode('emoji');
    setAddError(null);
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

  const mergedCollections = useMemo(
    () => allMergedEmojiCollections(customItems),
    [customItems],
  );

  const mergedItems = useMemo(
    () =>
      filterMergedEmojiItems(
        mergedCollections,
        mergedTag === 'all' ? null : mergedTag,
      ),
    [mergedCollections, mergedTag],
  );

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

  function handleSelectEmoji(emoji: string) {
    setRecentEmojis(rememberEmoji(emoji));
    onSelect(emoji);
  }

  function handleSelectMerged(art: MergedEmojiArt) {
    onSelect(art.art);
    onClose();
  }

  function handleSaveCollection() {
    setAddError(null);
    try {
      const saved = addCustomMergedEmoji({
        label: addLabel,
        art: addArt,
        tags: ['my collection', 'cute'],
      });
      setCustomItems(loadCustomMergedEmojis());
      setAddLabel('');
      setAddArt('');
      setMergedTag('my collection');
      setMode('merged');
      onSelect(saved.art);
      onClose();
    } catch (error) {
      setAddError(error instanceof Error ? error.message : 'Could not save collection');
    }
  }

  return (
    <div
      className="chat-emoji-picker"
      id={menuId}
      ref={rootRef}
      role="dialog"
      aria-label="Choose emoji">
      <div className="chat-emoji-picker-modes" role="tablist" aria-label="Emoji picker modes">
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'emoji'}
          className={mode === 'emoji' ? 'chat-emoji-picker-mode--active' : undefined}
          onClick={() => setMode('emoji')}>
          Emoji
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'merged'}
          className={mode === 'merged' ? 'chat-emoji-picker-mode--active' : undefined}
          onClick={() => setMode('merged')}>
          Merged emoji
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'add'}
          className={mode === 'add' ? 'chat-emoji-picker-mode--active' : undefined}
          onClick={() => setMode('add')}>
          Add collection
        </button>
      </div>

      {mode === 'emoji' ? (
        <>
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
                onClick={() => handleSelectEmoji(emoji)}>
                {emoji}
              </button>
            ))}
          </div>
        </>
      ) : null}

      {mode === 'merged' ? (
        <div className="chat-merged-emoji" role="tabpanel" aria-label="Merged emoji">
          <div className="chat-merged-emoji-tags" role="toolbar" aria-label="Merged emoji filters">
            {MERGED_EMOJI_FILTER_TAGS.map(tag => (
              <button
                key={tag}
                type="button"
                className={mergedTag === tag ? 'active' : undefined}
                onClick={() => setMergedTag(tag)}>
                {tag}
              </button>
            ))}
          </div>
          <div className="chat-merged-emoji-list">
            {mergedItems.length === 0 ? (
              <p className="chat-merged-emoji-empty">No merged emoji in this collection yet.</p>
            ) : (
              mergedItems.map(item => (
                <div key={item.id} className="chat-merged-emoji-row">
                  <button
                    type="button"
                    className="chat-merged-emoji-item"
                    aria-label={`Insert ${item.label}`}
                    onClick={() => handleSelectMerged(item)}>
                    <span className="chat-merged-emoji-label">{item.label}</span>
                    <span className="chat-merged-emoji-preview">{previewMergedEmoji(item.art)}</span>
                  </button>
                  {item.id.startsWith('custom-') ? (
                    <button
                      type="button"
                      className="chat-merged-emoji-remove"
                      aria-label={`Remove ${item.label}`}
                      onClick={() => {
                        removeCustomMergedEmoji(item.id);
                        setCustomItems(loadCustomMergedEmojis());
                      }}>
                      ×
                    </button>
                  ) : null}
                </div>
              ))
            )}
          </div>
        </div>
      ) : null}

      {mode === 'add' ? (
        <div className="chat-merged-emoji-add" role="tabpanel" aria-label="Add emoji collection">
          <label className="chat-merged-emoji-add-field">
            <span>Name</span>
            <input
              value={addLabel}
              onChange={event => setAddLabel(event.target.value)}
              placeholder="Golden lily"
              maxLength={64}
            />
          </label>
          <label className="chat-merged-emoji-add-field">
            <span>Paste merged emoji art</span>
            <textarea
              value={addArt}
              onChange={event => setAddArt(event.target.value.slice(0, CHAT_MESSAGE_MAX_LENGTH))}
              placeholder="Paste emoji art here…"
              rows={6}
              maxLength={CHAT_MESSAGE_MAX_LENGTH}
            />
          </label>
          <p className="chat-merged-emoji-add-hint">
            Up to {CHAT_MESSAGE_MAX_LENGTH.toLocaleString()} characters. Saved on this device.
          </p>
          {addError ? <p className="chat-merged-emoji-add-error">{addError}</p> : null}
          <button
            type="button"
            className="chat-merged-emoji-add-save"
            disabled={!addArt.trim()}
            onClick={handleSaveCollection}>
            Save and insert
          </button>
        </div>
      ) : null}
    </div>
  );
}
