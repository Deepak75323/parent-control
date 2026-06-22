-- Execution fingerprint: records that the child device ran a command before ACK arrives.
-- Prevents reclaimStaleDelivered from re-queuing already-executed commands.

ALTER TABLE device_commands
    ADD COLUMN IF NOT EXISTS executed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS execution_success BOOLEAN,
    ADD COLUMN IF NOT EXISTS execution_error TEXT;

COMMENT ON COLUMN device_commands.executed_at IS
    'When the child reported command execution (before ACK). Idempotency guard for stale reclaim.';
COMMENT ON COLUMN device_commands.execution_success IS
    'Outcome reported at execution time; used to auto-complete if ACK never arrives.';
COMMENT ON COLUMN device_commands.execution_error IS
    'Error message reported at execution time when execution_success is false.';

CREATE INDEX IF NOT EXISTS idx_device_commands_executed_awaiting_ack
    ON device_commands (device_id, status, executed_at)
    WHERE status = 'DELIVERED' AND executed_at IS NOT NULL;
