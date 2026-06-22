# parental-control-backend

Spring Boot API for the parental control platform: authentication, device pairing, monitoring ingest, policy engine, WebRTC signaling, push orchestration, and audit logs.

**Contract version:** `API_CONTRACT_VERSION=1.0.0`  
**Base path:** `/api/v1`  
**OpenAPI:** `https://<host>/swagger-ui.html` · `https://<host>/v3/api-docs`

---

## Overview

| Responsibility | Description |
|----------------|-------------|
| Auth | Parent registration/login, JWT access + refresh, device tokens |
| Devices | Pairing codes, heartbeat, capability flags, remote commands |
| Monitoring | Notifications, location, usage stats, alerts |
| Policies | App blocks, screen time, geofences, streaming rules |
| Real-time | STOMP WebSocket for parent dashboards |
| Streaming | WebRTC signaling, ICE config, screen-capture audit |
| Compliance | Consent tracking, parent PIN, audit logs |

**Consumers:** `parental-control-parent-app`, `parental-control-child-app` (no shared code—OpenAPI only).

---

## Tech stack

| Layer | Choice |
|-------|--------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.4+ |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Cache / rate limit | Redis |
| Auth | Spring Security + JJWT |
| API docs | springdoc-openapi |
| Real-time | STOMP over WebSocket |
| Tests | JUnit 5, Testcontainers |

---

## Repository structure

```
parental-control-backend/
├── .github/workflows/
│   ├── ci.yml
│   ├── deploy-staging.yml
│   └── publish-openapi.yml
├── docs/
│   ├── openapi/              # Exported contract (CI artifact)
│   ├── API.md
│   ├── SECURITY.md
│   └── RUNBOOK.md
├── src/main/java/com/parentalcontrol/
│   ├── config/
│   ├── common/
│   ├── auth/
│   ├── user/
│   ├── device/
│   ├── monitoring/
│   ├── policy/
│   └── audit/
├── src/main/resources/
│   ├── application.yml
│   ├── application-prod.yml
│   └── db/migration/
├── src/test/java/
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
├── helm/                       # Optional Kubernetes
├── pom.xml
├── .env.example
└── README.md
```

---

## Prerequisites

- Java 21+
- Maven 3.9+
- Podman (local Postgres, Redis) — use `podman compose` or project `./scripts/dev/compose.sh`
- PostgreSQL 16 (or Podman)

---

## Quick start (local)

```bash
# 1. Infrastructure (Podman)
podman compose -f docker/docker-compose.yml up -d postgres redis

# 2. Configuration
cp .env.example .env
# Edit: DB_URL, JWT_SECRET, REDIS_URL

# 3. Run
mvn clean install
mvn spring-boot:run
```

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| STOMP (RN) | ws://localhost:8080/ws-native |
| STOMP (browser) | http://localhost:8080/ws (SockJS) |

---

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SPRING_DATASOURCE_URL` | Yes | JDBC URL (`jdbc:postgresql://localhost:5432/parental_control`) |
| `SPRING_DATASOURCE_USERNAME` | Yes | DB user |
| `SPRING_DATASOURCE_PASSWORD` | Yes | DB password |
| `JWT_SECRET` | Yes | Min 256-bit secret for signing |
| `JWT_ACCESS_TTL_SECONDS` | No | Default `900` (15 min) |
| `JWT_REFRESH_TTL_DAYS` | No | Default `30` |
| `REDIS_HOST` | No | Rate limiting / nonce store |
| `WEBRTC_STUN_URLS` | No | Comma-separated STUN URLs |
| `WEBRTC_TURN_URL` | Prod | TURN URL for NAT traversal |
| `WEBRTC_TURN_USERNAME` | Prod | TURN credentials |
| `WEBRTC_TURN_PASSWORD` | Prod | TURN credentials |
| `FCM_SERVICE_ACCOUNT_JSON` | Prod | Firebase push (stream requests) |

See `.env.example` for the full list.

---

## API surface (summary)

All responses use:

```json
{ "success": true, "message": "optional", "data": {}, "timestamp": "ISO-8601" }
```

### Authentication

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/auth/register` | Public |
| POST | `/api/v1/auth/login` | Public |
| POST | `/api/v1/auth/refresh` | Public |

### Devices (parent)

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/devices/pairing-code` | Parent JWT |
| GET | `/api/v1/devices` | Parent JWT |
| GET | `/api/v1/devices/{id}` | Parent JWT |
| POST | `/api/v1/devices/{id}/commands` | Parent JWT |

