import {chatDateKey, formatChatDateLabel} from './formatChatTime';

export type ChatMessageGroup<T extends {createdAt: string}> = {
  dateKey: string;
  label: string;
  messages: T[];
};

export function groupMessagesByDate<T extends {createdAt: string}>(
  messages: T[],
): ChatMessageGroup<T>[] {
  const groups: ChatMessageGroup<T>[] = [];
  let current: ChatMessageGroup<T> | null = null;

  for (const message of messages) {
    const key = chatDateKey(message.createdAt);
    if (!current || current.dateKey !== key) {
      current = {
        dateKey: key,
        label: formatChatDateLabel(message.createdAt),
        messages: [],
      };
      groups.push(current);
    }
    current.messages.push(message);
  }

  return groups;
}
