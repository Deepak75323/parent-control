# Spec: Multi-parent room video call

Status: **APPROVED (defaults)**  
Date: 2026-09-06

## Objective

Parents in the same chat room can start/join a shared **video call** (multi-party), separate from child live monitoring.

## Product decisions (v1)

1. **Where:** Parent **web portal** only (not mobile apps yet).
2. **Topology:** **Mesh WebRTC** (each parent connects to each other). Cap **4** concurrent participants.
3. **Media:** Camera + microphone (browser getUserMedia). Audio-only toggle later.
4. **Who can start:** Any active room member. Owner/admin can **end for everyone**.
5. **One active call per room** at a time.
6. **ICE:** Reuse existing `/api/v1/monitoring/webrtc-config` + coturn TURN.
7. **Signaling:** REST post + STOMP fan-out to room members (`CALL_*` events on `/queue/chat-messages`).
8. **Not in v1:** Recording, screen share, SFU, mobile parent app, child device in the call.

## API

| Method | Path | Notes |
|--------|------|--------|
| POST | `/rooms/{roomId}/calls` | Start call (or return existing ACTIVE) |
| GET | `/rooms/{roomId}/calls/active` | Active call + participants, or null |
| POST | `/rooms/{roomId}/calls/{callId}/join` | Join ACTIVE call |
| POST | `/rooms/{roomId}/calls/{callId}/leave` | Leave (end call if last participant) |
| POST | `/rooms/{roomId}/calls/{callId}/end` | End for everyone (starter, owner, or admin) |
| POST | `/rooms/{roomId}/calls/{callId}/signal` | `{ toUserId, messageType, payload }` peer relay |

## Realtime events

`CALL_STARTED`, `CALL_JOINED`, `CALL_LEFT`, `CALL_ENDED`, `CALL_SIGNAL`  
Fields include `roomId`, `callId`, `userId` / `fromUserId`, `toUserId`, `messageType`, `payload` as needed.

## Portal UX

- Room header menu: **Start video call** / banner **Join call** when ACTIVE.
- Call overlay: local + remote video tiles, mute/camera toggles, Leave, End (mods).

## Commands

```
cd backend && mvn -Dtest=ChatCallServiceTest test
cd web && npx vitest run src/lib/chatCallMesh.test.ts
```
