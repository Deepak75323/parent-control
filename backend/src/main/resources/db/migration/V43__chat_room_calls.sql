-- Multi-parent chat room video calls (mesh WebRTC signaling sessions).

CREATE TABLE IF NOT EXISTS chat_room_calls (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms (id),
    started_by_user_id UUID NOT NULL REFERENCES users (id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chat_room_calls_room_status
    ON chat_room_calls (room_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS chat_room_call_participants (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL REFERENCES chat_room_calls (id),
    user_id UUID NOT NULL REFERENCES users (id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_chat_call_participant UNIQUE (call_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_call_participants_call
    ON chat_room_call_participants (call_id)
    WHERE deleted_at IS NULL AND left_at IS NULL;
