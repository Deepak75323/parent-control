-- Distinguish Discord-style DMs from 2-person servers (e.g. "Notes").
-- Do not backfill existing rooms: friendships that pointed at a shared server will get a
-- real DM the next time openDm / accept runs (ensureDirectRoomBetween only matches
-- direct_message = true).
ALTER TABLE chat_rooms
  ADD COLUMN IF NOT EXISTS direct_message BOOLEAN NOT NULL DEFAULT FALSE;
