-- Offline parent-password verifier for child devices (PBKDF2 material, synced when online).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS parent_offline_pin_salt VARCHAR(64),
    ADD COLUMN IF NOT EXISTS parent_offline_pin_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS parent_offline_pin_iterations INT;
