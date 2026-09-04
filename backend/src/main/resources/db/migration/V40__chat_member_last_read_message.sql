-- WhatsApp-style read cursor: unread = incoming messages after this message.
ALTER TABLE chat_room_members
    ADD COLUMN IF NOT EXISTS last_read_message_id UUID;

-- Backfill from latest receipt that was marked read for each member.
UPDATE chat_room_members m
SET last_read_message_id = sub.message_id
FROM (
    SELECT DISTINCT ON (r.recipient_user_id, msg.room_id)
        r.recipient_user_id AS user_id,
        msg.room_id AS room_id,
        r.message_id AS message_id
    FROM chat_message_receipts r
    INNER JOIN chat_messages msg ON msg.id = r.message_id
    WHERE r.read_at IS NOT NULL
      AND msg.deleted_at IS NULL
    ORDER BY r.recipient_user_id, msg.room_id, msg.created_at DESC
) sub
WHERE m.user_id = sub.user_id
  AND m.room_id = sub.room_id
  AND m.last_read_message_id IS NULL;

-- Mark-as-unread override: cursor sits just before unread_from_at.
UPDATE chat_room_members m
SET last_read_message_id = (
    SELECT msg.id
    FROM chat_messages msg
    WHERE msg.room_id = m.room_id
      AND msg.deleted_at IS NULL
      AND msg.created_at < m.unread_from_at
    ORDER BY msg.created_at DESC
    LIMIT 1
)
WHERE m.unread_from_at IS NOT NULL;
