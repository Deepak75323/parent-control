-- Phase 4: HMAC signing, kiosk state, remote device commands

ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS device_hmac_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS kiosk_mode_active BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS device_commands (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    command_type    VARCHAR(64) NOT NULL,
    payload         JSONB,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_device_commands_pending
    ON device_commands (device_id, created_at DESC)
    WHERE status = 'PENDING';
