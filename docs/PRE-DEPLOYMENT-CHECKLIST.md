# Pre-Deployment Security Checklist

Use this checklist before every production release. Each item should **pass** before go-live.

Set `spring.profiles.active=prod` in production. The API refuses to start if critical security requirements are not met.

---

## 1. Environment variables

Critical variables must be set in production. Missing values cause startup failure via `ProductionSecurityValidator` and `JwtTokenProvider`.

| Variable | Required in prod | Purpose |
|----------|------------------|---------|
| `JWT_SECRET` | Yes (≥ 32 bytes) | Signs parent/device JWTs |
| `APP_ENCRYPTION_KEY` | Yes | Field encryption at rest |
| `SPRING_DATASOURCE_URL` | Yes (must include `sslmode=require`) | PostgreSQL with TLS |
| `SPRING_DATASOURCE_USERNAME` | Yes | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Yes (no weak defaults) | Database password |
| `CORS_ALLOWED_ORIGINS` | Yes (no `*`) | Browser API origins |
| `WEBSOCKET_ALLOWED_ORIGINS` | Yes (no `*`) | SockJS/WebSocket origins |
| `TURN_SECRET` | Yes | Ephemeral WebRTC TURN credentials |
| `TURN_EXTERNAL_HOST` | Yes (public hostname) | TURN server for NAT traversal |
| `SPRING_DATA_REDIS_HOST` | Yes | Rate limiting (fail-closed in prod) |
| `PASSWORD_RESET_URL_BASE` | Yes (if reset enabled) | Parent app reset screen URL |
| `SMTP_HOST` | Yes (if reset enabled) | Outbound password-reset email |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | Yes (if SMTP auth required) | Mail credentials |
| `FCM_SERVICE_ACCOUNT_FILE` or `FCM_SERVER_KEY` | Yes when `FCM_ENABLED=true` | Push wake for child devices |
| `PLAY_INTEGRITY_SERVICE_ACCOUNT_FILE` | Yes when play-integrity required | Device attestation |
| `STRIPE_SECRET_KEY` | Yes | Stripe API |
| `STRIPE_WEBHOOK_SECRET` | Yes | Webhook signature verification (fail-closed) |
| `BILLING_ENFORCEMENT` | `true` | Subscription gating in prod (startup fails if false) |

**Dev-only (must be unset/disabled in prod):**

| Variable | Prod value |
|----------|------------|
| `TAMPER_MASTER_PIN` | Unset |
| `TAMPER_MASTER_PIN_ENABLED` | `false` |
| `ADMIN_ENDPOINTS_ENABLED` | `false` |
| `OBSERVABILITY_PROMETHEUS_PUBLIC` | `false` |

**Verify:**

```bash
# Prod boot should fail without secrets
SPRING_PROFILES_ACTIVE=prod java -jar backend.jar

# Dev should warn but start (with backend/.env populated)
SPRING_PROFILES_ACTIVE=dev ./scripts/dev/dev-up.sh
```

---

## 2. Debug code removal

| Check | Expected |
|-------|----------|
| Mobile `console.log` / `console.warn` | Gated behind `__DEV__` |
| Backend `TODO`/`FIXME` on security | None |
| Hardcoded credentials in source | None (use env vars) |
| Test API routes (`/test`, `/debug`, `/seed`) | None |
| Admin chaos/cert routes | Disabled (`app.admin.endpoints-enabled=false`) |
| Swagger/OpenAPI | Disabled in prod (`springdoc.api-docs.enabled=false`) |
| Tamper master PIN | Disabled in prod |

**Verify:**

```bash
rg 'console\.log' child/src parent/src --glob '!**/__tests__/**'
rg 'TODO|FIXME' backend/src/main
rg 'pcp_secret|Deepak@123' --glob '!docs/**'
```

---

## 3. Error handling

| Check | Expected |
|-------|----------|
| API error responses | Generic message + `correlationId` |
| Stack traces to clients | Never |
| SQL / file paths in responses | Never |
| Validation errors | `"Invalid request data"` (no field dump) |
| Spring Boot error pages | `include-stacktrace: never` in prod |

**Verify:**

```bash
# Trigger a 500 (e.g. invalid JSON) and confirm no stack trace in body
curl -s -X POST https://api.example.com/api/v1/auth/login \
  -H 'Content-Type: application/json' -d 'not-json' | jq .
```

