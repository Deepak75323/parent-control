import {describe, expect, it} from 'vitest';
import {
  decryptPayload,
  encryptPayload,
  unwrapResponseData,
  wrapRequestBody,
} from './apiPayloadCrypto';

describe('apiPayloadCrypto (Airtel AES-CBC)', () => {
  it('round-trips JSON plaintext', () => {
    const plain = JSON.stringify({email: 'a@b.c', password: 'x'});
    const cipher = encryptPayload(plain);
    expect(cipher.length).toBeGreaterThan(16);
    expect(decryptPayload(cipher)).toBe(plain);
  });

  it('wraps and unwraps envelope', () => {
    const body = wrapRequestBody({ok: true});
    expect(Object.keys(body)).toEqual(['data']);
    expect(typeof body.data).toBe('string');
    expect(unwrapResponseData(body)).toEqual({ok: true});
  });
});
