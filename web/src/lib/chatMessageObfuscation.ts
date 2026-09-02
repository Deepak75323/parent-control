const LETTERS_PER_SIDE = 3;

const ALPHABET = 'abcdefghijklmnopqrstuvwxyz';

type Rng = () => number;

function splitMessageWords(text: string): string[] {
  return text.trim().split(/\s+/).filter(Boolean);
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

function pickRandomLetters(count: number, rng: Rng): string {
  let result = '';
  for (let index = 0; index < count; index += 1) {
    result += ALPHABET[Math.floor(rng() * ALPHABET.length)] ?? 'a';
  }
  return result;
}

function encodeWord(word: string, rng: Rng): string {
  return pickRandomLetters(LETTERS_PER_SIDE, rng) + word + pickRandomLetters(LETTERS_PER_SIDE, rng);
}

type EncodeOptions = {
  seed?: string;
};

/**
 * Each word becomes one glued token: 3 letters + word + 3 letters (e.g. hi → abchixyz).
 */
export function encodeChatMessagePlaintext(plaintext: string, options?: EncodeOptions): string {
  const words = splitMessageWords(plaintext);
  if (words.length === 0) {
    return '';
  }
  const seed = options?.seed ?? `${plaintext}:${Date.now()}:${Math.random()}`;
  const rng = createRng(seed);
  return words.map(word => encodeWord(word, rng)).join(' ');
}

export const chatObfuscationConfig = {
  lettersPerSide: LETTERS_PER_SIDE,
} as const;
