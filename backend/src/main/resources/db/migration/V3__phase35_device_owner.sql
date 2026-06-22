-- Phase 3.5: Device Owner capabilities, audit, parent PIN usage tracking

ALTER TABLE devices
    ADD COLUMN device_owner_active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN media_projection_granted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN silent_screen_capable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN protection_level VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN device_owner_provisioned_at TIMESTAMPTZ;

CREATE TABLE device_provisioning_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provisioning_events_device ON device_provisioning_events (device_id, created_at DESC);

CREATE TABLE screen_capture_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    session_id UUID,
    stream_type VARCHAR(32) NOT NULL,
    capture_mode VARCHAR(32) NOT NULL,
    parent_user_id UUID,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ
);

CREATE INDEX idx_screen_capture_audit_device ON screen_capture_audit_log (device_id, started_at DESC);

ALTER TABLE users
    ADD COLUMN silent_stream_pin_unlocked BOOLEAN NOT NULL DEFAULT FALSE;
