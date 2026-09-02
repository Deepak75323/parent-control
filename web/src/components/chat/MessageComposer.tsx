import {type FormEvent, type KeyboardEvent, type RefObject, useRef} from 'react';

type Props = {
  draft: string;
  placeholder: string;
  busy?: boolean;
  canSend: boolean;
  formId?: string;
  emphasizeSend?: boolean;
  fileInputRef?: RefObject<HTMLInputElement | null>;
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
  fileInputRef: externalFileInputRef,
  onDraftChange,
  onSubmit,
  onFileSelected,
}: Props) {
  const internalFileInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = externalFileInputRef ?? internalFileInputRef;

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      if (canSend && !busy) {
        const form = event.currentTarget.form;
        form?.requestSubmit();
      }
    }
  }

  return (
    <form id={formId} className="chat-compose" onSubmit={onSubmit}>
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
      <div className="chat-compose-input-wrap">
        <textarea
          className="chat-compose-textarea"
          placeholder={placeholder}
          value={draft}
          rows={1}
          aria-label={placeholder}
          disabled={busy}
          onChange={event => onDraftChange(event.target.value)}
          onKeyDown={handleKeyDown}
        />
      </div>
      <button
        type="button"
        className="chat-compose-icon-btn"
        disabled={busy}
        title="Emoji"
        aria-label="Emoji"
        tabIndex={-1}>
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
  return (
    <div className="chat-compose chat-compose--editing">
      <div className="chat-compose-editing-head">
        <span className="chat-compose-editing-label">Editing message</span>
        <p className="chat-compose-editing-preview muted tiny">"{draft || '…'}"</p>
      </div>
      <div className="chat-compose-editing-row">
        <input
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
              onCancel();
            }
          }}
        />
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
