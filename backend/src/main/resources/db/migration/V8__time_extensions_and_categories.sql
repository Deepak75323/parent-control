ALTER TABLE device_installed_apps
    ADD COLUMN IF NOT EXISTS app_category VARCHAR(100);

ALTER TABLE screen_time_rules
    ADD COLUMN IF NOT EXISTS weekly_schedule_json JSONB NOT NULL DEFAULT '{}';

CREATE TABLE time_extension_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id           UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    parent_profile_id   UUID NOT NULL REFERENCES parent_profiles (id),
    package_name        VARCHAR(512) NOT NULL,
    app_label           VARCHAR(255),
    requested_minutes   INT NOT NULL DEFAULT 30,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    grant_minutes       INT,
    granted_until       TIMESTAMPTZ,
    responded_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_time_extension_device_status
    ON time_extension_requests (device_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_time_extension_parent_pending
    ON time_extension_requests (parent_profile_id, status)
    WHERE deleted_at IS NULL AND status = 'PENDING';
