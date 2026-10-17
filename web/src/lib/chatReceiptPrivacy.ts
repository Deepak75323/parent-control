import type {ChatMessageItem} from '../types/api';

/** Cap READ at DELIVERED when the viewer has read receipts turned off (WhatsApp mutual). */
export function receiptStatusForViewer(
  status: ChatMessageItem['receiptStatus'] | undefined,
  viewerReadReceiptsEnabled: boolean | undefined,
): ChatMessageItem['receiptStatus'] | undefined {
  if (!status) {
    return status;
  }
  if (viewerReadReceiptsEnabled === false && status === 'READ') {
    return 'DELIVERED';
  }
  return status;
}
