-- Unique family handle per parent (case-insensitive). Used by child first-time login.
ALTER TABLE parent_profiles
    ADD COLUMN IF NOT EXISTS family_handle VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS uq_parent_profiles_family_handle_lower
    ON parent_profiles (LOWER(family_handle))
    WHERE family_handle IS NOT NULL AND deleted_at IS NULL;
