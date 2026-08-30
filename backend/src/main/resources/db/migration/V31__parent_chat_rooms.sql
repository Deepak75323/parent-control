-- Parent-to-parent chat rooms (Discord-like servers), invites, and messages.

CREATE TABLE IF NOT EXISTS chat_rooms (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users (id),
    name VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chat_rooms_owner ON chat_rooms (owner_user_id);

CREATE TABLE IF NOT EXISTS chat_room_members (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms (id),
    user_id UUID NOT NULL REFERENCES users (id),
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_chat_room_members_room_user UNIQUE (room_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_room_members_user ON chat_room_members (user_id);

CREATE TABLE IF NOT EXISTS chat_room_invites (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms (id),
    inviter_user_id UUID NOT NULL REFERENCES users (id),
    invitee_user_id UUID NOT NULL REFERENCES users (id),
    invitee_identifier VARCHAR(320) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chat_room_invites_invitee_status
    ON chat_room_invites (invitee_user_id, status);

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms (id),
    sender_user_id UUID NOT NULL REFERENCES users (id),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_room_created
    ON chat_messages (room_id, created_at DESC);
