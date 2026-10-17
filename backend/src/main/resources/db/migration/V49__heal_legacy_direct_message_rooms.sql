-- Promote leftover pre-flag DM rooms into real direct messages.
-- "Chat with …" rooms were created as friend DMs before direct_message existed.
UPDATE chat_rooms
SET direct_message = TRUE
WHERE deleted_at IS NULL
  AND direct_message = FALSE
  AND name ILIKE 'Chat with %';
