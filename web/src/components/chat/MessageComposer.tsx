import {
  type FormEvent,
  type KeyboardEvent,
  type RefObject,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  buildMentionCandidates,
  filterMentionCandidates,
  findActiveMentionQuery,
  insertMention,
  type MentionCandidate,
} from '../../lib/chatMentions';
import type {ChatMemberItem} from '../../types/api';
import {insertTextAtSelection} from '../../lib/insertTextAtSelection';
import {ComposeAnchorPortal} from './ComposeAnchorPortal';
import {EmojiPicker} from './EmojiPicker';
import {MentionAutocomplete} from './MentionAutocomplete';

type Props = {
  draft: string;
  placeholder: string;
  busy?: boolean;
  canSend: boolean;
  formId?: string;
  emphasizeSend?: boolean;
  members?: ChatMemberItem[];
  fileInputRef?: RefObject<HTMLInputElement | null>;
  textareaRef?: RefObject<HTMLTextAreaElement | null>;
  onDraftChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  onFileSelected: (event: React.ChangeEvent<HTMLInputElement>) => void;
};

export function MessageComposer({
  draft,
  placeholder,
  busy = false,
  canSend,
  formId = 'chat-compose-form',
  emphasizeSend = false,
  members = [],
  fileInputRef: externalFileInputRef,
  textareaRef: externalTextareaRef,
  onDraftChange,
  onSubmit,
  onFileSelected,
}: Props) {
  const internalFileInputRef = useRef<HTMLInputElement>(null);
  const internalTextareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = externalFileInputRef ?? internalFileInputRef;
  const textareaRef = externalTextareaRef ?? internalTextareaRef;
  const inputWrapRef = useRef<HTMLDivElement>(null);
  const emojiButtonRef = useRef<HTMLButtonElement>(null);
  const mentionButtonRef = useRef<HTMLButtonElement>(null);
  const [mentionIndex, setMentionIndex] = useState(0);
  const [emojiOpen, setEmojiOpen] = useState(false);
  const [cursorPosition, setCursorPosition] = useState(0);

  const mentionCandidates = useMemo(() => buildMentionCandidates(members), [members]);

  const activeMention = useMemo(
    () => findActiveMentionQuery(draft, cursorPosition),
    [draft, cursorPosition],
  );

  const filteredMentions = useMemo(() => {
    if (!activeMention) {
      return [];
    }
    return filterMentionCandidates(mentionCandidates, activeMention.query);
  }, [activeMention, mentionCandidates]);

  const mentionOpen = Boolean(activeMention && filteredMentions.length > 0);

  function syncCursor() {
    const textarea = textareaRef.current;
    if (!textarea) {
      return;
    }
    setCursorPosition(textarea.selectionStart ?? draft.length);
  }

  function insertAtCursor(insertion: string) {
    const textarea = textareaRef.current;
    const start = textarea?.selectionStart ?? draft.length;
    const end = textarea?.selectionEnd ?? draft.length;
    const {nextText, cursor} = insertTextAtSelection(draft, insertion, start, end);
    onDraftChange(nextText);
    window.requestAnimationFrame(() => {
      if (!textarea) {
        return;
      }
      textarea.focus();
      textarea.setSelectionRange(cursor, cursor);
    });
  }

  function applyMention(candidate: MentionCandidate) {
    if (!activeMention) {
      return;
    }
    const {nextText, cursor} = insertMention(
      draft,
      activeMention.start,
      activeMention.end,
      candidate.token,
    );
    onDraftChange(nextText);
    setMentionIndex(0);
    window.requestAnimationFrame(() => {
      const textarea = textareaRef.current;
      if (!textarea) {
        return;
      }
      textarea.focus();
      textarea.setSelectionRange(cursor, cursor);
      setCursorPosition(cursor);
    });
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (mentionOpen) {
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        setMentionIndex(prev => (prev + 1) % filteredMentions.length);
        return;
      }
      if (event.key === 'ArrowUp') {
        event.preventDefault();
        setMentionIndex(prev => (prev - 1 + filteredMentions.length) % filteredMentions.length);
        return;
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        event.preventDefault();
        const candidate = filteredMentions[mentionIndex];
        if (candidate) {
          applyMention(candidate);
        }
        return;
      }
      if (event.key === 'Escape') {
        event.preventDefault();
        onDraftChange(draft.replace(/@[^\s@]*$/, ''));
        return;
      }
    }

    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      if (canSend && !busy) {
        const form = event.currentTarget.form;
        form?.requestSubmit();
      }
    }
  }

  function handleSubmit(event: FormEvent) {
    setEmojiOpen(false);
    onSubmit(event);
  }

  function insertMentionTrigger() {
    setEmojiOpen(false);
    const textarea = textareaRef.current;
    if (!textarea) {
      onDraftChange(`${draft}@`);
      setCursorPosition(draft.length + 1);
      return;
    }
    const start = textarea.selectionStart ?? draft.length;
    const end = textarea.selectionEnd ?? draft.length;
    const next = `${draft.slice(0, start)}@${draft.slice(end)}`;
    onDraftChange(next);
    window.requestAnimationFrame(() => {
      textarea.focus();
      const cursor = start + 1;
      textarea.setSelectionRange(cursor, cursor);
      setCursorPosition(cursor);
    });
  }

  return (
    <form id={formId} className="chat-compose" onSubmit={handleSubmit}>
      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm,video/quicktime"
        className="chat-compose-file-input"
        onChange={onFileSelected}
      />
      <button
        type="button"
        className="chat-compose-icon-btn"
        disabled={busy}
        title="Attach image or video"
        aria-label="Attach image or video"
        onClick={() => fileInputRef.current?.click()}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
          <path d="M12 5v14M5 12h14" />
        </svg>
      </button>
      <div className="chat-compose-input-wrap" ref={inputWrapRef}>
        <textarea
          ref={textareaRef}
          className="chat-compose-textarea"
          placeholder={placeholder}
          value={draft}
          rows={1}
          aria-label={placeholder}
          aria-expanded={mentionOpen}
          onChange={event => {
            onDraftChange(event.target.value);
            setMentionIndex(0);
            setCursorPosition(event.target.selectionStart ?? event.target.value.length);
          }}
          onClick={syncCursor}
          onKeyUp={syncCursor}
          onSelect={syncCursor}
          onKeyDown={handleKeyDown}
        />
      </div>
      <ComposeAnchorPortal
        open={mentionOpen}
        anchorRef={inputWrapRef}
        className="chat-compose-popover">
        <MentionAutocomplete
          candidates={filteredMentions}
          activeIndex={mentionIndex}
          onSelect={applyMention}
        />
      </ComposeAnchorPortal>
      <ComposeAnchorPortal
        open={emojiOpen}
        anchorRef={inputWrapRef}
        className="chat-compose-popover">
        <EmojiPicker
          open={emojiOpen}
          ignoreCloseRef={emojiButtonRef}
          onClose={() => setEmojiOpen(false)}
          onSelect={emoji => insertAtCursor(emoji)}
        />
      </ComposeAnchorPortal>
      <button
        ref={mentionButtonRef}
        type="button"
        className="chat-compose-icon-btn"
        disabled={busy}
        title="Mention by nickname"
        aria-label="Mention by nickname"
        onClick={insertMentionTrigger}>
        <span className="chat-compose-at" aria-hidden>
          @
        </span>
      </button>
      <button
        ref={emojiButtonRef}
        type="button"
        className={['chat-compose-icon-btn', emojiOpen ? 'chat-compose-icon-btn--active' : '']
          .filter(Boolean)
          .join(' ')}
        disabled={busy}
        title="Emoji"
        aria-label="Emoji"
        aria-expanded={emojiOpen}
        onClick={() => setEmojiOpen(open => !open)}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
          <circle cx="12" cy="12" r="9" />
          <path d="M8 14s1.5 2 4 2 4-2 4-2" />
          <circle cx="9" cy="10" r="1" fill="currentColor" stroke="none" />
          <circle cx="15" cy="10" r="1" fill="currentColor" stroke="none" />
        </svg>
      </button>
      <button
        type="submit"
        className={[
          'chat-compose-send',
          canSend ? 'chat-compose-send--active' : '',
          emphasizeSend ? 'chat-compose-send--emphasized' : '',
        ]
          .filter(Boolean)
          .join(' ')}
        disabled={busy || !canSend}
        aria-label={emphasizeSend ? 'Send attachment' : 'Send message'}>
        {emphasizeSend ? <span className="chat-compose-send-text">Send</span> : null}
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
          <path d="M3.4 20.6l17.2-8.6L3.4 3.4l2.9 6.8 7.5 1.1-7.5 1.1-2.9 6.8z" />
        </svg>
      </button>
    </form>
  );
}

