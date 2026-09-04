# Spec: Decrypt chat (room owner)

Status: **APPROVED**  
Date: 2026-09-04  
Updated: 2026-09-04 (creation + forgot flows)

## Objective

Chat message bodies are stored obfuscated (3 random letters before and after each word). Room owners can unlock a **Decrypt chat** view with a 4-digit server PIN so they can read plaintext while that room is open.

## Product decisions

1. **Who unlocks:** chat room owner only.
2. **PIN:** exactly 4 digits (`CHAT_DECRYPT_PIN`), created once when the **server** (this backend deployment) is first set up; printed once to the deployer; reusable for unlocks.
3. **Scope:** view-only decode while that room is open; cleared on leave / switch room / Lock chat.
4. **Storage:** messages stay obfuscated in the DB.
5. **Forgot PIN:** cannot be recovered from the chat UI (no email OTP). Only someone with server access regenerates it; the new PIN is printed once.

---

## Flow A — Create PIN at server creation

This runs on the Oracle/self-hosted VM when the backend is first brought up (or any time `.env` has no valid PIN yet).

```
Deployer creates / first-deploys server
        │
        ▼
scripts/deploy/ensure-chat-decrypt-pin.sh
        │
        ├─ CHAT_DECRYPT_PIN already 4 digits in .env?
        │     └─ yes → do nothing (do NOT reprint)
        │
        └─ missing / invalid
              ├─ generate random 1000–9999
              ├─ write CHAT_DECRYPT_PIN=…. into ~/parentalControlApp/.env
              └─ print ONE-TIME reveal banner to the deploy terminal
                    (deployer must save it — never shown in the portal)
        │
        ▼
Backend container starts with -e CHAT_DECRYPT_PIN=….
Room owners can Decrypt chat using that PIN.
```

**Commands (on the VM or via deploy):**

```bash
# Idempotent — generates only if missing
./scripts/deploy/ensure-chat-decrypt-pin.sh

# Called automatically before blue/green swap
./scripts/deploy/oracle-deploy-backend.sh
```

**Who sees the PIN at creation:** only the person running deploy (SSH/terminal). It is not emailed and not returned by any parent API.

---

## Flow B — Unlock in the portal (normal use)

```
Room owner → room menu → Decrypt chat
        → enter 4-digit PIN
        → POST /api/v1/chat/rooms/{id}/decrypt-unlock
        → on success: client decodes bodies locally for this open room
        → leave room / Lock chat → decrypt cleared
```

---

## Flow C — Forgot PIN (recovery)

There is **no** in-app reveal of the old PIN (it is not stored for parents to fetch).

```
Owner taps “Forgot PIN?” in the dialog
        → sees instructions: contact the person who hosts this server
        │
Server operator (SSH) runs:
        ./scripts/deploy/oracle-reset-chat-decrypt-pin.sh
        │
        ├─ writes a NEW random 4-digit PIN into .env
        ├─ prints the new PIN once
        └─ restarts/redeploys backend so the container picks it up
        │
        ▼
Operator gives the new PIN to room owners out-of-band
```

Old PIN stops working immediately after reset.

---

## API

`POST /api/v1/chat/rooms/{roomId}/decrypt-unlock`  
Body: `{ "pin": "4829" }`  
Auth: parent JWT. Owner + PIN match required. PIN never returned.

## Config

```
app.chat.decrypt.pin=${CHAT_DECRYPT_PIN:}
```

## Non-goals

- Parent/child mobile apps in v1  
- Email/SMS recovery of the decrypt PIN  
- API that returns the current PIN to any parent  
- OTP that rotates on every unlock  

## Commands

```
cd web && npx vitest run src/lib/chatMessageObfuscation.test.ts
cd backend && mvn -Dtest=ChatDecryptUnlockServiceTest test
./scripts/deploy/ensure-chat-decrypt-pin.sh --dry-run
```
