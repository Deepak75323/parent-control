import type {ReactNode} from 'react';
import {Avatar} from '../Avatar';
import {formatChatTime} from '../../lib/formatChatTime';
import {useMessageSwipeActions} from '../../hooks/useSwipeToReply';
import type {ChatMessageItem} from '../../types/api';
import {ChatMessageReplyQuote} from './ChatMessageReplyQuote';
import {MessageActions, type MessageAction} from './MessageActions';
import {ReadReceipt} from './ReadReceipt';

type Props = {
  message: ChatMessageItem;
  displayName: string;
  senderSeed: string;
  ownDisplayName: string;
  ownSeed: string;
  busy?: boolean;
  isEditing?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  swipeToReplyEnabled?: boolean;
  onEdit?: () => void;
  onDelete?: () => void;
  onCopy?: () => void;
  onReply?: () => void;
  onQuoteClick?: (messageId: string) => void;
  children: ReactNode;
};

export function MessageBubble({
  message,
  displayName,
  senderSeed,
  ownDisplayName,
  ownSeed,
  busy = false,
  isEditing = false,
  canEdit = false,
  canDelete = false,
  swipeToReplyEnabled = false,
  onEdit,
  onDelete,
  onCopy,
  onReply,
  onQuoteClick,
  children,
}: Props) {
  const isOwn = Boolean(message.ownMessage);
  const avatarName = isOwn ? ownDisplayName : displayName;
  const avatarSeed = isOwn ? ownSeed : senderSeed;
  const time = formatChatTime(message.createdAt);

  const swipe = useMessageSwipeActions({
    enabled:
      swipeToReplyEnabled &&
      !isEditing &&
      !busy &&
      (Boolean(onReply) || (canEdit && Boolean(onEdit))),
    canEdit: canEdit && Boolean(onEdit),
    onReply: onReply ? () => onReply() : undefined,
    onEdit: onEdit ? () => onEdit() : undefined,
  });

  const actions: MessageAction[] = [];
  if (onReply && !isEditing) {
    actions.push({id: 'reply', label: 'Reply', icon: '↩', onSelect: onReply});
  }
  if (isOwn && canEdit && onEdit && !isEditing) {
    actions.push({id: 'edit', label: 'Edit', icon: '✏', onSelect: onEdit});
  }
  if (canDelete && onDelete) {
    actions.push({
      id: 'delete',
      label: isOwn ? 'Delete for everyone' : 'Delete',
      icon: '🗑',
      danger: true,
      onSelect: onDelete,
    });
  }
  if (!isOwn && onCopy) {
    actions.push({id: 'copy', label: 'Copy', icon: '📋', onSelect: onCopy});
  }

  const bubble = (
    <div className={`chat-message-bubble${isOwn ? ' chat-message-bubble--own' : ''}`}>
      {isOwn ? (
        <>
          {actions.length > 0 ? (
            <div className="chat-message-bubble-head chat-message-bubble-head--own">
              <MessageActions actions={actions} disabled={busy} />
            </div>
          ) : null}
          {message.replyTo ? (
            <ChatMessageReplyQuote
              preview={message.replyTo}
              onClick={
                onQuoteClick && message.replyTo.messageId
                  ? () => onQuoteClick(message.replyTo!.messageId)
                  : undefined
              }
            />
          ) : null}
          <div className="chat-message-bubble-body">{children}</div>
          <div className="chat-message-bubble-foot chat-message-bubble-foot--own">
            <time className="chat-message-bubble-time" dateTime={message.createdAt}>
              {time}
            </time>
            <ReadReceipt status={message.receiptStatus} compact />
          </div>
        </>
      ) : (
        <>
          <div className="chat-message-bubble-head">
            <span className="chat-message-bubble-name">{displayName}</span>
            <time className="chat-message-bubble-time" dateTime={message.createdAt}>
              {time}
            </time>
            <MessageActions actions={actions} disabled={busy} />
          </div>
          {message.replyTo ? (
            <ChatMessageReplyQuote
              preview={message.replyTo}
              onClick={
                onQuoteClick && message.replyTo.messageId
                  ? () => onQuoteClick(message.replyTo!.messageId)
                  : undefined
              }
            />
          ) : null}
          <div className="chat-message-bubble-body">{children}</div>
        </>
      )}
    </div>
  );

  return (
    <article
      id={`chat-message-${message.id}`}
      className={`chat-message-row${isOwn ? ' chat-message-row--own' : ' chat-message-row--incoming'}${isEditing ? ' chat-message-row--editing' : ''}${swipe.swipingReply || swipe.swipingEdit ? ' chat-message-row--swiping' : ''}`}
      aria-label={isOwn ? `Your message at ${time}` : `${displayName}, ${time}`}>
      {!isOwn ? (
        <Avatar name={avatarName} seed={avatarSeed} size="sm" className="chat-message-avatar" />
      ) : null}
      <div
        className="chat-message-swipe-wrap"
        onTouchStart={swipe.onTouchStart}
        onTouchMove={swipe.onTouchMove}
        onTouchEnd={swipe.onTouchEnd}
        onTouchCancel={swipe.onTouchCancel}>
        <div
          className={`chat-message-swipe-reply${swipe.offset > 0 ? ' chat-message-swipe-reply--visible' : ''}`}
          aria-hidden>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M9 17l-5-5 5-5" />
            <path d="M20 18V6a2 2 0 00-2-2H4" />
          </svg>
        </div>
        <div
          className={`chat-message-swipe-edit${swipe.offset < 0 ? ' chat-message-swipe-edit--visible' : ''}`}
          aria-hidden>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 20h9" />
            <path d="M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4 12.5-12.5z" />
          </svg>
        </div>
        <div
          className="chat-message-content"
          style={swipe.offset !== 0 ? {transform: `translateX(${swipe.offset}px)`} : undefined}>
          {bubble}
          {isOwn && message.receiptStatus === 'READ' ? (
            <span className="chat-receipt-read-label" aria-label={`Read at ${time}`}>
              Read {time}
            </span>
          ) : null}
        </div>
      </div>
      {isOwn ? (
        <Avatar name={avatarName} seed={avatarSeed} size="sm" className="chat-message-avatar" />
      ) : null}
    </article>
  );
}
