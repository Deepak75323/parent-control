type Rng = () => number;

/** Must stay in sync with backend ChatNicknameGenerator.java */
const NICKNAME_ADJECTIVES = [
  'golden',
  'silver',
  'gentle',
  'radiant',
  'serene',
  'mystic',
  'crystal',
  'lunar',
  'solar',
  'emerald',
  'sapphire',
  'amber',
  'coral',
  'ivory',
  'pearl',
  'azure',
  'violet',
  'opal',
  'starlit',
  'moonlit',
  'luminous',
  'tranquil',
  'ethereal',
  'velvet',
  'graceful',
  'blossoming',
  'shimmering',
  'harmonious',
  'enchanted',
  'dawn',
  'dusk',
  'wild',
] as const;

const NICKNAME_NOUNS = [
  'aurora',
  'willow',
  'cedar',
  'sage',
  'river',
  'meadow',
  'falcon',
  'lark',
  'swan',
  'dove',
  'orchid',
  'lily',
  'jasmine',
  'violet',
  'rose',
  'lotus',
  'blossom',
  'breeze',
  'horizon',
  'comet',
  'nova',
  'star',
  'moon',
  'cloud',
  'tide',
  'brook',
  'glen',
  'vale',
  'peak',
  'grove',
  'harbor',
  'lantern',
] as const;

function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

function createRng(seed: string): Rng {
  let state = 0;
  for (let index = 0; index < seed.length; index += 1) {
    state = (state * 31 + seed.charCodeAt(index)) >>> 0;
  }
  if (state === 0) {
    state = 1;
  }
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 2 ** 32;
  };
}

function capitalize(word: string): string {
  return word.charAt(0).toUpperCase() + word.slice(1);
}

/** One stable, beautiful nickname per email (e.g. Golden Aurora). */
export function chatDisplayNickname(email: string): string {
  const normalized = normalizeEmail(email);
  if (!normalized) {
    return 'Unknown Parent';
  }
  const rng = createRng(normalized);
  const adjective =
    NICKNAME_ADJECTIVES[Math.floor(rng() * NICKNAME_ADJECTIVES.length)] ?? NICKNAME_ADJECTIVES[0];
  const noun = NICKNAME_NOUNS[Math.floor(rng() * NICKNAME_NOUNS.length)] ?? NICKNAME_NOUNS[0];
  return `${capitalize(adjective)} ${capitalize(noun)}`;
}

export function resolveChatDisplayNickname(
  nickname: string | null | undefined,
  emailOrLabel: string | null | undefined,
): string {
  if (nickname?.trim()) {
    return nickname.trim();
  }
  const label = emailOrLabel?.trim() ?? '';
  if (label && !label.startsWith('@') && label.includes('@')) {
    return chatDisplayNickname(label);
  }
  return 'Unknown Parent';
}
