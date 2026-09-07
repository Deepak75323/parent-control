-- Discord-style channels under chat rooms (servers).
-- Each existing room gets # general (TEXT) + Lounge (VOICE); messages backfill to general.

CREATE TABLE IF NOT EXISTS chat_channels (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms (id),
    name VARCHAR(64) NOT NULL,
    channel_type VARCHAR(16) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_chat_channel_type CHECK (channel_type IN ('TEXT', 'VOICE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_channels_room_name_active
    ON chat_channels (room_id, lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_channels_room
    ON chat_channels (room_id)
    WHERE deleted_at IS NULL;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS channel_id UUID REFERENCES chat_channels (id);

ALTER TABLE chat_room_calls
    ADD COLUMN IF NOT EXISTS channel_id UUID REFERENCES chat_channels (id);

-- Backfill default channels for every non-deleted room.
INSERT INTO chat_channels (id, room_id, name, channel_type, position, created_at, updated_at)
SELECT gen_random_uuid(), r.id, 'general', 'TEXT', 0, NOW(), NOW()
FROM chat_rooms r
WHERE r.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM chat_channels c
      WHERE c.room_id = r.id AND c.channel_type = 'TEXT' AND c.deleted_at IS NULL
  );

INSERT INTO chat_channels (id, room_id, name, channel_type, position, created_at, updated_at)
SELECT gen_random_uuid(), r.id, 'Lounge', 'VOICE', 1, NOW(), NOW()
FROM chat_rooms r
WHERE r.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM chat_channels c
      WHERE c.room_id = r.id AND c.channel_type = 'VOICE' AND c.deleted_at IS NULL
  );

UPDATE chat_messages m
SET channel_id = c.id
FROM chat_channels c
WHERE m.channel_id IS NULL
  AND c.room_id = m.room_id
  AND c.channel_type = 'TEXT'
  AND c.deleted_at IS NULL
  AND m.deleted_at IS NULL;

UPDATE chat_room_calls call
SET channel_id = c.id
FROM chat_channels c
WHERE call.channel_id IS NULL
  AND c.room_id = call.room_id
  AND c.channel_type = 'VOICE'
  AND c.deleted_at IS NULL
  AND call.deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_channel
    ON chat_messages (channel_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_room_calls_channel_status
    ON chat_room_calls (channel_id, status)
    WHERE deleted_at IS NULL;