### Device agent (child)

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/device/pair` | Public |
| POST | `/api/v1/device/heartbeat` | Device JWT + HMAC |
| POST | `/api/v1/device/capabilities` | Device JWT + HMAC |
| POST | `/api/v1/device/notifications` | Device JWT + HMAC |
| POST | `/api/v1/device/location` | Device JWT + HMAC |
| POST | `/api/v1/device/usage-stats` | Device JWT + HMAC |

### Monitoring & streaming

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/monitoring/start-stream` | Parent JWT |
| POST/GET | `/api/v1/monitoring/signaling/{roomId}` | Parent / Device JWT |
| GET | `/api/v1/monitoring/webrtc/ice` | Parent / Device JWT |
| POST | `/api/v1/monitoring/revoke-consent` | Parent JWT |
| GET | `/api/v1/audit/screen-capture` | Parent JWT |

Full reference: [docs/API.md](docs/API.md).

---

## WebSocket (STOMP)

**Connect:** `Authorization: Bearer <parent_access_token>` on STOMP `CONNECT`.

| Destination | Purpose |
|-------------|---------|
| `/user/queue/locations` | Live GPS updates |
| `/user/queue/notifications` | New notification logs |
| `/user/queue/device-status` | Heartbeat / online status |
| `/user/queue/alerts` | Low battery, violations |
| `/user/queue/policy-violations` | App block / screen time events |
| `/user/queue/streaming-state` | Stream session lifecycle |
| `/topic/device/{deviceId}/stream-request` | Child subscribes (device auth) |
| `/topic/signaling/{roomId}` | WebRTC SDP / ICE relay |

---

## Database

Migrations live in `src/main/resources/db/migration/`.

```bash
# Apply migrations (automatic on boot) or inspect
mvn flyway:info
```

Core tables: `users`, `parent_profiles`, `child_profiles`, `devices`, `pairing_codes`, `notification_logs`, `location_history`, `app_usage_logs`, `policies`, `screen_capture_audit_log`, `device_provisioning_events`.

---

## Testing

```bash
# Unit + integration (Testcontainers)
mvn verify

# Single test class
mvn test -Dtest=DevicePairingIntegrationTest
```

**CI:** `.github/workflows/ci.yml` runs `mvn -B verify` on push/PR to `main` and `develop`.

---

## Security

- TLS 1.2+ required in production
- Passwords: BCrypt (strength 12)
- Device ingest: JWT + HMAC request signing (`X-Device-Signature`, `X-Device-Timestamp`, `X-Device-Nonce`)
- Parent PIN required for first silent screen stream
- Screen capture audit log for every stream session

Details: [docs/SECURITY.md](docs/SECURITY.md).

---

## Deployment

### Podman

```bash
podman build -f docker/Dockerfile -t parental-control-api:latest .
podman run -p 8080:8080 --env-file .env parental-control-api:latest
```

### Production checklist

- [ ] Managed PostgreSQL with backups and encryption at rest
- [ ] Redis for rate limiting and HMAC nonce store
- [ ] Secrets in Vault / AWS SSM (not in image)
- [ ] TURN server (coturn or managed) wired via `WEBRTC_*` env
- [ ] Flyway migrations run before traffic shift
- [ ] Prometheus scrape `/actuator/prometheus`
- [ ] Publish OpenAPI artifact on release tag

### Suggested hosting

| Tier | Option |
|------|--------|
| Small | Single VM + managed Postgres |
| Medium | ECS / Cloud Run + RDS + ElastiCache |
| Large | Kubernetes (Helm chart in `helm/`) |

---

## Contract publishing

On each release:

1. Export OpenAPI YAML to `docs/openapi/v{version}.yaml`.
2. Tag: `api-contract-1.x.y`.
3. Mobile repos pin `API_CONTRACT_VERSION` and run Spectral diff in CI.

---

## Related repositories

| Repo | Role |
|------|------|
| [parental-control-parent-app](https://github.com/YOUR_ORG/parental-control-parent-app) | Parent dashboard & live viewer |
| [parental-control-child-app](https://github.com/YOUR_ORG/parental-control-child-app) | Android child agent |

---

## Legal

For lawful parental monitoring with informed consent only. Operators must comply with local privacy laws, app-store policies, and data-retention requirements.

---

## License

[YOUR LICENSE]
