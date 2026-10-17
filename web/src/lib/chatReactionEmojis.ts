/** WhatsApp-style quick reactions shown on long-press / react menu. */
export const CHAT_QUICK_REACTION_EMOJIS = ['👍', '❤️', '😂', '😮', '😢', '🙏'] as const;

export function reactionTooltip(labels: string[]): string {
  if (labels.length === 0) {
    return '';
  }
  if (labels.length <= 3) {
    return labels.join(', ');
  }
  return `${labels.slice(0, 3).join(', ')} +${labels.length - 3}`;
}