---

## 4. Security headers

Applied by `SecurityHeadersFilter` on every response:

| Header | Value |
|--------|-------|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` (prod) |
| `Content-Security-Policy` | Restrictive default; override via `CSP_HEADER` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |

**Verify:**

```bash
curl -sI https://api.example.com/actuator/health | rg -i 'x-content-type|x-frame|strict-transport|content-security'
```

---

## 5. Rate limiting

| Endpoint | Limit (prod) | Redis required |
|----------|--------------|----------------|
| `POST /api/v1/auth/login` | 5 / minute / IP | Yes (fail-closed) |
| `POST /api/v1/auth/register` | 5 / minute / IP | Yes |
| `POST /api/v1/auth/refresh` | 5 / minute / IP | Yes |
| `POST /api/v1/auth/logout` | 5 / minute / IP | Yes |
| `POST /api/v1/auth/forgot-password` | 3 / hour / IP | Yes |
| `POST /api/v1/auth/reset-password` | 5 / minute / IP | Yes |
| `POST /api/v1/device/tamper/verify` | 10 / minute / device | Yes |
| `DELETE /api/v1/auth/account` | 5 / minute / IP | Yes |
| `POST /api/v1/device/pair` | 5 / minute / IP | Yes |

**Verify:**

```bash
# Sixth login within a minute should return 429
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST .../auth/login -d '{"email":"x","password":"y"}'
done
```

---

## 6. CORS configuration

| Check | Expected |
|-------|----------|
| `CORS_ALLOWED_ORIGINS` | Explicit parent domain(s) only |
| Wildcard `*` | Rejected in prod validator |
| WebSocket origins | Explicit list, no `*` |
| Credentials | Allowed only for listed origins |

**Prod example:**

```bash
CORS_ALLOWED_ORIGINS=https://parent.yourdomain.com
WEBSOCKET_ALLOWED_ORIGINS=https://parent.yourdomain.com
```

---

## 7. Database security

| Check | Expected |
|-------|----------|
| TLS to PostgreSQL | `sslmode=require` (or stricter) in JDBC URL |
| Default passwords | Rejected (`pcp_secret`, `password`, `postgres`) |
| Public port 5432 | **Dev docker-compose only** — prod DB must not be internet-exposed |
| Connection pooling | Sized per replica (`DB_POOL_MAX`) |

**Prod JDBC example:**

```
jdbc:postgresql://db.internal:5432/parental_control?sslmode=require
```

---

## 8. Password reset (new)

| Endpoint | Method | Auth | Rate limit |
|----------|--------|------|------------|
| `/api/v1/auth/forgot-password` | POST | Public | 3 / hour / IP |
| `/api/v1/auth/reset-password` | POST | Public | 5 / min / IP |
| `/api/v1/auth/logout` | POST | Public (refresh token body) | 5 / min / IP |

**Behaviour:**

- Forgot-password always returns the same message (no email enumeration).
- Reset tokens are single-use (atomic claim), SHA-256 hashed at rest, expire in **15 minutes** (prod max).
- Successful reset revokes all refresh tokens and invalidates outstanding access tokens.
- Refresh-token reuse detection invalidates all sessions for the account.
- Prod requires `PASSWORD_RESET_URL_BASE` and `SMTP_HOST`.

**Prod mail example:**

```bash
PASSWORD_RESET_URL_BASE=https://parent.yourdomain.com/reset-password
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=noreply@yourdomain.com
```

**Parent app flow:** Login → Forgot password → email link → Reset password screen.

---

## Quick release gate

Before tagging a release, confirm:

- [ ] All prod env vars set in secrets manager / K8s Secrets
- [ ] `spring.profiles.active=prod` on API pods
- [ ] Redis reachable from API
- [ ] DB TLS verified
- [ ] CORS/WebSocket origins match deployed parent app URL
- [ ] SMTP test email received for password reset
- [ ] Rate limits return 429 when exceeded
- [ ] Security headers present on health check
- [ ] No secrets in `git diff` staged for commit
- [ ] Integration tests pass: `cd backend && mvn test`

---

## Related docs

- Secret rotation: see README **Secret management and rotation**
- Personal data map: prior audit in agent transcript / `AccountDeletionService`
- Local dev: `docs/DEVELOPER_ONBOARDING.md`
