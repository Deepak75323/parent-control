import {useEffect, useState} from 'react';
import {chatApi} from '../api/services';
import {ChatMediaPreview} from './chat/ChatMediaPreview';
import type {ChatMessageItem} from '../types/api';

type Props = {
  message: ChatMessageItem;
  onConsumed: (messageId: string, patch: Partial<ChatMessageItem>) => void;
  /** View-once media opens only after decrypt unlock for this room. */
  chatDecrypted?: boolean;
  canDecryptRoom?: boolean;
  onRequestDecrypt?: () => void;
};

function mediaLabel(message: ChatMessageItem): string {
  return message.messageType === 'VIDEO' ? 'video' : 'photo';
}

export function ChatMessageMedia({
  message,
  onConsumed,
  chatDecrypted = false,
  canDecryptRoom = false,
  onRequestDecrypt,
}: Props) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState(false);
  const [openedLocally, setOpenedLocally] = useState(false);

  const consumed = Boolean(message.attachmentConsumed || openedLocally);
  const label = mediaLabel(message);

  useEffect(() => {
    if (chatDecrypted || !blobUrl) {
      return;
    }
    URL.revokeObjectURL(blobUrl);
    setBlobUrl(null);
  }, [chatDecrypted, blobUrl]);

  async function handleViewOnce() {
    if (!chatDecrypted) {
      onRequestDecrypt?.();
      return;
    }
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
        View once {label} {consumed ? '· Opened' : '· Locked until recipient unlocks'}
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

  if (blobUrl && chatDecrypted) {
    if (message.messageType === 'VIDEO') {
      return (
        <ChatMediaPreview
          className="chat-message-video"
          kind="video"
          src={blobUrl}
          alt={message.attachmentFileName ?? 'Video message'}
          controls
          autoPlay
        />
      );
    }
    return (
      <ChatMediaPreview
        className="chat-message-image"
        kind="image"
        src={blobUrl}
        alt={message.attachmentFileName ?? 'Image message'}
      />
    );
  }

  if (message.canViewAttachment && !chatDecrypted) {
    return (
      <button
        type="button"
        className="chat-view-once-btn"
        disabled={!canDecryptRoom}
        onClick={() => onRequestDecrypt?.()}>
        {canDecryptRoom
          ? `Unlock decrypt to view ${label} once`
          : `Decrypt required to view ${label}`}
      </button>
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
