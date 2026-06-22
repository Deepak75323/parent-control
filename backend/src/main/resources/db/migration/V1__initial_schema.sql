-- Parental Control Platform — Initial Schema (Phase 1 + forward-compatible tables)

-- =============================================================================
-- USERS & PROFILES
-- =============================================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50) NOT NULL DEFAULT 'ROLE_PARENT',
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    consent_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    parent_pin_hash VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_role ON users (role) WHERE deleted_at IS NULL;

CREATE TABLE parent_profiles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id),
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    phone       VARCHAR(50),
    timezone    VARCHAR(64) NOT NULL DEFAULT 'UTC',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_parent_profiles_user UNIQUE (user_id)
);

CREATE INDEX idx_parent_profiles_user ON parent_profiles (user_id);

CREATE TABLE child_profiles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_profile_id UUID NOT NULL REFERENCES parent_profiles (id),
    display_name      VARCHAR(100) NOT NULL,
    date_of_birth     DATE,
    avatar_url        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_child_profiles_parent ON child_profiles (parent_profile_id);

-- =============================================================================
-- AUTH
-- =============================================================================

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id) WHERE revoked = FALSE;

-- =============================================================================
-- DEVICES & PAIRING
-- =============================================================================

CREATE TABLE devices (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_profile_id UUID NOT NULL REFERENCES parent_profiles (id),
    child_profile_id  UUID REFERENCES child_profiles (id),
    device_name       VARCHAR(255) NOT NULL,
    device_model      VARCHAR(255),
    os_version        VARCHAR(100),
    app_version       VARCHAR(50),
    android_id        VARCHAR(255),
    fcm_token         TEXT,
    status            VARCHAR(50) NOT NULL DEFAULT 'PENDING_PAIRING',
    battery_level     INT,
    is_charging       BOOLEAN,
    network_type      VARCHAR(50),
    last_seen_at      TIMESTAMPTZ,
    last_latitude     DOUBLE PRECISION,
    last_longitude    DOUBLE PRECISION,
    foreground_app    VARCHAR(512),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT uq_devices_android_id UNIQUE (android_id)
);

CREATE INDEX idx_devices_parent ON devices (parent_profile_id);
CREATE INDEX idx_devices_status ON devices (status);
CREATE INDEX idx_devices_last_seen ON devices (last_seen_at DESC);

CREATE TABLE pairing_codes (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(8) NOT NULL,
    parent_profile_id UUID NOT NULL REFERENCES parent_profiles (id),
    child_profile_id  UUID REFERENCES child_profiles (id),
    expires_at        TIMESTAMPTZ NOT NULL,
    used_at           TIMESTAMPTZ,
    device_id         UUID REFERENCES devices (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT uq_pairing_codes_code UNIQUE (code)
);

CREATE INDEX idx_pairing_codes_parent ON pairing_codes (parent_profile_id);
CREATE INDEX idx_pairing_codes_expires ON pairing_codes (expires_at) WHERE used_at IS NULL;

CREATE TABLE device_permission_status (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id                    UUID NOT NULL REFERENCES devices (id),
    accessibility_granted        BOOLEAN NOT NULL DEFAULT FALSE,
    notification_listener_granted BOOLEAN NOT NULL DEFAULT FALSE,
    usage_stats_granted          BOOLEAN NOT NULL DEFAULT FALSE,
    location_granted             BOOLEAN NOT NULL DEFAULT FALSE,
    device_admin_granted         BOOLEAN NOT NULL DEFAULT FALSE,
    overlay_granted              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at                   TIMESTAMPTZ,
    CONSTRAINT uq_device_permission_device UNIQUE (device_id)
);

-- =============================================================================
-- MONITORING (Phase 2 — schema ready)
-- =============================================================================

CREATE TABLE notification_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    UUID NOT NULL REFERENCES devices (id),
    package_name VARCHAR(512),
    title        TEXT,
    body         TEXT,
    posted_at    TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);

CREATE INDEX idx_notification_logs_device_posted ON notification_logs (device_id, posted_at DESC);

CREATE TABLE app_usage_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    UUID NOT NULL REFERENCES devices (id),
    package_name VARCHAR(512) NOT NULL,
    usage_ms     BIGINT NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);

CREATE INDEX idx_app_usage_device_recorded ON app_usage_logs (device_id, recorded_at DESC);

CREATE TABLE location_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id   UUID NOT NULL REFERENCES devices (id),
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    accuracy_m  DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_location_history_device_recorded ON location_history (device_id, recorded_at DESC);

CREATE TABLE call_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    UUID NOT NULL REFERENCES devices (id),
    phone_number VARCHAR(50),
    contact_name VARCHAR(255),
    call_type    VARCHAR(20),
    duration_sec INT,
    called_at    TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);

CREATE TABLE sms_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    UUID NOT NULL REFERENCES devices (id),
    phone_number VARCHAR(50),
    message_body TEXT,
    direction    VARCHAR(10),
    sent_at      TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);

CREATE TABLE browser_history (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id  UUID NOT NULL REFERENCES devices (id),
    url        TEXT NOT NULL,
    title      TEXT,
    visited_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE screen_sessions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id  UUID NOT NULL REFERENCES devices (id),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at   TIMESTAMPTZ,
    status     VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE camera_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id   UUID NOT NULL REFERENCES devices (id),
    camera_type VARCHAR(20),
    started_at  TIMESTAMPTZ NOT NULL,
    ended_at    TIMESTAMPTZ,
    status      VARCHAR(50),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE TABLE audio_sessions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id  UUID NOT NULL REFERENCES devices (id),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at   TIMESTAMPTZ,
    status     VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

-- =============================================================================
-- POLICIES & ALERTS (Phase 3)
-- =============================================================================

CREATE TABLE monitoring_policies (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_profile_id UUID NOT NULL REFERENCES parent_profiles (id),
    device_id         UUID REFERENCES devices (id),
    policy_type       VARCHAR(100) NOT NULL,
    config_json       JSONB NOT NULL DEFAULT '{}',
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE TABLE geofences (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_profile_id UUID NOT NULL REFERENCES parent_profiles (id),
    device_id         UUID REFERENCES devices (id),
    name              VARCHAR(255) NOT NULL,
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    radius_m          DOUBLE PRECISION NOT NULL,
    alert_on_enter    BOOLEAN NOT NULL DEFAULT TRUE,
    alert_on_exit     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE TABLE alerts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id   UUID NOT NULL REFERENCES devices (id),
    alert_type  VARCHAR(100) NOT NULL,
    severity    VARCHAR(20) NOT NULL DEFAULT 'INFO',
    title       VARCHAR(255) NOT NULL,
    message     TEXT,
    payload_json JSONB,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_alerts_device_created ON alerts (device_id, created_at DESC);

-- =============================================================================
-- BILLING (Phase 4)
-- =============================================================================

CREATE TABLE subscriptions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_profile_id UUID NOT NULL REFERENCES parent_profiles (id),
    plan_id           VARCHAR(100) NOT NULL,
    status            VARCHAR(50) NOT NULL,
    trial_ends_at     TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions (id),
    amount_cents    BIGINT NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    provider        VARCHAR(50),
    provider_ref    VARCHAR(255),
    status          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- =============================================================================
-- AUDIT
-- =============================================================================

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users (id),
    device_id   UUID REFERENCES devices (id),
    action      VARCHAR(255) NOT NULL,
    resource    VARCHAR(255),
    ip_address  VARCHAR(45),
    metadata_json JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_audit_logs_user_created ON audit_logs (user_id, created_at DESC);
