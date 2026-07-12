# parental-control-parent

React Native application for parents: device pairing, child activity dashboard, live screen viewing, policies, alerts, and remote commands.

**Runs on:** Parent phones (Android; iOS planned)  
**Backend contract:** `API_CONTRACT_VERSION=1.0.0` (see [parental-control-backend](https://github.com/YOUR_ORG/parental-control-backend))

---

## Overview

| Feature | Description |
|---------|-------------|
| Authentication | Register, login, JWT refresh |
| Device management | Pairing codes, QR, device list & detail |
| Real-time updates | STOMP WebSocket (locations, notifications, status, alerts) |
| Live monitoring | WebRTC viewer for child screen / camera / audio |
| Protection indicator | `STANDARD` · `DEVICE_OWNER` · `FULL` badge per device |
| Parent PIN | Required before first silent screen stream |
| Policies | App blocks, screen time, geofences, bedtime, streaming rules |
| Alerts & audit | Violations, geofence events, screen-capture history |

**Does not run on the child device.** Pairing is initiated here; enforcement happens in `parental-control-child`.

---

## Tech stack

| Layer | Choice |
|-------|--------|
| Framework | React Native 0.76+ |
| Language | TypeScript |
| Navigation | `@react-navigation/native-stack` |
| State | Zustand |
| HTTP | Axios |
| Real-time | `@stomp/stompjs` |
| WebRTC | `react-native-webrtc` |
| Maps | `react-native-maps` |
| Secure storage | `react-native-keychain` (recommended) |
| Pinning | `react-native-ssl-pinning` (production) |

---

## Repository structure

```
parental-control-parent/
├── .github/workflows/
│   ├── ci.yml
│   └── android-release.yml
├── android/
├── ios/                          # Future
├── contracts/
│   └── openapi-v1.0.0.yaml       # Pinned from backend release
├── src/
│   ├── app/
│   ├── navigation/
│   │   └── RootNavigator.tsx
│   ├── screens/
│   │   ├── auth/                 # Login, Register, Splash
│   │   ├── dashboard/
│   │   ├── devices/              # List, Detail, Pairing
│   │   ├── monitoring/           # LiveMonitoringScreen
│   │   ├── policies/
│   │   ├── alerts/
│   │   └── settings/             # ParentPinScreen
│   ├── components/
│   ├── services/
│   │   ├── api.ts
│   │   ├── websocketService.ts
│   │   ├── webrtcService.ts
│   │   └── pinService.ts
│   ├── store/
│   │   ├── authStore.ts
│   │   └── realtimeStore.ts
│   ├── hooks/
│   ├── types/
│   └── utils/
├── .env.example
├── package.json
└── README.md
```

---

## Prerequisites

- Node.js 18+
- JDK 17+ (Android builds)
- Android Studio + SDK
- Running backend ([parental-control-backend](https://github.com/YOUR_ORG/parental-control-backend))

---

## Quick start

```bash
npm install
cp .env.example .env
npm start
```

In a second terminal:

```bash
npm run android
# npm run ios   # when supported
```

### Emulator API URL

| Platform | `API_BASE_URL` |
|----------|----------------|
| Android emulator | `http://10.0.2.2:8080` |
| iOS simulator | `http://localhost:8080` |
| Physical device | `http://<your-lan-ip>:8080` |

---

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `API_BASE_URL` | Yes | Backend REST base (no trailing slash) |
| `WS_URL` | Yes | STOMP endpoint, e.g. `ws://10.0.2.2:8080/ws-native` |
| `API_CONTRACT_VERSION` | Yes | Must match backend OpenAPI release |
| `CERT_PINS` | Prod | Comma-separated SHA-256 SPKI pins |
| `ENABLE_SSL_PINNING` | Prod | `true` in production builds |

Example `.env`:

```env
API_BASE_URL=http://10.0.2.2:8080
WS_URL=ws://10.0.2.2:8080/ws-native
API_CONTRACT_VERSION=1.0.0
ENABLE_SSL_PINNING=false
```

---

## User flows

### 1. Register & pair a child device

1. **Register** → accept consent checkbox.
2. **Device list** → **Generate pairing code** (8 characters, 15 min TTL).
3. On child app: enter code → child receives device token.
4. Device appears on dashboard with online status after first heartbeat.

### 2. Live screen monitoring

1. Open device → **Live monitoring**.
2. If device is `FULL` protection and PIN not yet verified → **Parent PIN** screen.
3. Tap **Screen share** → backend creates session → child starts native WebRTC publisher.
4. Viewer connects via signaling + ICE (STUN/TURN from backend).

### 3. Policies

1. Device → **Policies** → create app block / screen time / geofence.
2. Child syncs within ~5 min (foreground service on child app).
3. Violations appear in **Alerts** and real-time WebSocket queue.

---

## Real-time (STOMP)

After login, the app connects to `WS_URL` with the parent access token and subscribes to:

- `/user/queue/locations`
- `/user/queue/notifications`
- `/user/queue/device-status`
- `/user/queue/alerts`
- `/user/queue/policy-violations`
- `/user/queue/streaming-state`

Connection status is shown on the dashboard (`realtimeStore`).

---

## Protection levels

Displayed on device detail from backend `protectionLevel`:

| Level | Meaning |
|-------|---------|
| `STANDARD` | Device Admin / accessibility; per-session screen consent |
| `DEVICE_OWNER` | Device Owner provisioned; stronger tamper resistance |
| `FULL` | Device Owner + MediaProjection granted → silent native stream |

---

## Scripts

| Command | Description |
|---------|-------------|
| `npm start` | Metro bundler |
| `npm run android` | Build & run on Android |
| `npm run ios` | Build & run on iOS (when added) |
| `npm run lint` | ESLint |
| `npm test` | Jest unit tests |

---

## Testing

```bash
npm test
npm run lint
```

### Manual test checklist

- [ ] Register → login → token refresh on 401
- [ ] Generate pairing code → child pairs → device online
- [ ] WebSocket: location/notification updates without refresh
- [ ] Live screen stream completes (offer/answer via API)
- [ ] Parent PIN blocks silent stream until verified
- [ ] Policy create → child block overlay (with child app)
- [ ] Revoke monitoring → child stops capture service

### E2E (optional)

Detox or Maestro against staging backend.

---

## CI/CD

`.github/workflows/ci.yml`:

- `npm ci`
- `npm run lint`
- `tsc --noEmit`
- `npm test`
- OpenAPI contract diff against `contracts/openapi-*.yaml`

Release workflow: signed AAB/APK to Play Console internal track.

---

## Security

- Store tokens in Keychain / EncryptedSharedPreferences (via native module)
- Enable certificate pinning in production (`ENABLE_SSL_PINNING=true`)
- Never log access tokens or parent PIN
- Require PIN for sensitive actions (silent stream, revoke monitoring)

---

## Build flavors (recommended)

| Flavor | `API_BASE_URL` | Pinning |
|--------|----------------|---------|
| `dev` | Local / staging | Off |
| `staging` | `https://api.staging.example.com` | On |
| `prod` | `https://api.example.com` | On |

---

## Related repositories

| Repo | Role |
|------|------|
| [parental-control-backend](https://github.com/YOUR_ORG/parental-control-backend) | API, signaling, audit |
| [parental-control-child](https://github.com/YOUR_ORG/parental-control-child) | Child Android agent |

---

## Known limitations

- Child monitoring is **Android-only**; iOS child agent not supported
- WebRTC behind strict NAT requires TURN (configure on backend)
- iOS parent app: planned

---

## Legal

Parents must obtain informed consent where required by law. This app is intended for lawful parental supervision of devices you own or are authorized to manage.

---

## License

[YOUR LICENSE]
