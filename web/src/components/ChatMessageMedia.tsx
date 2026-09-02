import {useState} from 'react';
import {chatApi} from '../api/services';
import type {ChatMessageItem} from '../types/api';

type Props = {
  message: ChatMessageItem;
  onConsumed: (messageId: string, patch: Partial<ChatMessageItem>) => void;
};

function mediaLabel(message: ChatMessageItem): string {
  return message.messageType === 'VIDEO' ? 'video' : 'photo';
}

export function ChatMessageMedia({message, onConsumed}: Props) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState(false);
  const [openedLocally, setOpenedLocally] = useState(false);

  const consumed = Boolean(message.attachmentConsumed || openedLocally);
  const label = mediaLabel(message);

  async function handleViewOnce() {
    if (!message.canViewAttachment || opening || blobUrl) {
      return;
    }
    setOpening(true);
    setError(false);
    try {
      const {data} = await chatApi.viewOnceAttachment(message.roomId, message.id);
      const mime = message.attachmentMimeType ?? 'application/octet-stream';
      const blob = new Blob([data], {type: mime});
      const url = URL.createObjectURL(blob);
      setBlobUrl(url);
      setOpenedLocally(true);
      onConsumed(message.id, {
        attachmentConsumed: true,
        canViewAttachment: false,
        attachmentUrl: null,
      });
    } catch {
      setError(true);
    } finally {
      setOpening(false);
    }
  }

  if (message.ownMessage) {
    return (
      <p className="chat-message-view-once muted">
        View once {label} {consumed ? '· Opened' : '· Not opened yet'}
      </p>
    );
  }

  if (consumed && !blobUrl) {
    return <p className="chat-message-view-once muted">View once {label} · Opened</p>;
  }

  if (error) {
    return (
      <p className="chat-message-media-error muted">
        Could not open — it may have already been viewed
      </p>
    );
  }

  if (blobUrl) {
    if (message.messageType === 'VIDEO') {
      return (
        <video
          className="chat-message-video"
          src={blobUrl}
          controls
          autoPlay
          playsInline
          preload="metadata"
          aria-label={message.attachmentFileName ?? 'Video message'}
        />
      );
    }
    return (
      <img
        className="chat-message-image"
        src={blobUrl}
        alt={message.attachmentFileName ?? 'Image message'}
      />
    );
  }

  if (message.canViewAttachment) {
    return (
      <button
        type="button"
        className="chat-view-once-btn"
        disabled={opening}
        onClick={() => void handleViewOnce()}>
        {opening ? 'Opening…' : `Tap to view ${label} once`}
      </button>
    );
  }

  return null;
}
