# Spec: Chat room admin permissions

Status: **APPROVED** (user confirmed 2026-09-06)  
Date: 2026-09-06

## Objective

Room creators (owners) and admins can manage members: remove, mute, grant decrypt, transfer ownership, and clear history — without giving every member those powers.

## Roles

| Role | Powers |
|------|--------|
| `OWNER` | Everything: kick, mute, grant decrypt, clear history, promote/demote admin, transfer ownership, delete room, decrypt, reset decrypt PIN |
| `ADMIN` | Kick members (not owner/admins), mute members (not owner), grant/revoke decrypt (not owner), clear history, decrypt if `canDecrypt` |
| `MEMBER` | Chat (unless muted), leave, clear chat for self only |

Exactly one `OWNER` per room (`chat_rooms.owner_user_id` stays in sync with member role).

## Member flags

| Flag | Default | Meaning |
|------|---------|---------|
| `muted` | false | Cannot send text/media or typing signals |
| `can_decrypt` | false | May unlock decrypt with the server PIN (owner always may) |

## API

| Method | Path | Who |
|--------|------|-----|
| DELETE | `/rooms/{roomId}/members/{userId}` | Owner / Admin (rules above) |
| PATCH | `/rooms/{roomId}/members/{userId}` | Owner / Admin — body: `{ role?, muted?, canDecrypt? }` (`role` = `ADMIN`\|`MEMBER` only; owner-only for role changes) |
| POST | `/rooms/{roomId}/transfer-ownership` | Owner only — body: `{ userId }` |
| POST | `/rooms/{roomId}/clear-history` | Owner **or** Admin (was owner-only) |
| POST | `/rooms/{roomId}/decrypt-unlock` | Owner **or** member with `canDecrypt` |
| POST | `/rooms/{roomId}/decrypt-reset` | Owner only (unchanged) |

## Room summary additions

`myRole`, `muted`, `canDecrypt` for the current viewer (owner always `canDecrypt: true` in API).

## Non-goals (v1)

- Multiple owners
- Timed mutes
- Per-message moderation
- Mobile parent/child apps

## Commands

```
cd backend && mvn -Dtest=ChatRoomModerationServiceTest,ChatDecryptUnlockServiceTest test
cd web && npx vitest run src/lib/chatMemberPermissions.test.ts
```
