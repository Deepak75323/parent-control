import {
  decryptPayload,
  encryptPayload,
  unwrapResponseData,
  wrapRequestBody,
} from '../utils/apiPayloadCrypto';

describe('apiPayloadCrypto (Airtel AES-CBC)', () => {
  const prevKey = process.env.API_PAYLOAD_ENCRYPTION_KEY;
  const prevIv = process.env.API_PAYLOAD_ENCRYPTION_IV;

  beforeAll(() => {
    // Test-only 16-byte values — not production secrets.
    process.env.API_PAYLOAD_ENCRYPTION_KEY = '0123456789abcdef';
    process.env.API_PAYLOAD_ENCRYPTION_IV = 'fedcba9876543210';
  });

  afterAll(() => {
    process.env.API_PAYLOAD_ENCRYPTION_KEY = prevKey;
    process.env.API_PAYLOAD_ENCRYPTION_IV = prevIv;
  });

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
