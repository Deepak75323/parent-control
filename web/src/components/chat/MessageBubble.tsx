import type {ReactNode} from 'react';
import {Avatar} from '../Avatar';
import {formatChatTime} from '../../lib/formatChatTime';
import type {ChatMessageItem} from '../../types/api';
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
  onEdit?: () => void;
  onDelete?: () => void;
  onCopy?: () => void;
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
  onEdit,
  onDelete,
  onCopy,
  children,
}: Props) {
  const isOwn = Boolean(message.ownMessage);
  const name = isOwn ? 'You' : displayName;
  const avatarName = isOwn ? ownDisplayName : displayName;
  const avatarSeed = isOwn ? ownSeed : senderSeed;
  const time = formatChatTime(message.createdAt);

  const actions: MessageAction[] = [];
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

  return (
    <article
      className={`chat-message-row${isOwn ? ' chat-message-row--own' : ' chat-message-row--incoming'}${isEditing ? ' chat-message-row--editing' : ''}`}>
      {!isOwn ? (
        <Avatar name={avatarName} seed={avatarSeed} size="sm" className="chat-message-avatar" />
      ) : null}
      <div className="chat-message-content">
        <div className={`chat-message-bubble${isOwn ? ' chat-message-bubble--own' : ''}`}>
          <div className="chat-message-bubble-head">
            <span className="chat-message-bubble-name">{name}</span>
            <time className="chat-message-bubble-time" dateTime={message.createdAt}>
              {time}
            </time>
            <MessageActions actions={actions} disabled={busy} />
          </div>
          <div className="chat-message-bubble-body">{children}</div>
          {isOwn ? (
            <div className="chat-message-bubble-foot">
              <ReadReceipt status={message.receiptStatus} compact />
            </div>
          ) : null}
        </div>
        {isOwn && message.receiptStatus === 'READ' ? (
          <span className="chat-receipt-read-label" aria-label={`Read at ${time}`}>
            Read {time}
          </span>
        ) : null}
      </div>
      {isOwn ? (
        <Avatar name={avatarName} seed={avatarSeed} size="sm" className="chat-message-avatar" />
      ) : null}
    </article>
  );
}
