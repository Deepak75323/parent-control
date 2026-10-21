-- Per-parent PIN required to review soft-deleted Notes (owner view).
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS deleted_messages_pin_hash VARCHAR(100),
  ADD COLUMN IF NOT EXISTS deleted_messages_pin_unlocked BOOLEAN NOT NULL DEFAULT FALSE;
