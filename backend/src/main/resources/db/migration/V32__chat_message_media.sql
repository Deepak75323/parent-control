-- Chat message media attachments (images and videos).

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS message_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS attachment_storage_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS attachment_mime_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS attachment_size_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS attachment_file_name VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_chat_messages_room_type_created
    ON chat_messages (room_id, message_type, created_at DESC);
