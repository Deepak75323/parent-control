ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS trust_level VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS trust_score INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS trust_signals_json JSONB,
    ADD COLUMN IF NOT EXISTS integrity_verdict_json JSONB,
    ADD COLUMN IF NOT EXISTS last_trust_evaluated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS trust_violation_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_devices_trust_level ON devices (trust_level);
