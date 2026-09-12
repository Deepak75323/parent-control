/**
 * Send is always allowed once chat is usable — bodies are sealed as random-looking text.
 * View plaintext still requires ⋮ → Decrypt chat.
 * Create sheet auto-opens only when no decrypt code exists yet.
 */
export type DecryptSendGate = 'allow' | 'prompt-create';

export function decryptSendGate(opts: {
  /** null = status unknown; false = never created; true = code exists */
  configured: boolean | null;
}): DecryptSendGate {
  if (opts.configured === false) {
    return 'prompt-create';
  }
  return 'allow';
}

/** Auto-open the pin sheet only for first-time setup, never for unlock. */
export function shouldAutoOpenDecryptSetup(opts: {
  canDecrypt: boolean;
  configured: boolean | null;
}): boolean {
  return opts.canDecrypt && opts.configured === false;
}
