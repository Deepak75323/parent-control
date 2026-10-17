import {CHAT_MESSAGE_PLAINTEXT_MAX} from './chatMessageLimits';
import {plaintextFitsSealedLimit} from './chatMessageSealedLength';

export type MergedEmojiArt = {
  id: string;
  label: string;
  tags: string[];
  art: string;
};

export type MergedEmojiCollection = {
  id: string;
  name: string;
  icon: string;
  items: MergedEmojiArt[];
};

const CUSTOM_KEY = 'parentChat.customMergedEmojis';
const MAX_CUSTOM = 40;

const LOVE_PERCENT_WAVE = `I love you 1%❤
 I love you 2%🧡
  I love you 3%💛
   I love you 4%💚
    I love you 5%💙
     I love you 6%💜
      I love you 7%💗
       I love you 8%❤
        I love you 9%🧡
       I love you 10%💛
      I love you 11%💚
     I love you 12%💙
    I love  you 13%💜
   I love  you 14%💗
  I love you 15%❤
 I love you 16%🧡
I love you 17%💛
 I love you 18%💚
  I love you 19%💙
   I love you 20%💜
    I love you 21%💗
     I love you 22%❤
      I love you 23%🧡
       I love you 24%💛
        I love you 25%💚
         I love you 26%💙
        I love you 27%💜
       I love you 28%💗
      I love you 29%❤
     I love you 30%🧡
    I love you 31%💛
   I love you 32%💚
  I love you 33%💙
 I love you 34%💜
  I love you 35%💗
   I love you 36%❤
    I love you 37%🧡
     I love you 38%💛
      I love you 39%💚
       I love you 40%💙
        I love you 41%💜
         I love you 42%💗
          I love you 43%❤
           I love you 44%🧡
            I love you 45%💛
           I love you 46%💚
          I love you 47%💙
         I love you 48%💜
        I love you 49%💗
       I love you 50%❤
      I love you 51%🧡
     I love you 52%💛
    I love you 53%💚
     I love you 54%💙
      I love you 55%💜
       I love you 56%💗
        I love you 57%❤
         I love you 58%🧡
          I love you 59%💛
           I love you 60%💚
            I love you 61%💙
             I love you 62%💜
              I love you 63%💗
             I love you 64%❤
            I love you 65%🧡
           I love you 66%💛
          I love you 67%💚
         I love you 68%💙
        I love you 69%💜
       I love you 69%💗
      I love you 70%❤
       I love you 71%🧡
        I love you 72%💛
         I love you 73%💚
          I love you 74%💙
           I love you 75%💜
            I love you 76%💗
             I love you 77%❤
              I love you 78%🧡
             I love you 79%💛
            I love you 80%💚
           I love you 81%💙
          I love you 82%💜
         I love you 83%💗
        I love you 84%❤
       I love you 85%🧡
      I love you 86%💛
       I love you 87%💚
        I love you 88%💙
         I love you 89%💜
          I love you 90%💗
           I love you 91%❤
            I love you 92%🧡
           I love you 93%💛
          I love you 94%💚
         I love you 95%💙
        I love you 96%💜
       I love you 97%💗
      I love you 98%❤
       I love you 99%🧡
    I LOVE YOU 100%💖`;

const LOVE_PERCENT_STRAIGHT = Array.from({length: 100}, (_, index) => `I Love You 💕${index + 1}%`).join(
  '\n',
);

const LOVE_LETTERS = `❤️❤️❤️❤️
       ❤️
       ❤️
       ❤️
❤️❤️❤️❤️

❤️              ❤️❤️❤️
❤️             ❤️       ❤️
❤️             ❤️       ❤️
❤️❤️❤️    ❤️❤️❤️

❤️             ❤️   ❤️❤️❤️
  ❤️         ❤️      ❤️
     ❤️    ❤️        ❤️❤️❤️
        ❤️❤️         ❤️
           ❤️            ❤️❤️❤️

❤️        ❤️❤️❤️❤️❤️      ❤️
   ❤️   ❤️  ❤️      ❤️❤️     ❤️
     ❤️❤️   ❤️      ❤️❤️     ❤️
        ❤️     ❤️      ❤️ ❤️   ❤️
        ❤️     ❤️❤️❤️      ❤️

❤️❤️❤️❤️❤️❤️❤️❤️❤️
     ❤️      ❤️     ❤️❤️      ❤️
     ❤️      ❤️     ❤️❤️      ❤️
     ❤️      ❤️❤️❤️❤️❤️❤️`;

