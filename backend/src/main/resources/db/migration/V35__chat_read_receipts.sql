ALTER TABLE parent_profiles
    ADD COLUMN IF NOT EXISTS read_receipts_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS chat_message_receipts (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_chat_message_receipt UNIQUE (message_id, recipient_user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_receipts_message
    ON chat_message_receipts (message_id);

CREATE INDEX IF NOT EXISTS idx_chat_message_receipts_recipient
    ON chat_message_receipts (recipient_user_id);
