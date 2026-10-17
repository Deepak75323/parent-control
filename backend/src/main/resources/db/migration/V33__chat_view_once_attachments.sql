-- View-once chat attachments: deleted from storage after the first recipient view.

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS attachment_view_once BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS attachment_consumed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS attachment_consumed_by_user_id UUID;

CREATE INDEX IF NOT EXISTS idx_chat_messages_room_attachment_consumed
    ON chat_messages (room_id, attachment_consumed_at)
    WHERE attachment_storage_key IS NOT NULL;
