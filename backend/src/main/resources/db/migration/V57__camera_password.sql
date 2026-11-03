-- Per-parent camera unlock password (separate credential from the chat decrypt code).
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS camera_password_hash VARCHAR(100);
