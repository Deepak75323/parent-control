import type {ChatMessageItem} from '../types/api';

type ReceiptStatus = NonNullable<ChatMessageItem['receiptStatus']>;

export function ChatMessageReceipt({status}: {status?: ReceiptStatus}) {
  if (!status) {
    return null;
  }
  const label =
    status === 'READ' ? 'Read' : status === 'DELIVERED' ? 'Delivered' : 'Sent';
  const ticks = status === 'SENT' ? '✓' : '✓✓';
  return (
    <span
      className={`chat-receipt chat-receipt--${status.toLowerCase()}`}
      title={label}
      aria-label={label}>
      {ticks}
    </span>
  );
}
