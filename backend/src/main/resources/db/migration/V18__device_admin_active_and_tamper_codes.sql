ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS device_admin_active BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS tamper_unlock_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    parent_profile_id UUID NOT NULL,
    code VARCHAR(8) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tamper_unlock_codes_device_active
    ON tamper_unlock_codes (device_id, expires_at)
    WHERE used_at IS NULL;
