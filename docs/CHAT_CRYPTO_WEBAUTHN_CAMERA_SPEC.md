# Spec: Hard chat crypto + WebAuthn camera gate

Status: **IMPLEMENTED (portal)** — locked view hides AES + legacy padding; camera gate requires fingerprint then decrypt code  
Date: 2026-09-12

## Assumptions (correct me if wrong)

1. **Web portal only** for fingerprint + camera gate (not parent/child native apps in this slice).
2. **Decrypt code** is an exact 6-digit numeric code entered and confirmed by the parent in the portal. It is not stored in `.env`.
3. **Messages** are encrypted client-side with AES-256-GCM; key = PBKDF2(passphrase, public salt). Server stores ciphertext only. The portal refuses to send a message that contains the live decrypt code.
4. **Fingerprint** = platform WebAuthn (Touch ID / Windows Hello / USB key). Multiple credentials allowed per parent account; registered once when portal opens after login.
5. **Camera start** on web requires WebAuthn success **then** decrypt-code verify when a fingerprint is registered. Decrypt code alone only if no fingerprint is registered on that browser.
6. Legacy obfuscated messages (`abc…xyz` padding) remain readable after unlock via the old decoder.

## Objective

- Chat bodies unreadable without the decrypt code (including to an AI that only sees DB/API ciphertext).
- Opening **Camera** live view on the web requires registered fingerprint **and** decrypt password (so another enrolled finger on the phone is not enough alone).

## Non-goals

- Recovering a forgotten decrypt code without wipe-reset (existing Snapchat-style reset stays).
- Encrypting media attachments in this slice (text bodies only).
- Native Android biometric for this gate.

## API additions

| Method | Path | Notes |
|--------|------|--------|
| GET | `/chat/decrypt-status` | + `cryptoSalt` (base64) when configured |
| POST | `/chat/decrypt-verify` | `{ pin }` — verify only (camera step 2) |
| unlock/create | existing | return `cryptoSalt`; create/reset/unlock accept exactly 6 digits |

## Client modules

```
web/src/lib/chatMessageCrypto.ts      — AES-GCM + PBKDF2
web/src/lib/chatDecryptSession.ts     — in-memory CryptoKey after unlock
web/src/lib/webAuthnParentGate.ts     — register/list/assert credentials (IndexedDB)
web/src/components/ParentWebAuthnSetup.tsx
web/src/components/CameraUnlockDialog.tsx
```

## Commands

```
cd web && npx vitest run src/lib/chatMessageCrypto.test.ts src/lib/webAuthnParentGate.test.ts
cd backend && mvn -Dtest=ChatDecryptUnlockServiceTest test
```
