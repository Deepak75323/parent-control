# Spec: Parent presence (Online / Last seen)

Status: **APPROVED**  
Date: 2026-09-14

## Objective

In **2-person (DM / couple) chats**, show the partner’s real presence next to their name:

- 🟢 **Online** when they have an active STOMP session and share presence
- 🕐 **Last seen …** when offline and they share presence

Parents can **hide** this from Settings (“Share presence”).

## Defaults

1. `sharePresenceEnabled` defaults to **true** (same spirit as read receipts).
2. Presence is **never** shown in servers with more than 2 members.
3. No fake dots — only WebSocket connect/disconnect (+ cold GET).
4. Multi-tab: online while **any** session is open; offline + `lastSeenAt` when the last session closes.
5. When share presence is **off**, partners see no Online / Last seen (hidden).

## API

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/v1/parent/share-presence/status` | `{ enabled }` |
| POST | `/api/v1/parent/share-presence` | body `{ enabled }` — republish to DM partners |
| GET | `/api/v1/chat/rooms/{roomId}/presence` | Partner snapshot for DM rooms only |

## STOMP

`PRESENCE_UPDATE` on `/user/queue/chat-messages`:

```json
{
  "eventType": "PRESENCE_UPDATE",
  "roomId": "<uuid>",
  "userId": "<uuid>",
  "online": true,
  "lastSeenAt": "2026-09-14T10:00:00Z",
  "hidden": false
}
```

`hidden: true` clears Online / Last seen on the client.

## UI

- Web chat header subtitle (DM only)
- Settings: Share presence toggle (web + parent app)

## Non-goals (v1)

- Presence in multi-member servers
- Friend graph requirement
- Mobile native chat presence UI (settings toggle only)

## Commands

```
cd backend && mvn -Dtest=ParentPresenceTrackerTest,SharePresenceSettingsServiceTest,ParentPresenceFanoutServiceTest test
cd web && npx vitest run src/lib/parentPresence.test.ts && npx tsc --noEmit
```
