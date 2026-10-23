export type StickerPack = {
  id: string;
  label: string;
  icon: string;
  stickers: string[];
};

/** Curated emoji sticker packs — sent as note text, rendered large in the thread. */
export const CHAT_STICKER_PACKS: StickerPack[] = [
  {
    id: 'reactions',
    label: 'Reactions',
    icon: '😍',
    stickers: ['😍', '🥰', '😂', '🤣', '😭', '🥺', '😎', '🤩', '😤', '😡', '😱', '🤯', '😴', '🤗', '🫡', '🫠'],
  },
  {
    id: 'gestures',
    label: 'Gestures',
    icon: '👍',
    stickers: ['👍', '👎', '👏', '🙌', '🤝', '✌️', '🤞', '🤟', '🤘', '👌', '💪', '🙏', '🫶', '👋', '🤙', '👀'],
  },
  {
    id: 'hearts',
    label: 'Hearts',
    icon: '❤️',
    stickers: ['❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '💕', '💞', '💖', '💘', '💝', '💔', '❤️‍🔥', '✨'],
  },
  {
    id: 'fun',
    label: 'Fun',
    icon: '🎉',
    stickers: ['🎉', '🎊', '🎈', '🎁', '🥳', '🎂', '🍰', '🍕', '🍔', '☕', '🍺', '🏆', '🥇', '⚽', '🎮', '🎵'],
  },
  {
    id: 'animals',
    label: 'Animals',
    icon: '🐶',
    stickers: ['🐶', '🐱', '🐭', '🐹', '🐰', '🦊', '🐻', '🐼', '🐨', '🐯', '🦁', '🐸', '🐵', '🐔', '🐧', '🦄'],
  },
];

export function defaultStickerPackId(packs: StickerPack[] = CHAT_STICKER_PACKS): string {
  return packs[0]?.id ?? 'reactions';
}

/** True when a note is a single sticker-sized emoji (no other text). */
export function isStickerOnlyMessage(body: string): boolean {
  const trimmed = body.trim();
  if (!trimmed || trimmed.length > 16) {
    return false;
  }
  if (typeof Intl !== 'undefined' && 'Segmenter' in Intl) {
    const graphemes = [
      ...new Intl.Segmenter(undefined, {granularity: 'grapheme'}).segment(trimmed),
    ];
    return graphemes.length === 1;
  }
  // Fallback: no spaces/newlines and short enough to be one emoji (incl. ZWJ).
  return !/\s/.test(trimmed);
}
