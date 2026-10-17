-- Owner can share the Discord-style Members panel with other room members.
-- Default false: only the owner sees the new Members UI until they share it.
ALTER TABLE chat_rooms
  ADD COLUMN IF NOT EXISTS members_panel_shared BOOLEAN NOT NULL DEFAULT FALSE;
