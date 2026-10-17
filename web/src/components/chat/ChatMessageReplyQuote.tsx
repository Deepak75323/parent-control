import {formatReplyPreviewText, replyPreviewSenderName} from '../../lib/chatReplyPreview';
import type {ChatMessageReplyPreview} from '../../types/api';

type Props = {
  preview: ChatMessageReplyPreview;
  variant?: 'bubble' | 'composer';
  onClick?: () => void;
};

export function ChatMessageReplyQuote({preview, variant = 'bubble', onClick}: Props) {
  const senderName = replyPreviewSenderName(preview);
  const previewText = formatReplyPreviewText(preview);
  const clickable = Boolean(onClick && preview.messageId && !preview.deleted);

  return (
    <button
      type="button"
      className={[
        'chat-reply-quote',
        variant === 'composer' ? 'chat-reply-quote--composer' : '',
        preview.deleted ? 'chat-reply-quote--deleted' : '',
        clickable ? 'chat-reply-quote--clickable' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      disabled={!clickable}
      aria-label={
        preview.deleted
          ? 'This note is deleted'
          : `Quoted message from ${senderName}: ${previewText}`
      }
      onClick={clickable ? onClick : undefined}>
      <span className="chat-reply-quote-bar" aria-hidden />
      <span className="chat-reply-quote-content">
        <span className="chat-reply-quote-sender">{preview.deleted ? 'Message' : senderName}</span>
        <span className="chat-reply-quote-text">{previewText}</span>
      </span>
    </button>
  );
}

type ComposerReplyBarProps = {
  preview: ChatMessageReplyPreview;
  onDismiss: () => void;
};

export function ComposerReplyBar({preview, onDismiss}: ComposerReplyBarProps) {
  const senderName = replyPreviewSenderName(preview);
  const previewText = formatReplyPreviewText(preview);

  return (
    <div className="chat-compose-reply">
      <div className="chat-compose-reply-body">
        <span className="chat-compose-reply-label">Replying to {senderName}</span>
        <span className="chat-compose-reply-preview">{previewText}</span>
      </div>
      <button
        type="button"
        className="chat-compose-reply-dismiss"
        aria-label="Cancel reply"
        onClick={onDismiss}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
          <path d="M18 6L6 18M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
}
