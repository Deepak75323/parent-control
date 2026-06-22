-- P1-10: OEM certification and device readiness telemetry
ALTER TABLE devices ADD COLUMN IF NOT EXISTS oem_vendor VARCHAR(32);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS android_sdk_int INTEGER;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_readiness_score INTEGER NOT NULL DEFAULT 0;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS oem_setup_complete BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS oem_diagnostics_json JSONB;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS oem_failure_classifications JSONB;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS last_certification_at TIMESTAMPTZ;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS certification_status VARCHAR(32);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS certification_score INTEGER;

CREATE INDEX IF NOT EXISTS idx_devices_oem_vendor ON devices (oem_vendor);
CREATE INDEX IF NOT EXISTS idx_devices_readiness_score ON devices (device_readiness_score);
