-- Room moderation: ADMIN role support columns, mute, per-member decrypt grant.

ALTER TABLE chat_room_members
    ADD COLUMN IF NOT EXISTS muted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chat_room_members
    ADD COLUMN IF NOT EXISTS can_decrypt BOOLEAN NOT NULL DEFAULT FALSE;

-- Owners always effectively have decrypt; keep DB flag false and resolve in app.
COMMENT ON COLUMN chat_room_members.muted IS 'When true, member cannot send messages or typing';
COMMENT ON COLUMN chat_room_members.can_decrypt IS 'When true, member may unlock decrypt with server PIN (owners always can)';
