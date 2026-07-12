# Step-by-Step Implementation Plan

## Phase 1 — Foundation ✅ Complete

- [x] Monorepo: `backend/`, `parent/`, `child/`
- [x] Single Spring Boot backend module
- [x] PostgreSQL schema + Flyway `V1__initial_schema.sql`
- [x] Parent registration/login with JWT access + refresh
- [x] Device pairing code (15 min TTL) + QR payload
- [x] Child device pair → device JWT
- [x] Heartbeat API (battery, location, online status)
- [x] Podman Compose (Postgres, Redis, MinIO)
- [x] Swagger/OpenAPI
- [x] Integration tests (Testcontainers)

## Phase 2 — Monitoring & Real-Time ✅ Complete

### Backend
- [x] Notification, location, usage-stats ingest
- [x] Parent read APIs (paginated)
- [x] Alerts + low-battery engine
- [x] STOMP WebSocket parent queues

### Child app
- [x] NotificationListener, UsageStats, Location, ForegroundService, BootReceiver

### Parent app
- [x] STOMP client, dashboard, device screens, alerts

## Phase 3 — Policies & Control ✅ Complete

### Backend
- [x] Flyway `V2__phase3_policies.sql` — policies, rules, assignments, sync state, violations, signaling
- [x] Policy CRUD APIs (`/api/v1/policies/*`)
- [x] Child sync (`GET /api/v1/child/policies`, `POST ack`)
- [x] Violations (`POST /api/v1/events/policy-violations`)
- [x] WebSocket: policy-updates, policy-violations, geofence-events, streaming-state
- [x] WebRTC signaling REST + `/topic/signaling/{roomId}`
- [x] Streaming: screen, camera, audio sessions

### Child app
- [x] PolicySyncManager + PolicyEnforcementEngine
- [x] AppBlockingAccessibilityService + BlockOverlayActivity
- [x] GeofenceMonitor on location sync
- [x] Screen time tracking via usage stats
- [x] WebRTC publisher (`react-native-webrtc`)
- [x] Device Admin (`ChildDeviceAdminReceiver`) + hide launcher (`TamperProtectionManager`)

### Parent app
- [x] Policies management screen
- [x] Live monitoring with WebRTC viewer
- [x] Real-time violation / geofence alerts

## Phase 3.5 — Device Owner + Native MediaProjection ✅ Complete

Silent screen capture (no per-session MediaProjection dialog), stronger uninstall block, ADB/QR provisioning path.

**Plan:** [DEVICE_OWNER_MEDIAPROJECTION_PLAN.md](./DEVICE_OWNER_MEDIAPROJECTION_PLAN.md) · **Tests:** [TESTING_DEVICE_OWNER.md](./TESTING_DEVICE_OWNER.md)

| Milestone | Status |
|-----------|--------|
| M1 Device Owner + setup UI | ✅ |
| M2 MediaProjection consent + token store | ✅ |
| M3 Native screen service + WebRTC publisher | ✅ |
| M4 Capability flags + parent protection badge | ✅ |
| M5 PIN, audit logs, revocation, TURN config | ✅ |

## Phase 4 — Billing ✅ Complete

- Stripe checkout session + webhook (`/api/v1/billing/*`)
- Razorpay webhook stub
- Plans: free / standard / family
- 14-day trial on parent registration
- Feature gates: device limits, policies, live stream
- Parent app subscription screen

## Phase 5 — Scale & Ops ✅ Complete (Android-only product)

- Kafka ingest topics + publisher on monitoring ingest + consumer
- Redis rate limiting (auth + device ingest)
- Prometheus via `micrometer-registry-prometheus` + `/actuator/prometheus`
- Per-component GitHub Actions CI workflows
- Helm chart `helm/parental-control-api/`
- Repo split script `scripts/split-repos.sh`
- HMAC key encryption at rest (`APP_ENCRYPTION_ENABLED`)
- **No iOS** — Android child + parent (RN) only

## Mobile Native Modules (Child App)

| Module | Android API | Status |
|--------|-------------|--------|
| NotificationCapture | NotificationListenerService | ✅ Phase 2 |
| UsageTracker | UsageStatsManager | ✅ Phase 2 |
| Persistence | ForegroundService, BOOT_COMPLETED | ✅ Phase 2 |
| LocationReader | LocationManager | ✅ Phase 2 |
| AppBlocking | AccessibilityService | ✅ Phase 3 |
| GeofenceMonitor | Location + haversine | ✅ Phase 3 |
| ScreenCapture | MediaProjection / WebRTC | ✅ Phase 3 (RN); native silent path → Phase 3.5 |
| CameraStream | Camera2 / WebRTC | ✅ Phase 3 |
| AudioStream | AudioRecord / WebRTC | ✅ Phase 3 |
| Device Admin / hide launcher | DevicePolicyManager + component disable | ✅ Phase 3 |

## Phase 4 — Security + Kiosk ✅ Complete

- [x] HMAC request signing for child device POST APIs (`app.hmac.required`, headers `X-Device-*`)
- [x] Redis rate limiting (auth + device ingest)
- [x] Parent audit log API (`GET /api/v1/audit`) — policies, streams, PIN, commands
- [x] Remote device commands (`POST /api/v1/devices/{id}/commands`) — kiosk, revoke
- [x] Lock Task / kiosk mode (`KioskActivity`, `DeviceOwnerManager`)
- [x] Certificate pinning scaffold (`network_security_config.xml` on child Android)

## Security Hardening (Ongoing)

- [x] JWT for REST and WebSocket CONNECT
- [x] HMAC request signing for child APIs
- [x] Parent PIN for sensitive actions
- [x] Audit log on policy changes, streaming, commands
- [x] Certificate pinning scaffold (enable pins for production HTTPS host)
