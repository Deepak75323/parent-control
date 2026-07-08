ALTER TABLE tamper_unlock_codes
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
