-- Public PBKDF2 salt for client-side AES chat crypto (filled lazily by the API).
ALTER TABLE chat_decrypt_settings
  ADD COLUMN IF NOT EXISTS crypto_salt VARCHAR(64);
