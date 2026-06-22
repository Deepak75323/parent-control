-- Track delivery (claim) attempts so stale DELIVERED commands can be requeued up to max retries.

ALTER TABLE device_commands
    ADD COLUMN IF NOT EXISTS delivery_attempt_count INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN device_commands.delivery_attempt_count IS
    'Times this command entered DELIVERED via atomic claim; used for stale requeue (max 3).';
