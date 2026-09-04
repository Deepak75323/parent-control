# Spec: Chat typing indicator (WhatsApp-style)

Status: **APPROVED (defaults)**  
Date: 2026-09-04

## Objective

While a parent is composing in an open room, other members of that room see a live “X is typing…” line (web portal v1).

## Behavior

- Typing starts when the composer has non-empty text (throttled pulses).
- Clears ~2.5s after they stop changing the draft, when they send, clear the draft, or leave the room.
- Show above the composer: nickname + “is typing…” (or “A and B are typing…” / “Several people are typing…”).
- Ephemeral only — not stored in the database.

## API

`POST /api/v1/chat/rooms/{roomId}/typing`  
Body: `{ "typing": true | false }`  
Auth: parent JWT, must be an active room member.  
Fan-out: WebSocket `/user/queue/chat-messages` event `TYPING` to other members (not the sender).

## Surfaces

v1: parent web portal only.

## Non-goals

- Parent RN / child apps in v1  
- Typing on room list rows in v1  
- Exact keystroke streaming  
