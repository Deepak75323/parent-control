import {describe, expect, it} from 'vitest';
import {decryptSendGate, shouldAutoOpenDecryptSetup} from './chatDecryptPromptPolicy';

describe('chatDecryptPromptPolicy', () => {
  it('allows send whenever a decrypt code exists or status is unknown', () => {
    expect(decryptSendGate({configured: true})).toBe('allow');
    expect(decryptSendGate({configured: null})).toBe('allow');
  });

  it('prompts create only when decrypt code was never set', () => {
    expect(decryptSendGate({configured: false})).toBe('prompt-create');
  });

  it('auto-opens setup sheet only when code is missing', () => {
    expect(shouldAutoOpenDecryptSetup({canDecrypt: true, configured: false})).toBe(true);
    expect(shouldAutoOpenDecryptSetup({canDecrypt: true, configured: true})).toBe(false);
    expect(shouldAutoOpenDecryptSetup({canDecrypt: true, configured: null})).toBe(false);
    expect(shouldAutoOpenDecryptSetup({canDecrypt: false, configured: false})).toBe(false);
  });
});
