# Spec: Parent Community Chat Workspace (UX redesign)

Status: **APPROVED (defaults)**  
Date: 2026-09-07  
Visual reference: design mock (Parent Control branded, Discord-inspired IA — not Discord branding)

## Objective

Evolve `/chat` into a communication workspace:

```
App nav (unchanged) → Chat sidebar (server/channels/DMs) → Main (text OR video) → Optional right panel
```

Preserve rooms, invites, moderation, messages, mesh WebRTC.

## Assumptions

1. Existing `chat_rooms` = communities/servers; `chat_channels` TEXT|VOICE already shipped (V44).
2. Direct = heuristic `memberCount === 2` until a friends/DM API exists — do not fake presence or a friend graph.
3. No channel categories API yet — UI uses fixed TEXT / VOICE category sections (collapsible).
4. No parent presence API — do not show fake online dots. **(Superseded 2026-09-14:** real presence for 2-person chats — see `PARENT_PRESENCE_SPEC.md`.)
5. Video in call uses a **dedicated main workspace** (not docked above messages). Side chat during call uses the right panel.

## In scope (this increment)

- Dark Parent Control chat chrome (brand purple/blue, not Discord palette)
- Sidebar: server header, TEXT/VOICE/DM sections, Connect Parent, independent scroll
- Dedicated video workspace when in a call
- Right panel: call participants during call; members otherwise
- Nested scrolling (sidebar / messages / right panel independent; page does not scroll)

## Out of scope / backend TODOs

| Gap | Needed |
|-----|--------|
| Channel categories | `chat_channel_categories` + API |
| Channel description/topic | column + API |
| Parent presence | presence service + STOMP |
| Real DM graph | friends table / DM room type |
| Incoming call ring off `/chat` | global call notification UI |
| Voice occupancy for all channels | lightweight presence fan-out per voice channel |

## Commands

```
cd web && npx vitest run src/lib/chatChannelNav.test.ts && npx tsc --noEmit && npm run build
```
