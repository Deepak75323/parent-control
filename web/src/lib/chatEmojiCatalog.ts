export type EmojiCategory = {
  id: string;
  label: string;
  icon: string;
  emojis: string[];
};

export const CHAT_EMOJI_CATEGORIES: EmojiCategory[] = [
  {
    id: 'smileys',
    label: 'Smileys',
    icon: '😀',
    emojis: [
      '😀', '😃', '😄', '😁', '😆', '😅', '🤣', '😂', '🙂', '🙃', '😉', '😊', '😇', '🥰', '😍',
      '🤩', '😘', '😗', '😚', '😙', '🥲', '😋', '😛', '😜', '🤪', '😝', '🤑', '🤗', '🤭', '🤫',
      '🤔', '🤐', '🤨', '😐', '😑', '😶', '😏', '😒', '🙄', '😬', '😮‍💨', '🤥', '😌', '😔', '😪',
      '🤤', '😴', '😷', '🤒', '🤕', '🤢', '🤮', '🤧', '🥵', '🥶', '🥴', '😵', '🤯', '🤠', '🥳',
      '😎', '🤓', '🧐', '😕', '😟', '🙁', '☹️', '😮', '😯', '😲', '😳', '🥺', '😦', '😧', '😨',
      '😰', '😥', '😢', '😭', '😱', '😖', '😣', '😞', '😓', '😩', '😫', '🥱', '😤', '😡', '😠',
    ],
  },
  {
    id: 'gestures',
    label: 'Gestures',
    icon: '👍',
    emojis: [
      '👍', '👎', '👊', '✊', '🤛', '🤜', '🤞', '✌️', '🤟', '🤘', '👌', '🤌', '🤏', '👈', '👉',
      '👆', '👇', '☝️', '✋', '🤚', '🖐️', '🖖', '👋', '🤙', '💪', '🙏', '✍️', '💅', '🤳', '💃',
      '🕺', '👏', '🙌', '🫶', '🤝', '🫡', '🫢', '🫣', '🫠',
    ],
  },
  {
    id: 'hearts',
    label: 'Hearts',
    icon: '❤️',
    emojis: [
      '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '🤎', '💔', '❤️‍🔥', '❤️‍🩹', '💕', '💞', '💓',
      '💗', '💖', '💘', '💝', '💟', '♥️', '💌', '💋', '🌹', '✨', '⭐', '🌟', '💫', '🔥', '💯',
    ],
  },
  {
    id: 'animals',
    label: 'Animals',
    icon: '🐶',
    emojis: [
      '🐶', '🐱', '🐭', '🐹', '🐰', '🦊', '🐻', '🐼', '🐨', '🐯', '🦁', '🐮', '🐷', '🐸', '🐵',
      '🐔', '🐧', '🐦', '🐤', '🦄', '🐝', '🦋', '🐛', '🐢', '🐍', '🐙', '🦑', '🐠', '🐬', '🐳',
    ],
  },
  {
    id: 'food',
    label: 'Food',
    icon: '🍕',
    emojis: [
      '🍎', '🍊', '🍋', '🍌', '🍉', '🍇', '🍓', '🫐', '🍒', '🍑', '🥭', '🍍', '🥥', '🥝', '🍅',
      '🥑', '🌽', '🌶️', '🥕', '🍞', '🥐', '🥨', '🧀', '🍳', '🍔', '🍟', '🍕', '🌭', '🥪', '🌮',
      '🌯', '🥗', '🍝', '🍜', '🍣', '🍱', '🍩', '🍪', '🎂', '🍰', '🧁', '🍫', '🍿', '☕', '🍵',
      '🧃', '🥤', '🍺', '🍻', '🥂', '🍷',
    ],
  },
  {
    id: 'activities',
    label: 'Activities',
    icon: '⚽',
    emojis: [
      '⚽', '🏀', '🏈', '⚾', '🎾', '🏐', '🏉', '🎱', '🏓', '🏸', '🥅', '🏆', '🥇', '🥈', '🥉',
      '🎯', '🎮', '🎲', '🎭', '🎨', '🎬', '🎤', '🎧', '🎼', '🎹', '🎸', '🎺', '🎻', '🎁', '🎈',
      '🎉', '🎊', '🎃', '🎄', '🎆', '🎇', '✈️', '🚀', '🛸', '🏠', '🏫', '🏥', '🏖️', '🏔️', '🗺️',
    ],
  },
  {
    id: 'objects',
    label: 'Objects',
    icon: '💡',
    emojis: [
      '⌚', '📱', '💻', '⌨️', '🖥️', '🖨️', '📷', '📸', '📹', '🎥', '📞', '☎️', '📺', '📻', '⏰',
      '🔋', '🔌', '💡', '🔦', '🕯️', '🧯', '💰', '💳', '💎', '🔧', '🔨', '⚙️', '🔑', '🗝️', '🔒',
      '📝', '✏️', '📌', '📎', '📁', '📂', '📅', '📆', '📊', '📈', '📉', '🗑️', '📦', '✉️', '📩',
    ],
  },
  {
    id: 'symbols',
    label: 'Symbols',
    icon: '✅',
    emojis: [
      '✅', '❌', '❓', '❗', '‼️', '⁉️', '💤', '💢', '💬', '👁️‍🗨️', '🗨️', '🗯️', '💭', '🔴', '🟠',
      '🟡', '🟢', '🔵', '🟣', '⚫', '⚪', '🟤', '🔺', '🔻', '🔸', '🔹', '🔶', '🔷', '➕', '➖',
      '➗', '✖️', '♾️', '💲', '©️', '®️', '™️', '🔔', '🔕', '🎵', '🎶', '⏳', '⌛', '🌈', '☀️',
      '🌙', '⭐', '☁️', '⛈️', '❄️', '☔', '⚡',
    ],
  },
];

const RECENT_EMOJIS_KEY = 'parentChat.recentEmojis';
const MAX_RECENT = 24;

export function loadRecentEmojis(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_EMOJIS_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((item): item is string => typeof item === 'string').slice(0, MAX_RECENT);
  } catch {
    return [];
  }
}

export function rememberEmoji(emoji: string): string[] {
  const next = [emoji, ...loadRecentEmojis().filter(item => item !== emoji)].slice(0, MAX_RECENT);
  try {
    localStorage.setItem(RECENT_EMOJIS_KEY, JSON.stringify(next));
  } catch {
    /* quota / private mode */
  }
  return next;
}

export function defaultEmojiCategoryId(recent: string[]): string {
  return recent.length > 0 ? 'recent' : CHAT_EMOJI_CATEGORIES[0]?.id ?? 'smileys';
}
