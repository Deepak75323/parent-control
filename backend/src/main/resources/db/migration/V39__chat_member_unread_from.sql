ALTER TABLE chat_room_members
    ADD COLUMN IF NOT EXISTS unread_from_at TIMESTAMPTZ;
