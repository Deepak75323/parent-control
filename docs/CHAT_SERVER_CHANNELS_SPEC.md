# Spec: Chat servers & channels (Discord-inspired)

Status: **APPROVED (defaults)**  
Date: 2026-09-07

## Objective

Evolve parent chat so each existing **room** behaves like a Discord **server**, containing:

- Multiple **text channels** (`#general`, `#school`, …)
- Multiple **voice channels** (`Lounge`, `Study hall`, …) that host video/voice calls

Preserve membership, invites, moderation, decrypt, receipts, and mesh WebRTC.

## Product mapping

| Discord concept | Our model |
|-----------------|-----------|
| Server | Existing `chat_rooms` (UI label: server) |
| Text channel | `chat_channels` type `TEXT` |
| Voice channel | `chat_channels` type `VOICE` |
| Join voice | Start/join call scoped to a voice `channel_id` |

## Defaults (v1)

1. Creating a server/room auto-creates `# general` (TEXT) + `Lounge` (VOICE).
2. Existing rooms get the same two channels via migration; all prior messages attach to `# general`.
3. Messages require `channelId` (TEXT only). Legacy clients omit it → server uses default TEXT channel.
4. Calls may include `channelId` (VOICE). Omit → default VOICE channel for the room.
5. One ACTIVE call per **voice channel** (not per entire server).
6. Cap 20 channels per server; names unique per server (case-insensitive).
7. Web portal only for channel UI in this slice.

## Non-goals (v1)

- Separate friend graph / DM servers
- Screen share / SFU
- Channel-level permissions beyond room membership
- Renaming “room” table (DB keeps `chat_rooms`)

## Commands

```
cd backend && mvn -Dtest=ChatChannelServiceTest,ChatIntegrationTest test
cd web && npx vitest run src/lib/chatChannelNav.test.ts && npm run build
```
