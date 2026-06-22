ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS android16_readiness_score INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN devices.android16_readiness_score IS 'Android 16 (API 36) migration readiness score 0-100 from child heartbeat';