const NESTED_HEART = `🖤🖤🖤🖤🖤🖤🖤🖤🖤🖤🖤🖤🖤🖤🖤
🖤🖤🖤🩷🩷🩷🖤🖤🖤🩷🩷🩷🖤🖤🖤
🖤🖤🩷🤍🤍🤍🩷🖤🩷🤍🤍🤍🩷🖤🖤
🖤🩷🤍🩶🩶🩶🤍🩷🤍🩶🩶🩶🤍🩷🖤
🩷🤍🩶🩷🩷🩷🩶🤍🩶🩷🩷🩷🩶🤍🩷
🩷🤍🩶🩷🤍🤍🩷🩶🩷🤍🤍🩷🩶🤍🩷
🩷🤍🩶🩷🤍🩶🤍🩷🤍🩶🤍🩷🩶🤍🩷
🩷🤍🩶🩷🤍🩶🩶🤍🩶🩶🤍🩷🩶🤍🩷
🖤🩷🤍🩶🩷🤍🩶🩶🩶🤍🩷🩶🤍🩷🖤
🖤🖤🩷🤍🩶🩷🤍🩶🤍🩷🩶🤍🩷🖤🖤
🖤🖤🖤🩷🤍🩶🩷🤍🩷🩶🤍🩷🖤🖤🖤
🖤🖤🖤🖤🩷🤍🩶🩷🩶🤍🩷🖤🖤🖤🖤
🖤🖤🖤🖤🖤🩷🤍🩶🤍🩷🖤🖤🖤🖤🖤
🖤🖤🖤🖤🖤🖤🩷🤍🩷🖤🖤🖤🖤🖤🖤
🖤🖤🖤🖤🖤🖤🖤🩷🖤🖤🖤🖤🖤🖤🖤`;

const SPARKLE_HEART = `✨✨✨✨✨✨✨✨✨✨✨
✨✨✨❤❤❤❤❤✨✨✨
✨✨✨❤❤❤❤❤✨✨✨
✨✨✨✨❤❤❤✨✨✨✨
✨✨✨✨❤❤❤✨✨✨✨
✨✨✨✨❤❤❤✨✨✨✨
✨✨✨✨❤❤❤✨✨✨✨
✨✨✨✨❤❤❤✨✨✨✨
✨✨✨❤❤❤❤❤✨✨✨
✨✨✨❤❤❤❤❤✨✨✨
✨✨✨✨✨✨✨✨✨✨✨`;

const TEXT_HEART = `      I love you
   I love you I love you
 I love you       I love you
I love you         I love you
 I love you       I love you
   I love you I love you
      I love you
         💖🖤💖`;

const CAT_HEART = `^,,,^
( ・・ )
/ づ♡ I love you`;

const ROSE_WALL = Array.from({length: 8}, () => '🌹'.repeat(18)).join('\n');

const E_LETTER = `❤️❤️❤️❤️
       ❤️
       ❤️
       ❤️
❤️❤️❤️❤️`;

export const BUILTIN_MERGED_EMOJI_COLLECTIONS: MergedEmojiCollection[] = [
  {
    id: 'love',
    name: 'I love you',
    icon: '💕',
    items: [
      {
        id: 'love-percent-wave',
        label: 'Love percent wave',
        tags: ['i love you', 'heart', 'cute'],
        art: LOVE_PERCENT_WAVE,
      },
      {
        id: 'love-percent-straight',
        label: 'Love percent list',
        tags: ['i love you', 'heart'],
        art: LOVE_PERCENT_STRAIGHT,
      },
      {
        id: 'love-letters',
        label: 'Love letters',
        tags: ['i love you', 'heart'],
        art: LOVE_LETTERS,
      },
      {
        id: 'text-heart',
        label: 'Text heart',
        tags: ['i love you', 'heart', 'cute'],
        art: TEXT_HEART,
      },
    ],
  },
  {
    id: 'heart',
    name: 'Heart',
    icon: '❤️',
    items: [
      {
        id: 'nested-heart',
        label: 'Nested heart',
        tags: ['heart', 'cute'],
        art: NESTED_HEART,
      },
      {
        id: 'sparkle-heart',
        label: 'Sparkle heart',
        tags: ['heart', 'cute'],
        art: SPARKLE_HEART,
      },
      {
        id: 'e-letter',
        label: 'Heart E',
        tags: ['heart'],
        art: E_LETTER,
      },
      {
        id: 'rose-wall',
        label: 'Rose wall',
        tags: ['heart', 'cute'],
        art: ROSE_WALL,
      },
    ],
  },
  {
    id: 'cute',
    name: 'Cute',
    icon: '🐱',
    items: [
      {
        id: 'cat-heart',
        label: 'Cat sending heart',
        tags: ['cat', 'cute', 'i love you', 'kitten', 'cat sending heart'],
        art: CAT_HEART,
      },
    ],
  },
];

