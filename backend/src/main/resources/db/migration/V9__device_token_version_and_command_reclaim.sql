ALTER TABLE devices ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_device_commands_stale_delivered
    ON device_commands (device_id, status, delivered_at)
    WHERE status = 'DELIVERED';
