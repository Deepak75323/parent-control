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
  previewMergedEmojiLines,
  removeCustomMergedEmoji,
  type MergedEmojiArt,
} from '../../lib/chatMergedEmojiCatalog';
import {
  countMergedEmojiLines,
  normalizeMergedEmojiArt,
} from '../../lib/chatMergedEmojiFormat';
import {CHAT_MESSAGE_PLAINTEXT_MAX} from '../../lib/chatMessageLimits';
import {trimPlaintextToSealedLimit} from '../../lib/chatMessageSealedLength';
import {
  CHAT_STICKER_PACKS,
  defaultStickerPackId,
} from '../../lib/chatStickerCatalog';
import {
  downloadGifAsFile,
  fetchTrendingGiphyGifs,
  giphyApiKeyFromEnv,
  searchGiphyGifs,
  type GifResult,
} from '../../lib/giphyClient';

type PickerMode = 'emoji' | 'stickers' | 'gifs' | 'merged';

type Props = {
  open: boolean;
  onClose: () => void;
  onSelect: (emoji: string) => void;
  /** Called when a GIF is picked — parent should stage/send as view-once media. */
  onPickMedia?: (file: File) => void;
  ignoreCloseRef?: RefObject<HTMLElement | null>;
};

export function EmojiPicker({open, onClose, onSelect, onPickMedia, ignoreCloseRef}: Props) {
  const menuId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const pasteRef = useRef<HTMLTextAreaElement>(null);
  const gifSearchRef = useRef<HTMLInputElement>(null);
  const [recentEmojis, setRecentEmojis] = useState<string[]>(() => loadRecentEmojis());
  const [activeCategoryId, setActiveCategoryId] = useState(() =>
    defaultEmojiCategoryId(loadRecentEmojis()),
  );
  const [mode, setMode] = useState<PickerMode>('emoji');
  const [stickerPackId, setStickerPackId] = useState(() => defaultStickerPackId());
  const [mergedTag, setMergedTag] = useState<string>('all');
  const [customItems, setCustomItems] = useState<MergedEmojiArt[]>(() => loadCustomMergedEmojis());
  const [addLabel, setAddLabel] = useState('');
  const [addArt, setAddArt] = useState('');
  const [addError, setAddError] = useState<string | null>(null);
  const [showPaste, setShowPaste] = useState(false);
  const [gifQuery, setGifQuery] = useState('');
  const [gifResults, setGifResults] = useState<GifResult[]>([]);
  const [gifBusy, setGifBusy] = useState(false);
  const [gifError, setGifError] = useState<string | null>(null);
  const giphyKey = useMemo(() => giphyApiKeyFromEnv(), []);

  useEffect(() => {
    if (!open) {
      return;
    }
    const recent = loadRecentEmojis();
    setRecentEmojis(recent);
    setActiveCategoryId(defaultEmojiCategoryId(recent));
    setCustomItems(loadCustomMergedEmojis());
    setMode('emoji');
    setStickerPackId(defaultStickerPackId());
    setShowPaste(false);
    setAddError(null);
    setGifQuery('');
    setGifResults([]);
    setGifError(null);
  }, [open]);

  useEffect(() => {
    if (!open || mode !== 'merged' || !showPaste) {
      return;
    }
    window.requestAnimationFrame(() => pasteRef.current?.focus());
  }, [open, mode, showPaste]);

  useEffect(() => {
    if (!open || mode !== 'gifs' || !giphyKey) {
      return;
    }
    let cancelled = false;
    setGifBusy(true);
    setGifError(null);
    const handle = window.setTimeout(() => {
      void (async () => {
        try {
          const results = gifQuery.trim()
            ? await searchGiphyGifs(gifQuery, giphyKey)
            : await fetchTrendingGiphyGifs(giphyKey);
          if (!cancelled) {
            setGifResults(results);
          }
        } catch (error) {
          if (!cancelled) {
            setGifResults([]);
            setGifError(error instanceof Error ? error.message : 'Could not load GIFs');
          }
        } finally {
          if (!cancelled) {
            setGifBusy(false);
          }
        }
      })();
    }, gifQuery.trim() ? 280 : 0);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [open, mode, gifQuery, giphyKey]);

  useEffect(() => {
    if (!open || mode !== 'gifs' || !giphyKey) {
      return;
    }
    window.requestAnimationFrame(() => gifSearchRef.current?.focus());
  }, [open, mode, giphyKey]);

  const categories = useMemo(() => {
    const recentCategory: EmojiCategory | null =
      recentEmojis.length > 0
        ? {id: 'recent', label: 'Recent', icon: '🕘', emojis: recentEmojis}
        : null;
    return recentCategory ? [recentCategory, ...CHAT_EMOJI_CATEGORIES] : CHAT_EMOJI_CATEGORIES;
  }, [recentEmojis]);

  const activeCategory =
    categories.find(category => category.id === activeCategoryId) ?? categories[0];

  const activeStickerPack =
    CHAT_STICKER_PACKS.find(pack => pack.id === stickerPackId) ?? CHAT_STICKER_PACKS[0];

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

  if (!open || !activeCategory || !activeStickerPack) {
    return null;
  }

  function handleSelectEmoji(emoji: string) {
    setRecentEmojis(rememberEmoji(emoji));
    onSelect(emoji);
  }

  function handleSelectSticker(sticker: string) {
    setRecentEmojis(rememberEmoji(sticker));
    onSelect(sticker);
    onClose();
  }

  async function handleSelectGif(gif: GifResult) {
    if (!onPickMedia) {
      setGifError('GIF attach is not available here');
      return;
    }
    setGifBusy(true);
    setGifError(null);
    try {
      const file = await downloadGifAsFile(gif);
      onPickMedia(file);
      onClose();
    } catch (error) {
      setGifError(error instanceof Error ? error.message : 'Could not attach GIF');
    } finally {
      setGifBusy(false);
    }
  }

  function artForSend(raw: string): string {
    const normalized = normalizeMergedEmojiArt(raw).slice(0, CHAT_MESSAGE_PLAINTEXT_MAX);
    return trimPlaintextToSealedLimit(normalized);
  }

  function handleSelectMerged(art: MergedEmojiArt) {
    onSelect(artForSend(art.art));
    onClose();
  }

  function handlePasteArt(event: React.ClipboardEvent<HTMLTextAreaElement>) {
    const pasted = event.clipboardData.getData('text');
    if (!pasted) {
      return;
    }
    event.preventDefault();
    setAddArt(artForSend(pasted));
    setAddError(null);
  }

  function clearPasteForm() {
    setAddLabel('');
    setAddArt('');
    setAddError(null);
  }

  function handleSaveFromPaste(insertAfterSave: boolean) {
    setAddError(null);
    try {
      const saved = addCustomMergedEmoji({
        label: addLabel || 'Pasted art',
        art: artForSend(addArt),
        tags: ['my collection', 'cute'],
      });
      setCustomItems(loadCustomMergedEmojis());
      clearPasteForm();
      setMergedTag('my collection');
      setShowPaste(false);
      if (insertAfterSave) {
        onSelect(saved.art);
        onClose();
      }
    } catch (error) {
      setAddError(error instanceof Error ? error.message : 'Could not save collection');
    }
  }

  function handleInsertPasteOnly() {
    const art = artForSend(addArt);
    if (!art.trim()) {
      setAddError('Paste emoji art first');
      return;
    }
    onSelect(art);
    onClose();
  }

  return (
    <div
      className="chat-emoji-picker"
      id={menuId}
      ref={rootRef}
      role="dialog"
      aria-label="Choose emoji, sticker, or GIF">
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
          aria-selected={mode === 'stickers'}
          className={mode === 'stickers' ? 'chat-emoji-picker-mode--active' : undefined}
          onClick={() => setMode('stickers')}>
          Stickers
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'gifs'}
          className={mode === 'gifs' ? 'chat-emoji-picker-mode--active' : undefined}
          onClick={() => setMode('gifs')}>
          GIFs
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={mode === 'merged'}
          className={mode === 'merged' ? 'chat-emoji-picker-mode--active' : undefined}
          onClick={() => setMode('merged')}>
          Merged
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

      {mode === 'stickers' ? (
        <>
          <div className="chat-emoji-picker-tabs" role="tablist" aria-label="Sticker packs">
            {CHAT_STICKER_PACKS.map(pack => (
              <button
                key={pack.id}
                type="button"
                role="tab"
                aria-selected={pack.id === activeStickerPack.id}
                aria-label={pack.label}
                className={[
                  'chat-emoji-picker-tab',
                  pack.id === activeStickerPack.id ? 'chat-emoji-picker-tab--active' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onClick={() => setStickerPackId(pack.id)}>
                {pack.icon}
              </button>
            ))}
          </div>
          <div
            className="chat-emoji-picker-grid chat-sticker-grid"
            role="tabpanel"
            aria-label={activeStickerPack.label}>
            {activeStickerPack.stickers.map((sticker, index) => (
              <button
                key={`${activeStickerPack.id}-${index}-${sticker}`}
                type="button"
                className="chat-emoji-picker-item chat-sticker-item"
                aria-label={`Send sticker ${sticker}`}
                onClick={() => handleSelectSticker(sticker)}>
                {sticker}
              </button>
            ))}
          </div>
        </>
      ) : null}

      {mode === 'gifs' ? (
        <div className="chat-gif-picker" role="tabpanel" aria-label="GIF search">
          {!giphyKey ? (
            <div className="chat-gif-picker-empty">
              <p>
                Add <code>VITE_GIPHY_API_KEY</code> to enable GIF search. Tenor no longer accepts new
                API clients.
              </p>
              <p className="chat-gif-picker-hint">
                Get a free key at{' '}
                <a href="https://developers.giphy.com/" target="_blank" rel="noreferrer">
                  developers.giphy.com
                </a>
                . Or tap <strong>+</strong> to attach a GIF file from your device.
              </p>
            </div>
          ) : (
            <>
              <label className="chat-gif-picker-search">
                <span className="sr-only">Search GIFs</span>
                <input
                  ref={gifSearchRef}
                  type="search"
                  value={gifQuery}
                  onChange={event => setGifQuery(event.target.value)}
                  onMouseDown={event => event.stopPropagation()}
                  placeholder="Search GIFs…"
                  autoComplete="off"
                />
              </label>
              {gifError ? <p className="chat-gif-picker-error">{gifError}</p> : null}
              {gifBusy && gifResults.length === 0 ? (
                <p className="chat-gif-picker-hint">Loading GIFs…</p>
              ) : null}
              <div className="chat-gif-picker-grid">
                {gifResults.map(gif => (
                  <button
                    key={gif.id}
                    type="button"
                    className="chat-gif-picker-item"
                    disabled={gifBusy}
                    aria-label={`Attach GIF ${gif.title}`}
                    onClick={() => void handleSelectGif(gif)}>
                    <img src={gif.previewUrl} alt="" loading="lazy" />
                  </button>
                ))}
              </div>
              {!gifBusy && gifResults.length === 0 && !gifError ? (
                <p className="chat-gif-picker-hint">No GIFs found — try another search.</p>
              ) : null}
            </>
          )}
        </div>
      ) : null}

      {mode === 'merged' ? (
        <div className="chat-merged-emoji" role="tabpanel" aria-label="Merged emoji">
          <button
            type="button"
            className="chat-merged-emoji-paste-toggle"
            aria-expanded={showPaste}
            onClick={() => {
              setShowPaste(openPaste => !openPaste);
              setAddError(null);
            }}>
            {showPaste ? 'Hide paste box' : 'Paste your own emoji art'}
          </button>

          {showPaste ? (
            <div className="chat-merged-emoji-add">
              <label className="chat-merged-emoji-add-field">
                <span>Paste here</span>
                <textarea
                  ref={pasteRef}
                  className="chat-merged-emoji-paste-input"
                  value={addArt}
                  onChange={event =>
                    setAddArt(event.target.value.slice(0, CHAT_MESSAGE_PLAINTEXT_MAX))
                  }
                  onPaste={handlePasteArt}
                  onMouseDown={event => event.stopPropagation()}
                  placeholder="Copy emoji art from anywhere, then paste it here…"
                  rows={6}
                  maxLength={CHAT_MESSAGE_PLAINTEXT_MAX}
                  spellCheck={false}
                />
              </label>
              <button
                type="button"
                className="chat-merged-emoji-add-secondary"
                disabled={!addArt.trim()}
                onClick={() => setAddArt(artForSend(addArt))}>
                Split into rows (fix order)
              </button>
              <label className="chat-merged-emoji-add-field">
                <span>Name (optional)</span>
                <input
                  value={addLabel}
                  onChange={event => setAddLabel(event.target.value)}
                  onMouseDown={event => event.stopPropagation()}
                  placeholder="Golden lily"
                  maxLength={64}
                />
              </label>
              <p className="chat-merged-emoji-add-hint">
                Up to {CHAT_MESSAGE_PLAINTEXT_MAX.toLocaleString()} characters (longer after sealing).
                Saved on this device.
              </p>
              {addError ? <p className="chat-merged-emoji-add-error">{addError}</p> : null}
              <div className="chat-merged-emoji-add-actions">
                <button
                  type="button"
                  className="chat-merged-emoji-add-save"
                  disabled={!addArt.trim()}
                  onClick={() => handleSaveFromPaste(true)}>
                  Save & insert
                </button>
                <button
                  type="button"
                  className="chat-merged-emoji-add-secondary"
                  disabled={!addArt.trim()}
                  onClick={() => handleSaveFromPaste(false)}>
                  Save only
                </button>
                <button
                  type="button"
                  className="chat-merged-emoji-add-secondary"
                  disabled={!addArt.trim()}
                  onClick={handleInsertPasteOnly}>
                  Insert only
                </button>
              </div>
              {addArt.trim() ? (
                <details className="chat-merged-emoji-live-preview" open={countMergedEmojiLines(addArt) <= 8}>
                  <summary className="chat-merged-emoji-live-preview-label">
                    Full preview ({countMergedEmojiLines(addArt)} lines) — scroll inside
                  </summary>
                  <pre className="chat-merged-emoji-live-preview-body" tabIndex={0}>
                    {artForSend(addArt)}
                  </pre>
                </details>
              ) : null}
            </div>
          ) : null}

          {!showPaste ? (
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
          ) : null}
          {!showPaste ? (
          <div className="chat-merged-emoji-list">
            {mergedItems.length === 0 ? (
              <p className="chat-merged-emoji-empty">
                No merged emoji here yet. Paste your own art above.
              </p>
            ) : (
              mergedItems.map(item => (
                <div key={item.id} className="chat-merged-emoji-row">
                  <button
                    type="button"
                    className="chat-merged-emoji-item"
                    aria-label={`Insert ${item.label}`}
                    onClick={() => handleSelectMerged(item)}>
                    <span className="chat-merged-emoji-label">{item.label}</span>
                    <pre className="chat-merged-emoji-preview">
                      {previewMergedEmojiLines(artForSend(item.art), 8, 120)}
                    </pre>
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
          ) : (
            <p className="chat-merged-emoji-add-hint">
              Hide paste box to browse built-in collections.
            </p>
          )}
        </div>
      ) : null}
    </div>
  );
}
