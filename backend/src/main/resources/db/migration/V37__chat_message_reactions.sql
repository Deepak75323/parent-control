-- One emoji reaction per user per message (WhatsApp-style).

CREATE TABLE IF NOT EXISTS chat_message_reactions (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms (id),
    message_id UUID NOT NULL REFERENCES chat_messages (id),
    user_id UUID NOT NULL REFERENCES users (id),
    emoji VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_chat_message_reactions_message_user UNIQUE (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_reactions_message
    ON chat_message_reactions (message_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_message_reactions_room
    ON chat_message_reactions (room_id)
    WHERE deleted_at IS NULL;
