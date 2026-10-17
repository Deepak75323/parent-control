import type {ChatMessageItem} from '../types/api';

const RECEIPT_RANK = {
  SENT: 0,
  DELIVERED: 1,
  READ: 2,
} as const;

const RECEIPT_BY_RANK: NonNullable<ChatMessageItem['receiptStatus']>[] = [
  'SENT',
  'DELIVERED',
  'READ',
];

/** If a later own message was read, earlier ones in the thread must not show a lower status. */
export function withMonotonicReceiptStatuses(
  messages: ChatMessageItem[],
): ChatMessageItem[] {
  let maxFromNewer = 0;
  const result = [...messages];

  for (let index = result.length - 1; index >= 0; index -= 1) {
    const message = result[index];
    if (!message.ownMessage || !message.receiptStatus) {
      continue;
    }
    const rank = RECEIPT_RANK[message.receiptStatus];
    maxFromNewer = Math.max(maxFromNewer, rank);
    if (rank < maxFromNewer) {
      result[index] = {
        ...message,
        receiptStatus: RECEIPT_BY_RANK[maxFromNewer],
      };
    }
  }

  return result;
}
