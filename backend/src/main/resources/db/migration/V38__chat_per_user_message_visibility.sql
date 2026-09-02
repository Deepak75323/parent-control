-- Per-user chat visibility: hide individual messages or clear chat locally (WhatsApp-style).

ALTER TABLE chat_room_members
    ADD COLUMN IF NOT EXISTS chat_cleared_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS chat_message_hides (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms (id),
    user_id UUID NOT NULL REFERENCES users (id),
    message_id UUID NOT NULL REFERENCES chat_messages (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_chat_message_hides_user_message UNIQUE (user_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_hides_user_room
    ON chat_message_hides (user_id, room_id);
