# Spec: Parent Friends & Servers (Discord-style)

Status: **DRAFT — awaiting product decisions**  
Date: 2026-08-05

## Objective

Let parents connect with each other (friend requests) and collaborate in shared “servers” (workspaces), using the same multi-pane chrome already on the web portal — without turning the product into an untrusted social network over children’s data.

**Success looks like:** Parent A finds Parent B → friend request accepted → A creates/joins a server → invites B → both see the agreed shared content for that server.

## Assumptions (correct me)

1. This is **parent ↔ parent** social graph only (not child accounts as friends).
2. A “server” is a **shared family workspace**, not a free-form chat community like public Discord.
3. **Web portal first**; parent RN app follows in a later slice.
4. Friend discovery uses existing **family handle** and/or email (not a global public directory scrape).
5. Sharing child **devices / live streams / location** with another parent is **opt-in per device** (or per server), never automatic for all friends.
6. Backend remains Spring + Postgres; realtime via existing STOMP where useful.

## Open questions (need your answers)

### Q1 — What does a “server” contain?
Pick one (or mix):

| Option | Meaning |
|--------|---------|
| **A. Shared monitoring** | Members can see selected child devices, status, alerts (and maybe live view) |
| **B. Co-parenting workspace** | Shared policies/alerts for devices you explicitly share |
| **C. Social only** | Friends + servers + text chat / presence — **no** device data shared |
| **D. Full Discord clone** | Channels, DMs, roles, voice — large scope |

**Recommendation:** Start with **friends + servers + roles**, then **device sharing ACL** (A/B). Chat as phase 2.

### Q2 — What can a friend see by default after accept?
- Nothing until invited to a server?  
- Or presence only (online) until device shares?

### Q3 — Who owns a server?
- Creator is owner; can invite friends, kick, transfer?  
- One personal server auto-created per parent (“My Family”) like Discord home?

### Q4 — Device sharing model
When Parent B is in Parent A’s server:
- See **all** of A’s devices?  
- Or only devices A **explicitly shares** into that server?  
- Can B start live stream / change policies, or **view-only**?

### Q5 — Discovery
Invite by: `@familyHandle` / email / invite link / QR?

### Q6 — Scope for v1
- Web only?  
- Parent app too?  
- Chat messages in v1 or later?

## Proposed v1 (if you approve the defaults)

### Entities
- `parent_friendships` — pending / accepted / blocked between two `parent_profiles`
- `parent_friend_requests` — from → to, message, status
- `servers` — id, name, owner_parent_id, icon, created_at
- `server_members` — server_id, parent_id, role (`OWNER` | `ADMIN` | `MEMBER`)
- `server_invites` — server_id, invited_parent_id or token, status
- `server_device_shares` — server_id, device_id, permission (`VIEW` | `MONITOR` | `CONTROL`)

### UX (maps to current Discord chrome)
- **Rail:** personal “My Family” + joined servers (like Discord server icons)
- **Sidebar:** channels = Devices / Alerts / Members / Settings **for the active server**
- **Members panel:** server members + online status
- **Friends:** friend list + pending requests (Me / Friends screen)

### Security (non-negotiable)
- No cross-tenant device access without an explicit `server_device_shares` row
- Audit log every share grant/revoke and friend accept
- Rate-limit friend requests and invites
- Owner can revoke shares instantly; revoked device disappears for non-owners
- PIN / stream rules still apply per acting parent session

### Non-goals (v1)
- Public server discovery  
- Voice / video between parents  
- Child-facing social features  
- Nitro-style monetization of servers  

## Implementation slices (after approval)

1. Schema + friend request API + tests  
2. Friends UI (web)  
3. Servers + members + rail switching  
4. Device share ACL + device list filtered by active server  
5. Realtime presence for friends/members  
6. Parent RN parity  

## Commands (when building)

```
cd backend && mvn test
cd web && npm run build && npm run lint
./scripts/dev/start-backend-local.sh
cd web && npm run dev
```
