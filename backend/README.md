# Parental Control — Backend API

Spring Boot **3.4.5** / Java **21** API for the parental control platform: REST, JWT auth, STOMP WebSocket, policies, monitoring, billing, audit, and device commands.

**Full setup, architecture, env vars, and troubleshooting:**  
→ **[docs/DEVELOPER_ONBOARDING.md](../docs/DEVELOPER_ONBOARDING.md)**

---

## Quick start

```bash
# From repo root (Podman only — never docker)
./scripts/dev/compose.sh up -d postgres redis

cd backend
cp .env.example .env
# Recommended for local dev: BILLING_ENFORCEMENT=false, HMAC_REQUIRED=false, KAFKA_ENABLED=false

mvn clean install -DskipTests
mvn spring-boot:run
```

| URL | Description |
|-----|-------------|
| http://localhost:8080 | API base |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/actuator/health | Health check |

---

## Package structure

```
com.parentalcontrol/
├── auth/           # Register, login, refresh
├── user/           # Profiles, parent PIN
├── device/         # Pairing, heartbeat, HMAC, remote commands
├── monitoring/     # Ingest, alerts, streaming, signaling, Kafka
├── policy/         # Policies, child sync, violations
├── billing/        # Stripe, subscriptions, feature gates
├── audit/          # Parent audit log
├── config/         # Security, WebSocket, rate limits
└── common/         # JWT, exceptions, encryption
```

Flyway migrations: `src/main/resources/db/migration/V1`–`V5`

---

## API overview

| Area | Base path | Auth |
|------|-----------|------|
| Auth | `/api/v1/auth/*` | Public / refresh |
| Devices (parent) | `/api/v1/devices/*` | Parent JWT |
| Devices (child) | `/api/v1/device/*` | Device JWT (+ optional HMAC) |
| Monitoring (parent) | `/api/v1/devices/{id}/*` | Parent JWT |
| Monitoring (child) | `/api/v1/device/notifications`, `location`, `usage-stats` | Device JWT |
| Policies | `/api/v1/policies/*`, `/api/v1/child/policies` | Parent / Device |
| Streaming | `/api/v1/monitoring/*`, signaling | Parent / Device |
| Billing | `/api/v1/billing/*` | Parent / webhooks |
| Audit | `/api/v1/audit` | Parent JWT |
| WebSocket | `/ws-native` (STOMP) | Parent Bearer |

Details: [docs/API.md](../docs/API.md) · Live docs: Swagger UI when server is running.

---

## Configuration

Defaults in `src/main/resources/application.yml`; overrides via environment or `backend/.env`:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/parental_control
SPRING_DATASOURCE_USERNAME=pcp
SPRING_DATASOURCE_PASSWORD=pcp_secret
SPRING_DATA_REDIS_HOST=localhost
JWT_SECRET=change-me-in-production-use-256-bit-minimum-secret-key!!
HMAC_REQUIRED=false
BILLING_ENFORCEMENT=false
KAFKA_ENABLED=false
```

See [docs/DEVELOPER_ONBOARDING.md §8](../docs/DEVELOPER_ONBOARDING.md#8-environment-variables) for the full table.

---

## Tests

```bash
mvn test   # requires Podman for Testcontainers (see docs/DEVELOPER_ONBOARDING.md)
```

---

## Related docs

- [DEVELOPER_ONBOARDING.md](../docs/DEVELOPER_ONBOARDING.md) — full stack setup
- [SECURITY.md](../docs/SECURITY.md) — HMAC, rate limits, audit
- [BILLING.md](../docs/BILLING.md) — Stripe, plans, trials
- [OPS.md](../docs/OPS.md) — CI, Helm, Kafka
