-- UPI-style client analytics: screen impressions, clicks, ingress, errors, back navigation.
CREATE TABLE client_analytics_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type          VARCHAR(32)  NOT NULL,
    user_id             UUID,
    device_id           UUID,
    event_type          VARCHAR(32)  NOT NULL,
    section             VARCHAR(128) NOT NULL,
    sub_section         VARCHAR(128),
    event_label         VARCHAR(256),
    screen              VARCHAR(128),
    correlation_id      VARCHAR(64),
    attributes_json     JSONB,
    client_timestamp_ms BIGINT       NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_client_analytics_user_created
    ON client_analytics_events (user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_client_analytics_device_created
    ON client_analytics_events (device_id, created_at DESC)
    WHERE device_id IS NOT NULL;

CREATE INDEX idx_client_analytics_correlation
    ON client_analytics_events (correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX idx_client_analytics_section_created
    ON client_analytics_events (section, event_type, created_at DESC);
