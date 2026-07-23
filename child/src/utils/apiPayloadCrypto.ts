/**
 * Airtel-compatible AES-CBC payload encryption.
 * Key/IV from env only — never hardcode secrets in source.
 * Wire format: { "data": "<Base64 ciphertext>" }.
 */
import CryptoJS from 'crypto-js';

function requireSecret(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(
      `${name} is not set. Add it to child/.env (or the process env) — do not commit the real value.`,
    );
  }
  return value;
}

function aesKey() {
  return CryptoJS.enc.Utf8.parse(requireSecret('API_PAYLOAD_ENCRYPTION_KEY'));
}

function aesIv() {
  return CryptoJS.enc.Utf8.parse(requireSecret('API_PAYLOAD_ENCRYPTION_IV'));
}

export function encryptPayload(plaintext: string): string {
  const encrypted = CryptoJS.AES.encrypt(plaintext, aesKey(), {
    iv: aesIv(),
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  });
  return encrypted.ciphertext.toString(CryptoJS.enc.Base64);
}

export function decryptPayload(ciphertextBase64: string): string {
  const decrypted = CryptoJS.AES.decrypt(
    {ciphertext: CryptoJS.enc.Base64.parse(ciphertextBase64.trim())} as CryptoJS.lib.CipherParams,
    aesKey(),
    {
      iv: aesIv(),
      mode: CryptoJS.mode.CBC,
      padding: CryptoJS.pad.Pkcs7,
    },
  );
  let text = decrypted.toString(CryptoJS.enc.Utf8);
  const lastBrace = text.lastIndexOf('}');
  if (lastBrace >= 0) {
    text = text.substring(0, lastBrace + 1);
  }
  return text;
}

export function wrapRequestBody(data: unknown): {data: string} {
  const plaintext =
    typeof data === 'string' ? data : JSON.stringify(data ?? {});
  return {data: encryptPayload(plaintext)};
}

/** Returns decrypted JSON value, or original if not an Airtel envelope. */
export function unwrapResponseData(body: unknown): unknown {
  if (body == null || typeof body !== 'object') {
    return body;
  }
  const record = body as Record<string, unknown>;
  const keys = Object.keys(record);
  if (keys.length === 1 && keys[0] === 'data' && typeof record.data === 'string') {
    try {
      return JSON.parse(decryptPayload(record.data));
    } catch {
      return body;
    }
  }
  return body;
}

export function isApiPayloadEncryptionConfigured(): boolean {
  return Boolean(
    process.env.API_PAYLOAD_ENCRYPTION_KEY?.trim() &&
      process.env.API_PAYLOAD_ENCRYPTION_IV?.trim(),
  );
}
