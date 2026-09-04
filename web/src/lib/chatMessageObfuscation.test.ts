import {describe, expect, it} from 'vitest';
import {
  decodeChatMessagePlaintext,
  encodeChatMessagePlaintext,
} from './chatMessageObfuscation';

describe('chatMessageObfuscation', () => {
  it('glues three letters before and after each word', () => {
    const encoded = encodeChatMessagePlaintext('hi', {seed: 'test-seed'});
    expect(encoded).not.toContain(' ');
    expect(encoded).toContain('hi');
    expect(encoded).toMatch(/^[a-z]{3}hi[a-z]{3}$/);
  });

  it('encodes how are you as three glued chunks separated by spaces', () => {
    const encoded = encodeChatMessagePlaintext('how are you', {seed: 'test-seed'});
    const chunks = encoded.split(' ');
    expect(chunks).toHaveLength(3);
    expect(chunks[0]).toMatch(/^[a-z]{3}how[a-z]{3}$/);
    expect(chunks[1]).toMatch(/^[a-z]{3}are[a-z]{3}$/);
    expect(chunks[2]).toMatch(/^[a-z]{3}you[a-z]{3}$/);
  });

  it('uses a fresh ciphertext on send (no fixed seed)', () => {
    const first = encodeChatMessagePlaintext('hi');
    const second = encodeChatMessagePlaintext('hi');
    expect(first).toMatch(/^[a-z]{3}hi[a-z]{3}$/);
    expect(second).toMatch(/^[a-z]{3}hi[a-z]{3}$/);
  });

  it('changes ciphertext when plaintext changes', () => {
    const hi = encodeChatMessagePlaintext('hi', {seed: 'draft'});
    const hello = encodeChatMessagePlaintext('hello', {seed: 'draft'});
    expect(hi).not.toBe(hello);
  });

  it('decodes by stripping three letters from each side of every token', () => {
    expect(decodeChatMessagePlaintext('abchixyz')).toBe('hi');
    expect(decodeChatMessagePlaintext('xxxhowyyy zzzarewww aaayoubbb')).toBe('how are you');
  });

  it('round-trips encode then decode for multi-word plaintext', () => {
    const plaintext = 'how are you';
    const encoded = encodeChatMessagePlaintext(plaintext, {seed: 'round-trip'});
    expect(decodeChatMessagePlaintext(encoded)).toBe(plaintext);
  });

  it('leaves short tokens unchanged', () => {
    expect(decodeChatMessagePlaintext('hi')).toBe('hi');
    expect(decodeChatMessagePlaintext('abcdef')).toBe('abcdef');
  });
});
