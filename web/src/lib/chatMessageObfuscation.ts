const LETTERS_PER_SIDE = 3;

const ALPHABET = 'abcdefghijklmnopqrstuvwxyz';

type Rng = () => number;

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

function encodeLine(line: string, rng: Rng): string {
  const leadingMatch = line.match(/^\s*/);
  const leading = leadingMatch?.[0] ?? '';
  const core = line.slice(leading.length).replace(/\s+$/, '');
  const words = core.split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return leading.length > 0 ? leading : '';
  }
  return `${leading}${words.map(word => encodeWord(word, rng)).join(' ')}`;
}

function decodeLine(line: string): string {
  const leadingMatch = line.match(/^\s*/);
  const leading = leadingMatch?.[0] ?? '';
  const core = line.slice(leading.length).replace(/\s+$/, '');
  const words = core.split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return leading.length > 0 ? leading : '';
  }
  return `${leading}${words.map(decodeWord).join(' ')}`;
}

type EncodeOptions = {
  seed?: string;
};

/**
 * Each word becomes one glued token: 3 random letters + word + 3 random letters
 * (e.g. hi → abchixyz). Newlines and leading indentation are preserved for emoji art.
 */
export function encodeChatMessagePlaintext(plaintext: string, options?: EncodeOptions): string {
  if (!plaintext.trim()) {
    return '';
  }
  const seed = options?.seed ?? `${plaintext}:${Date.now()}:${Math.random()}`;
  const rng = createRng(seed);
  return plaintext.split('\n').map(line => encodeLine(line, rng)).join('\n');
}

function decodeWord(token: string): string {
  if (token.length <= LETTERS_PER_SIDE * 2) {
    return token;
  }
  return token.slice(LETTERS_PER_SIDE, -LETTERS_PER_SIDE);
}

/**
 * Inverse of encode: strip 3 letters from each side of every whitespace-separated token.
 * Tokens too short to have been padded are left unchanged. Line breaks are preserved.
 */
export function decodeChatMessagePlaintext(obfuscated: string): string {
  if (!obfuscated.trim()) {
    return '';
  }
  return obfuscated.split('\n').map(decodeLine).join('\n');
}

export const chatObfuscationConfig = {
  lettersPerSide: LETTERS_PER_SIDE,
} as const;
