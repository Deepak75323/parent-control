import {formatChatTime} from '../../lib/formatChatTime';
import type {ChatMessageItem} from '../../types/api';

type ReceiptStatus = NonNullable<ChatMessageItem['receiptStatus']>;

type Props = {
  status?: ReceiptStatus;
  createdAt?: string;
  compact?: boolean;
};

export function ReadReceipt({status, createdAt, compact = false}: Props) {
  if (!status) {
    return null;
  }

  const label =
    status === 'READ' ? 'Read' : status === 'DELIVERED' ? 'Delivered' : 'Sent';
  const ticks = status === 'SENT' ? '✓' : '✓✓';
  const readTime = status === 'READ' && createdAt ? formatChatTime(createdAt) : null;

  if (compact) {
    return (
      <span
        className={`chat-receipt chat-receipt--${status.toLowerCase()}`}
        title={label}
        aria-label={label}>
        {ticks}
      </span>
    );
  }

  return (
    <div className="chat-receipt-block">
      <span
        className={`chat-receipt chat-receipt--${status.toLowerCase()}`}
        title={label}
        aria-label={label}>
        {ticks}
      </span>
      {readTime ? (
        <span className="chat-receipt-read-label" aria-label={`Read at ${readTime}`}>
          Read {readTime}
        </span>
      ) : null}
    </div>
  );
}
