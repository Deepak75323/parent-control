# Spec: Parent friends graph (v1)

Status: **APPROVED**  
Date: 2026-09-14

## Objective

Discord-style **friend request → accept → friends list** for parents. On accept, open a **2-person DM room** so Direct messages works.

## Defaults

1. Discover by `@familyHandle` or email (same as chat invites).
2. No device sharing in this slice.
3. Web portal first; parent RN later.
4. Accept creates/finds a 2-person chat room and links it on the friendship.
5. Reject / remove soft-ends the friendship; DM room stays unless owner deletes it separately.

## API

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/v1/parent/friends/requests` | `{ identifier }` |
| GET | `/api/v1/parent/friends/requests/pending` | Incoming PENDING |
| GET | `/api/v1/parent/friends/requests/outgoing` | Outgoing PENDING |
| POST | `/api/v1/parent/friends/requests/{id}/respond` | `{ accept }` |
| GET | `/api/v1/parent/friends` | Accepted friends (+ `dmRoomId`) |
| DELETE | `/api/v1/parent/friends/{friendUserId}` | Unfriend |

## STOMP (optional)

`/user/queue/friend-requests` — bump pending list (mirror chat-invites).

## UI

- Connect Parent modal → **Add friend** (friend request)
- Pending friend banner (like chat invites)
- Friends list under Direct messages; click opens `dmRoomId`

## Non-goals

- Servers redesign, device ACL, public discovery, block list UI

## Commands

```
cd backend && mvn -Dtest=ParentFriendServiceTest test
cd web && npx vitest run src/lib/parentFriends.test.ts && npx tsc --noEmit
```
