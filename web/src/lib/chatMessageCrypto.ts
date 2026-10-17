/**
 * AES-256-GCM chat body crypto. Key = PBKDF2-SHA-256(passphrase, public salt).
 * Without the passphrase the ciphertext is opaque (not word-padding).
 */
const PREFIX = 'pcp1:';
const PBKDF2_ITERATIONS = 310_000;
const IV_BYTES = 12;

function bytesToB64(bytes: ArrayBuffer | Uint8Array): string {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let binary = '';
  view.forEach(b => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary);
}

function b64ToBytes(b64: string): Uint8Array<ArrayBuffer> {
  const binary = atob(b64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    out[i] = binary.charCodeAt(i);
  }
  return out;
}

export function isEncryptedChatBody(body: string | null | undefined): boolean {
  return Boolean(body && body.startsWith(PREFIX));
}

export async function deriveChatCryptoKey(
  passphrase: string,
  saltBase64: string,
): Promise<CryptoKey> {
  const enc = new TextEncoder();
  const baseKey = await crypto.subtle.importKey(
    'raw',
    enc.encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey'],
  );
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: b64ToBytes(saltBase64),
      iterations: PBKDF2_ITERATIONS,
      hash: 'SHA-256',
    },
    baseKey,
    {name: 'AES-GCM', length: 256},
    false,
    ['encrypt', 'decrypt'],
  );
}

export async function encryptChatBody(plaintext: string, key: CryptoKey): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES));
  const cipher = await crypto.subtle.encrypt(
    {name: 'AES-GCM', iv},
    key,
    new TextEncoder().encode(plaintext),
  );
  return `${PREFIX}${bytesToB64(iv)}.${bytesToB64(cipher)}`;
}

export async function decryptChatBody(envelope: string, key: CryptoKey): Promise<string> {
  if (!isEncryptedChatBody(envelope)) {
    throw new Error('Not an encrypted chat body');
  }
  const payload = envelope.slice(PREFIX.length);
  const [ivB64, ctB64] = payload.split('.');
  if (!ivB64 || !ctB64) {
    throw new Error('Malformed encrypted chat body');
  }
  const plain = await crypto.subtle.decrypt(
    {name: 'AES-GCM', iv: b64ToBytes(ivB64)},
    key,
    b64ToBytes(ctB64),
  );
  return new TextDecoder().decode(plain);
}

export const chatCryptoConfig = {
  prefix: PREFIX,
  iterations: PBKDF2_ITERATIONS,
} as const;
