import CryptoJS from 'crypto-js';

export function signDeviceRequest(
  secret: string,
  method: string,
  path: string,
  body: string,
): {timestamp: string; nonce: string; signature: string} {
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const nonce = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const bodyHash = CryptoJS.SHA256(body).toString(CryptoJS.enc.Hex);
  const canonical = `${method.toUpperCase()}\n${path}\n${timestamp}\n${bodyHash}`;
  const signature = CryptoJS.HmacSHA256(canonical, secret).toString(CryptoJS.enc.Base64);
  return {timestamp, nonce, signature};
}