type EditComposerProps = {
  draft: string;
  busy?: boolean;
  isText: boolean;
  onDraftChange: (value: string) => void;
  onSave: () => void;
  onCancel: () => void;
};

export function EditMessageComposer({
  draft,
  busy = false,
  isText,
  onDraftChange,
  onSave,
  onCancel,
}: EditComposerProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const inputWrapRef = useRef<HTMLDivElement>(null);
  const emojiButtonRef = useRef<HTMLButtonElement>(null);
  const [emojiOpen, setEmojiOpen] = useState(false);

  function insertAtCursor(insertion: string) {
    const input = inputRef.current;
    const start = input?.selectionStart ?? draft.length;
    const end = input?.selectionEnd ?? draft.length;
    const {nextText, cursor} = insertTextAtSelection(draft, insertion, start, end);
    onDraftChange(nextText);
    window.requestAnimationFrame(() => {
      if (!input) {
        return;
      }
      input.focus();
      input.setSelectionRange(cursor, cursor);
    });
  }

  return (
    <div className="chat-compose chat-compose--editing">
      <div className="chat-compose-editing-head">
        <span className="chat-compose-editing-label">Editing message</span>
        <p className="chat-compose-editing-preview muted tiny">"{draft || '…'}"</p>
      </div>
      <div className="chat-compose-editing-row">
        <div className="chat-compose-editing-input-wrap" ref={inputWrapRef}>
          <input
            ref={inputRef}
            type="text"
            className="chat-compose-editing-input"
            value={draft}
            autoFocus
            maxLength={2000}
            placeholder={isText ? 'Edit message' : 'Edit caption'}
            disabled={busy}
            onChange={event => onDraftChange(event.target.value)}
            onKeyDown={event => {
              if (event.key === 'Enter') {
                event.preventDefault();
                onSave();
              }
              if (event.key === 'Escape') {
                if (emojiOpen) {
                  setEmojiOpen(false);
                  return;
                }
                onCancel();
              }
            }}
          />
        </div>
        <ComposeAnchorPortal
          open={emojiOpen}
          anchorRef={inputWrapRef}
          className="chat-compose-popover">
          <EmojiPicker
            open={emojiOpen}
            ignoreCloseRef={emojiButtonRef}
            onClose={() => setEmojiOpen(false)}
            onSelect={emoji => insertAtCursor(emoji)}
          />
        </ComposeAnchorPortal>
        <button
          ref={emojiButtonRef}
          type="button"
          className={['btn ghost sm chat-compose-editing-emoji', emojiOpen ? 'active' : '']
            .filter(Boolean)
            .join(' ')}
          disabled={busy}
          aria-label="Insert emoji"
          aria-expanded={emojiOpen}
          onClick={() => setEmojiOpen(open => !open)}>
          😀
        </button>
        <button type="button" className="btn ghost sm" disabled={busy} onClick={onCancel}>
          Cancel
        </button>
        <button type="button" className="btn primary sm" disabled={busy} onClick={onSave}>
          Save
        </button>
      </div>
    </div>
  );
}