export function mergedEmojiFitsLimit(art: string): boolean {
  return (
    art.length > 0 &&
    art.length <= CHAT_MESSAGE_PLAINTEXT_MAX &&
    plaintextFitsSealedLimit(art)
  );
}

export function previewMergedEmoji(art: string, maxChars = 48): string {
  const compact = art.replace(/\s+/g, ' ').trim();
  if (compact.length <= maxChars) {
    return compact;
  }
  return `${compact.slice(0, Math.max(0, maxChars - 1))}…`;
}

/** Multi-line preview for paste box and collection cards (keeps row order). */
export function previewMergedEmojiLines(art: string, maxLines = 5, maxLineChars = 120): string {
  const lines = art.replace(/\r\n/g, '\n').split('\n');
  const shown = lines.slice(0, maxLines).map(line => {
    if (line.length <= maxLineChars) {
      return line;
    }
    return `${line.slice(0, maxLineChars - 1)}…`;
  });
  if (lines.length > maxLines) {
    shown.push(`… ${lines.length - maxLines} more line${lines.length - maxLines === 1 ? '' : 's'}`);
  }
  return shown.join('\n');
}

export function loadCustomMergedEmojis(): MergedEmojiArt[] {
  try {
    const raw = localStorage.getItem(CUSTOM_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter(
        (item): item is MergedEmojiArt =>
          item != null &&
          typeof item === 'object' &&
          typeof item.id === 'string' &&
          typeof item.label === 'string' &&
          typeof item.art === 'string' &&
          Array.isArray(item.tags),
      )
      .filter(item => mergedEmojiFitsLimit(item.art))
      .slice(0, MAX_CUSTOM);
  } catch {
    return [];
  }
}

function persistCustom(items: MergedEmojiArt[]) {
  try {
    localStorage.setItem(CUSTOM_KEY, JSON.stringify(items.slice(0, MAX_CUSTOM)));
  } catch {
    /* quota / private mode */
  }
}

export function addCustomMergedEmoji(input: {
  label: string;
  art: string;
  tags?: string[];
}): MergedEmojiArt {
  const art = input.art.trimEnd();
  const label = input.label.trim() || 'My emoji';
  if (!mergedEmojiFitsLimit(art)) {
    throw new Error(
      `Merged emoji is too long (max ${CHAT_MESSAGE_PLAINTEXT_MAX.toLocaleString()} characters, or too large after sealing)`,
    );
  }
  const item: MergedEmojiArt = {
    id: `custom-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    label,
    tags: (input.tags ?? ['my collection']).map(tag => tag.trim().toLowerCase()).filter(Boolean),
    art,
  };
  const next = [item, ...loadCustomMergedEmojis().filter(existing => existing.art !== art)];
  persistCustom(next);
  return item;
}

export function removeCustomMergedEmoji(id: string): void {
  persistCustom(loadCustomMergedEmojis().filter(item => item.id !== id));
}

export function allMergedEmojiCollections(
  customItems: MergedEmojiArt[] = loadCustomMergedEmojis(),
): MergedEmojiCollection[] {
  const customCollection: MergedEmojiCollection = {
    id: 'my-collection',
    name: 'My collection',
    icon: '📌',
    items: customItems,
  };
  return [...BUILTIN_MERGED_EMOJI_COLLECTIONS, customCollection];
}

export function filterMergedEmojiItems(
  collections: MergedEmojiCollection[],
  tag: string | null,
): MergedEmojiArt[] {
  const items = collections.flatMap(collection => collection.items);
  if (!tag) {
    return items;
  }
  const needle = tag.trim().toLowerCase();
  return items.filter(
    item =>
      item.tags.some(entry => entry.includes(needle)) ||
      item.label.toLowerCase().includes(needle),
  );
}

export const MERGED_EMOJI_FILTER_TAGS = [
  'all',
  'i love you',
  'heart',
  'cute',
  'cat',
  'my collection',
] as const;
