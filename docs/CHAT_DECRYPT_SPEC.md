# Spec: Decrypt chat (room owner)

Status: **APPROVED**  
Date: 2026-09-04  
Updated: 2026-09-04 (portal create + Snapchat-style forgot reset)

## Objective

Chat message bodies are stored obfuscated (**3 random letters before and after each word**). Room owners can unlock a **Decrypt chat** view with a 6-digit parent code so they can read plaintext while that room is open. View-once photos/videos open only after that decrypt unlock, and only once.

## Product decisions

1. **Who unlocks:** chat room owner, or a member granted decrypt by an owner/admin (PIN still required).
2. **PIN:** exactly 6 digits, parent-chosen in the portal (no env secret).
3. **Create:** auto-opens only when no decrypt code exists yet. After it is set, unlock is manual via ⋮ → Decrypt chat — not on every room open or send attempt.
4. **Scope:** view-only decode while that room is open; cleared on leave / switch room / Lock chat.
5. **Storage:** messages stay obfuscated/encrypted in the DB; PIN is stored as bcrypt hash in `chat_decrypt_settings`.
6. **Forgot PIN:** try the current PIN first. If forgotten, owner may **Wipe & reset PIN** — deletes **all messages in that room for everyone**, then sets a new PIN (Snapchat passkey-style). The old PIN cannot be revealed or emailed.

---

## Flow A — Create PIN (portal, preferred)

```
Room owner → Decrypt chat
        → GET /api/v1/chat/decrypt-status → configured=false
        → Create decrypt PIN (enter + confirm)
        → POST /api/v1/chat/decrypt-pin
        → unlock for this room
```

Optional headless: `./scripts/deploy/ensure-chat-decrypt-pin.sh --generate` writes env PIN once.

---

## Flow B — Unlock

```
Room owner → Decrypt chat → enter PIN
        → POST /api/v1/chat/rooms/{id}/decrypt-unlock
        → client decodes locally until leave / Lock chat
```

---

## Flow C — Forgot PIN (in-app wipe + reset)

```
Owner → Forgot PIN? → confirm wipe acknowledgment
        → enter + confirm new PIN
        → POST /api/v1/chat/rooms/{id}/decrypt-reset { newPin }
        → clears that room’s history for everyone
        → stores new PIN hash
        → decrypt unlocked with the new PIN
```

Other rooms keep their messages; the new PIN is still server-wide for unlocks.

---

## API

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/v1/chat/decrypt-status` | `{ configured }` |
| POST | `/api/v1/chat/decrypt-pin` | `{ pin }` — only when unset |
| POST | `/api/v1/chat/rooms/{roomId}/decrypt-unlock` | `{ pin }` — owner |
| POST | `/api/v1/chat/rooms/{roomId}/decrypt-reset` | `{ newPin }` — owner; wipes room |

PIN never returned from any API.

## Config

```
app.chat.decrypt.pin=${CHAT_DECRYPT_PIN:}
```

Env is fallback until migrated to DB hash.

## Non-goals

- Parent/child mobile apps in v1  
- Email/SMS recovery of the decrypt PIN  
- API that returns the current PIN  
- Wiping all rooms on reset (only the room where reset was started)

## Commands

```
cd backend && mvn -Dtest=ChatDecryptUnlockServiceTest test
cd web && npx vitest run src/lib/chatMessageObfuscation.test.ts
```
