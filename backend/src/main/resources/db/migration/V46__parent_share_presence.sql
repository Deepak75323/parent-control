ALTER TABLE parent_profiles
    ADD COLUMN IF NOT EXISTS share_presence_enabled BOOLEAN NOT NULL DEFAULT TRUE;
