ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS accessibility_service_connected BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN devices.accessibility_service_connected IS
    'Runtime AccessibilityService instance connected (distinct from accessibility_enabled in system settings).';
