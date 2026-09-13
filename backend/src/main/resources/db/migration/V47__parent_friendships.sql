CREATE TABLE IF NOT EXISTS parent_friendships (
    id UUID PRIMARY KEY,
    requester_user_id UUID NOT NULL REFERENCES users (id),
    addressee_user_id UUID NOT NULL REFERENCES users (id),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    invitee_identifier VARCHAR(320) NOT NULL,
    dm_room_id UUID REFERENCES chat_rooms (id),
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_parent_friendships_not_self
        CHECK (requester_user_id <> addressee_user_id)
);

CREATE INDEX IF NOT EXISTS idx_parent_friendships_addressee_status
    ON parent_friendships (addressee_user_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_parent_friendships_requester_status
    ON parent_friendships (requester_user_id, status)
    WHERE deleted_at IS NULL;

-- One active PENDING or ACCEPTED edge per unordered pair
CREATE UNIQUE INDEX IF NOT EXISTS uq_parent_friendships_pair_active
    ON parent_friendships (
        LEAST(requester_user_id, addressee_user_id),
        GREATEST(requester_user_id, addressee_user_id)
    )
    WHERE deleted_at IS NULL AND status IN ('PENDING', 'ACCEPTED');
