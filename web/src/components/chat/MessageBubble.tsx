import {type CSSProperties, type ReactNode, useRef, useState} from 'react';
import {formatChatTime} from '../../lib/formatChatTime';
import {useLongPress} from '../../hooks/useLongPress';
import {useMessageSwipeActions} from '../../hooks/useSwipeToReply';
import type {ChatMessageItem} from '../../types/api';
import {pickLearningQuestion} from '../../lib/learningQuestions';
import {ChatMessageReplyQuote} from './ChatMessageReplyQuote';
import {MessageLearningChallenge} from './MessageLearningChallenge';
import {MessageActions, type MessageAction} from './MessageActions';
import {MessageReactionPicker} from './MessageReactionPicker';
import {MessageReactions} from './MessageReactions';
import {ReadReceipt} from './ReadReceipt';

type Props = {
  message: ChatMessageItem;
  displayName: string;
  senderSeed: string;
  ownDisplayName: string;
  ownSeed: string;
  /** Stable notebook highlighter color for this sender in the room. */
  highlightColor?: string;
  busy?: boolean;
  isEditing?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  canDeleteForEveryone?: boolean;
  swipeToReplyEnabled?: boolean;
  onEdit?: () => void;
  onDeleteForMe?: () => void;
  onDeleteForEveryone?: () => void;
  onCopy?: () => void;
  onReply?: () => void;
  onQuoteClick?: (messageId: string) => void;
  onToggleReaction?: (emoji: string) => void;
  onMarkUnread?: () => void;
  /** Room owner: unlock/lock obfuscated chat from the message menu. */
  canDecryptRoom?: boolean;
  chatDecrypted?: boolean;
  onDecryptChat?: () => void;
  onLockDecrypt?: () => void;
  children: ReactNode;
};

export function MessageBubble({
  message,
  displayName,
  highlightColor,
  busy = false,
  isEditing = false,
  canEdit = false,
  canDelete = false,
  canDeleteForEveryone = false,
  swipeToReplyEnabled = false,
  onEdit,
  onDeleteForMe,
  onDeleteForEveryone,
  onCopy,
  onReply,
  onQuoteClick,
  onToggleReaction,
  onMarkUnread,
  canDecryptRoom = false,
  chatDecrypted = false,
  onDecryptChat,
  onLockDecrypt,
  children,
}: Props) {
  const isOwn = Boolean(message.ownMessage);
  const time = formatChatTime(message.createdAt);
  const bubbleRef = useRef<HTMLDivElement>(null);
  const [reactionPickerOpen, setReactionPickerOpen] = useState(false);

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

  const longPress = useLongPress({
    enabled: !isEditing && !busy && Boolean(onToggleReaction) && !reactionPickerOpen,
    onLongPress: () => setReactionPickerOpen(true),
  });

  const actions: MessageAction[] = [];
  if (onToggleReaction && !isEditing) {
    actions.push({
      id: 'react',
      label: 'React',
      icon: '🙂',
      onSelect: () => setReactionPickerOpen(true),
    });
  }
  if (onReply && !isEditing) {
    actions.push({id: 'reply', label: 'Reply', icon: '↩', onSelect: onReply});
  }
  if (canDecryptRoom && !isEditing) {
    if (chatDecrypted && onLockDecrypt) {
      actions.push({
        id: 'lock-decrypt',
        label: 'Lock chat',
        icon: '🔒',
        onSelect: onLockDecrypt,
      });
    } else if (!chatDecrypted && onDecryptChat) {
      actions.push({
        id: 'decrypt-chat',
        label: 'Decrypt chat',
        icon: '🔓',
        onSelect: onDecryptChat,
      });
    }
  }
  if (onMarkUnread && !isEditing) {
    actions.push({
      id: 'mark-unread',
      label: 'Mark as unread',
      icon: '●',
      onSelect: onMarkUnread,
    });
  }
  if (isOwn && canEdit && onEdit && !isEditing) {
    actions.push({id: 'edit', label: 'Edit', icon: '✏', onSelect: onEdit});
  }
  if (canDelete && onDeleteForMe) {
    actions.push({
      id: 'delete-me',
      label: 'Delete for me',
      icon: '🗑',
      danger: true,
      onSelect: onDeleteForMe,
    });
  }
  if (canDeleteForEveryone && onDeleteForEveryone) {
    actions.push({
      id: 'delete-everyone',
      label: 'Delete for everyone',
      icon: '🗑',
      danger: true,
      onSelect: onDeleteForEveryone,
    });
  }
  if (!isOwn && onCopy) {
    actions.push({id: 'copy', label: 'Copy', icon: '📋', onSelect: onCopy});
  }

  const noteClass = isOwn
    ? 'chat-message-bubble--parent-a'
    : 'chat-message-bubble--parent-b';
  const learning = pickLearningQuestion(message.id);
  const bubbleStyle = highlightColor
    ? ({['--notes-highlight']: highlightColor} as CSSProperties)
    : undefined;

  return (
    <article
      id={`chat-message-${message.id}`}
      className={`chat-message-row chat-message-row--notebook${isOwn ? ' chat-message-row--own' : ' chat-message-row--incoming'}${isEditing ? ' chat-message-row--editing' : ''}${swipe.swipingReply || swipe.swipingEdit ? ' chat-message-row--swiping' : ''}${reactionPickerOpen ? ' chat-message-row--reacting' : ''}`}
      aria-label={isOwn ? `Your note at ${time}` : `Note from ${displayName} at ${time}`}>
      <div className="chat-message-stack">
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
            <div
              ref={bubbleRef}
              className={`chat-message-bubble chat-message-bubble--notebook ${noteClass}${isOwn ? ' chat-message-bubble--own' : ''}${reactionPickerOpen ? ' chat-message-bubble--reacting' : ''}`}
              style={bubbleStyle}
              onTouchStart={longPress.onTouchStart}
              onTouchEnd={longPress.onTouchEnd}
              onTouchMove={longPress.onTouchMove}
              onTouchCancel={longPress.onTouchCancel}
              onMouseDown={longPress.onMouseDown}
              onMouseUp={longPress.onMouseUp}
              onMouseLeave={longPress.onMouseLeave}
              onMouseMove={longPress.onMouseMove}
              onContextMenu={longPress.onContextMenu}>
              {actions.length > 0 ? (
                <div className="chat-message-bubble-head chat-message-bubble-head--notebook">
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
              <MessageLearningChallenge question={learning} />
              <div className="chat-message-bubble-foot chat-message-bubble-foot--notebook">
                <time className="chat-message-bubble-time" dateTime={message.createdAt}>
                  {time}
                </time>
                {isOwn ? <ReadReceipt status={message.receiptStatus} compact /> : null}
              </div>
              {onToggleReaction ? (
                <MessageReactionPicker
                  open={reactionPickerOpen}
                  anchorRef={bubbleRef}
                  onClose={() => setReactionPickerOpen(false)}
                  onSelect={onToggleReaction}
                />
              ) : null}
            </div>
          </div>
        </div>
        {onToggleReaction ? (
          <MessageReactions reactions={message.reactions} onToggle={onToggleReaction} />
        ) : null}
      </div>
    </article>
  );
}
