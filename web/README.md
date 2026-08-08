# Parent Web Portal

Browser client for parents. Uses the **same account** as the React Native parent app — devices paired on mobile show up here without re-pairing.

## Prerequisites

- Backend running locally (`backend/`, typically `http://localhost:8080`)
- Parent account already registered (via parent app or API)
- For live streaming: child online with capture permissions; coturn / TURN as in mobile dev setup

## Setup

```bash
cd web
cp .env.example .env   # if needed
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

### Environment

| Variable | Purpose | Default |
|----------|---------|---------|
| `VITE_API_BASE_URL` | Spring API origin | `http://localhost:8080` |
| `VITE_WS_URL` | SockJS URL for STOMP (`/ws`, not `/ws-native`) | `http://localhost:8080/ws` |

Vite also proxies `/api` and `/ws` to `localhost:8080` for same-origin debugging.

### Backend CORS (dev)

[`application-dev.properties`](../backend/src/main/resources/application-dev.properties) allows `http://localhost:5173` by default. Override with:

```bash
export CORS_ALLOWED_ORIGINS=http://localhost:5173
export WEBSOCKET_ALLOWED_ORIGINS=http://localhost:5173
```

For Stripe checkout success/cancel URLs, configure billing return URLs to include the web origin when using the portal.

## Phase 1 (MVP)

- Login / logout (JWT + refresh)
- Device list + detail (status, battery)
- Live updates via STOMP over SockJS (`device-status`, `device-paired`, `device-removed`, alerts, notifications, locations)
- Notifications, location history, installed apps, and recent usage on a device
- Alerts feed
- App-block + screen-time policies
- Optional pairing-code generation (“Add device”) with claim/expiry polling

## Phase 2

- **Live view** on device detail — screen / camera / audio via browser WebRTC (REST signaling poll + same `start-stream` API as the app; parent PIN for silent screen)
- **Stream handoff** — starting live view on web (or app) ends any active stream on the other client for that device
- **Independent sessions** — logging in or out of the app does not sign you out of the web (and vice versa); each client has its own refresh token
- **Geofences** — list / create / delete per device
- **Billing** — subscription + plans + Stripe checkout redirect
- **Audit log** — paginated account actions
- **Settings** — profile display, parent PIN, family handle, delete account

## Scripts

```bash
npm run dev      # Vite on :5173
npm run build    # production bundle in dist/
npm run preview  # serve dist/
```
