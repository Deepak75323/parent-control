-- Room-wide clear history watermark. Messages created at or before this instant
-- are hidden for everyone (no "this message was deleted" tombstones).
ALTER TABLE chat_rooms
  ADD COLUMN IF NOT EXISTS history_cleared_at TIMESTAMPTZ;
