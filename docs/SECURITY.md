# Security Considerations

## Authentication

- **Access tokens**: JWT, 15-minute TTL, `ROLE_PARENT` / `ROLE_CHILD_DEVICE` / `ROLE_ADMIN`
- **Refresh tokens**: Stored as SHA-256 hash in PostgreSQL; revocable
- **Device tokens**: Long-lived, scoped to `deviceId` claim

## Transport

- TLS 1.2+ required in production
- Certificate pinning recommended in mobile apps

## Data protection

- Passwords: BCrypt (strength 12)
- Parent PIN: hashed server-side (Phase 3)
- Sensitive logs: avoid PII in application logs
- Encrypt data at rest (PostgreSQL TDE / volume encryption)

## Child device integrity

- Device token required for `/api/v1/device/*` (except `/pair`)
- Request signing (HMAC) on child `POST /api/v1/device/*` (except `/pair`); set `HMAC_REQUIRED=true` in production
- Device integrity / SafetyNet checks planned

## Anti-tampering (legal scope)

- [x] Device Admin (`ChildDeviceAdminReceiver`) — uninstall blocked until admin deactivated
- [x] Hide launcher icon when admin active (`TamperProtectionManager`)
- [x] Foreground service for persistence (silent `IMPORTANCE_MIN` channel); tap opens app when icon hidden
- [x] Parent monitoring actions do not push alerts to child device
- [x] Device Owner provisioning (ADB / QR)
- [x] Kiosk / Lock Task via parent command (`ENABLE_KIOSK` / `DISABLE_KIOSK`)
- **No** unauthorized reinstall after user uninstalls

## Compliance

- Consent flows on parent registration and child pairing
- Audit log table for sensitive parent actions
- Data retention policies per jurisdiction (configure in Phase 3)

## Rate limiting

- Redis-based limits: `app.rate-limit.auth-requests-per-minute` (default 30), `device-ingest-requests-per-minute` (default 120)
