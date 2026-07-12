ALTER TABLE users
    ADD COLUMN IF NOT EXISTS tokens_invalidated_after TIMESTAMPTZ;
