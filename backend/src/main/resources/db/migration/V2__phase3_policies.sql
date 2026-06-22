-- Phase 3: Policy engine, violations, sync state, WebRTC signaling

CREATE TABLE policies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_profile_id   UUID NOT NULL REFERENCES parent_profiles (id),
    name                VARCHAR(255) NOT NULL,
    policy_type         VARCHAR(50) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    version             INT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_policies_parent ON policies (parent_profile_id);
CREATE INDEX idx_policies_type ON policies (policy_type) WHERE deleted_at IS NULL;

CREATE TABLE app_block_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL REFERENCES policies (id),
    package_name    VARCHAR(512) NOT NULL,
    schedule_type   VARCHAR(30) NOT NULL DEFAULT 'ALWAYS',
    schedule_json   JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_app_block_rules_policy ON app_block_rules (policy_id);

CREATE TABLE screen_time_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id           UUID NOT NULL REFERENCES policies (id),
    package_name        VARCHAR(512),
    app_category        VARCHAR(100),
    daily_limit_minutes INT NOT NULL,
    action_on_limit     VARCHAR(30) NOT NULL DEFAULT 'BLOCK_APP',
    warn_at_percent       INT NOT NULL DEFAULT 80,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_screen_time_rules_policy ON screen_time_rules (policy_id);

CREATE TABLE device_schedule_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL REFERENCES policies (id),
    schedule_type   VARCHAR(30) NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    days_of_week    JSONB NOT NULL DEFAULT '[]',
    lock_device     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_device_schedule_rules_policy ON device_schedule_rules (policy_id);

CREATE TABLE geofence_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID NOT NULL REFERENCES policies (id),
    name            VARCHAR(255) NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    radius_m        DOUBLE PRECISION NOT NULL,
    alert_on_enter  BOOLEAN NOT NULL DEFAULT TRUE,
    alert_on_exit   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_geofence_rules_policy ON geofence_rules (policy_id);

CREATE TABLE streaming_policy_rules (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id               UUID NOT NULL REFERENCES policies (id),
    screen_share_allowed    BOOLEAN NOT NULL DEFAULT TRUE,
    camera_allowed          BOOLEAN NOT NULL DEFAULT TRUE,
    audio_allowed           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT uq_streaming_policy UNIQUE (policy_id)
);

CREATE TABLE policy_assignments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id   UUID NOT NULL REFERENCES policies (id),
    device_id   UUID NOT NULL REFERENCES devices (id),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_policy_device UNIQUE (policy_id, device_id)
);

CREATE INDEX idx_policy_assignments_device ON policy_assignments (device_id) WHERE active = TRUE;

CREATE TABLE policy_sync_state (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES devices (id),
    policy_version  VARCHAR(64) NOT NULL,
    last_ack_at     TIMESTAMPTZ,
    last_sync_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_policy_sync_device UNIQUE (device_id)
);

CREATE TABLE policy_violation_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID NOT NULL REFERENCES devices (id),
    policy_id       UUID REFERENCES policies (id),
    violation_type  VARCHAR(100) NOT NULL,
    package_name    VARCHAR(512),
    details_json    JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_policy_violations_device ON policy_violation_events (device_id, occurred_at DESC);

CREATE TABLE signaling_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     VARCHAR(255) NOT NULL,
    session_id  UUID,
    sender_role VARCHAR(20) NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    payload     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_signaling_room_created ON signaling_messages (room_id, created_at DESC);

ALTER TABLE screen_sessions ADD COLUMN IF NOT EXISTS stream_type VARCHAR(30) DEFAULT 'SCREEN';
ALTER TABLE screen_sessions ADD COLUMN IF NOT EXISTS signaling_room_id VARCHAR(255);
ALTER TABLE camera_sessions ADD COLUMN IF NOT EXISTS signaling_room_id VARCHAR(255);
ALTER TABLE audio_sessions ADD COLUMN IF NOT EXISTS signaling_room_id VARCHAR(255);
