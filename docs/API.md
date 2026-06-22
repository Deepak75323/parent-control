# API Reference

Base URL: `http://localhost:8080`  
Swagger UI: `/swagger-ui.html`  
WebSocket (React Native): `ws://localhost:8080/ws-native` (STOMP)  
WebSocket (browser): `http://localhost:8080/ws` (SockJS + STOMP)

All REST responses use this envelope:

```json
{
  "success": true,
  "message": "optional",
  "data": { },
  "timestamp": "2026-05-19T12:00:00Z"
}
```

---

## Authentication

### POST `/api/v1/auth/register` — Public

**Request:**
```json
{
  "email": "parent@example.com",
  "password": "SecurePass123!",
  "firstName": "Jane",
  "lastName": "Doe",
  "consentAccepted": "true"
}
```

**Response `201`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "<jwt>",
    "refreshToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresInSeconds": 900
  }
}
```

### POST `/api/v1/auth/login` — Public

**Request:**
```json
{
  "email": "parent@example.com",
  "password": "SecurePass123!"
}
```

**Response `200`:** Same shape as register.

### POST `/api/v1/auth/refresh` — Public

**Request:**
```json
{
  "refreshToken": "<jwt>"
}
```

**Response `200`:** New access + refresh tokens.

---

## Devices (Parent) — Bearer parent access token

### POST `/api/v1/devices/pairing-code`

**Request:** `{}` or `{ "childProfileId": "uuid" }`

**Response:**
```json
{
  "success": true,
  "data": {
    "code": "ABCD1234",
    "qrPayload": "pcp://pair?code=ABCD1234&api=...",
    "expiresAt": "2026-05-19T12:15:00Z",
    "expiresInSeconds": 900
  }
}
```

### GET `/api/v1/devices`

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "deviceName": "Child Phone",
      "status": "ONLINE",
      "batteryLevel": 85,
      "charging": false,
      "networkType": "WIFI",
      "lastSeenAt": "2026-05-19T12:00:00Z",
      "lastLatitude": 37.7749,
      "lastLongitude": -122.4194,
      "foregroundApp": "com.example.app"
    }
  ]
}
```

### GET `/api/v1/devices/{deviceId}`

**Response:** Single device object (same fields as list item).

---

## Device (Child) — Bearer device token (except pair)

### POST `/api/v1/device/pair` — Public

**Request:**
```json
{
  "pairingCode": "ABCD1234",
  "deviceName": "Child Phone",
  "androidId": "unique-android-id",
  "consentAccepted": "true",
  "deviceModel": "Pixel 8",
  "osVersion": "14",
  "appVersion": "1.0.0"
}
```

**Response `201`:**
```json
{
  "success": true,
  "message": "Device paired successfully",
  "data": {
    "deviceId": "uuid",
    "deviceToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresInSeconds": 2592000
  }
}
```

### POST `/api/v1/device/heartbeat` — Device token

**Request:**
```json
{
  "batteryLevel": 85,
  "charging": false,
  "networkType": "WIFI",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "foregroundApp": "com.example.app",
  "fcmToken": "optional"
}
```

**Response:** Updated device summary. Publishes `/user/queue/device-status`. May create `LOW_BATTERY` alert if battery ≤ 20%.

---

## Monitoring ingest (Child) — Device token

### POST `/api/v1/device/notifications`

**Request:**
```json
{
  "notifications": [
    {
      "packageName": "com.example.chat",
      "title": "New message",
      "body": "Hello",
      "postedAt": "2026-05-19T10:00:00Z"
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "data": { "accepted": 1 }
}
```

Publishes each notification to parent `/user/queue/notifications`.

### POST `/api/v1/device/location`

**Request:**
```json
{
  "latitude": 37.7749,
  "longitude": -122.4194,
  "accuracyM": 12.5,
  "recordedAt": "2026-05-19T10:05:00Z"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "accuracyM": 12.5,
    "recordedAt": "2026-05-19T10:05:00Z"
  }
}
```

Publishes to parent `/user/queue/locations`.

### POST `/api/v1/device/usage-stats`

**Request:**
```json
{
  "entries": [
    {
      "packageName": "com.example.game",
      "usageMs": 3600000,
      "recordedAt": "2026-05-19T11:00:00Z"
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "data": { "accepted": 1 }
}
```

Publishes each entry to parent `/user/queue/usage-stats`.

---

## Monitoring read (Parent) — Parent token

### GET `/api/v1/devices/{deviceId}/notifications?page=0&size=20`

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "packageName": "com.example.chat",
        "title": "New message",
        "body": "Hello",
        "postedAt": "2026-05-19T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

### GET `/api/v1/devices/{deviceId}/location-history?page=0&size=50`

**Response:** Paginated list of location points (`id`, `latitude`, `longitude`, `accuracyM`, `recordedAt`).

### GET `/api/v1/devices/{deviceId}/usage-stats?page=0&size=50`

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "deviceId": "uuid",
        "packageName": "com.example.game",
        "usageMs": 3600000,
        "recordedAt": "2026-05-19T11:00:00Z"
      }
    ],
    "page": 0,
    "size": 50,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

---

## Alerts (Parent)

### GET `/api/v1/alerts?page=0&size=20`

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "deviceId": "uuid",
        "alertType": "LOW_BATTERY",
        "severity": "WARNING",
        "title": "Low battery",
        "message": "Device battery is at 15%",
        "payloadJson": { "batteryLevel": 15 },
        "readAt": null,
        "createdAt": "2026-05-19T12:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

---

## Policies (Parent)

### GET `/api/v1/policies?type=APP_BLOCK`

Lists policies for authenticated parent. Optional `type`: `APP_BLOCK`, `SCREEN_TIME`, `DEVICE_SCHEDULE`, `GEOFENCE`, `STREAMING`.

### POST `/api/v1/policies/app-block`

**Request:**
```json
{
  "name": "Block Chrome",
  "deviceIds": ["device-uuid"],
  "rules": [
    { "packageName": "com.android.chrome", "scheduleType": "ALWAYS" }
  ]
}
```

### POST `/api/v1/policies/screen-time`

**Request:**
```json
{
  "name": "Chrome 1h daily",
  "deviceIds": ["device-uuid"],
  "packageName": "com.android.chrome",
  "dailyLimitMinutes": 60,
  "actionOnLimit": "BLOCK_APP",
  "warnAtPercent": 80
}
```

### POST `/api/v1/policies/geofence`

**Request:**
```json
{
  "name": "School zone",
  "deviceIds": ["device-uuid"],
  "geofenceName": "School",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "radiusM": 200
}
```

### DELETE `/api/v1/policies/{policyId}`

---

## Policies (Child)

### GET `/api/v1/child/policies`

Returns bundled policies for authenticated device:

```json
{
  "success": true,
  "data": {
    "policyVersion": "a1b2c3d4e5f6",
    "deviceId": "uuid",
    "appBlocks": [],
    "screenTime": [],
    "deviceSchedules": [],
    "geofences": [],
    "streaming": {
      "screenShareAllowed": true,
      "cameraAllowed": true,
      "audioAllowed": true,
      "enabled": true
    }
  }
}
```

### POST `/api/v1/child/policies/ack`

**Request:** `{ "policyVersion": "a1b2c3d4e5f6" }`

---

## Events (Child)

### POST `/api/v1/events/policy-violations`

**Request:**
```json
{
  "violationType": "APP_BLOCKED_ATTEMPT",
  "packageName": "com.android.chrome",
  "policyId": "uuid",
  "details": { "reason": "schedule" }
}
```

---

## Streaming (Parent)

### POST `/api/v1/monitoring/start-screen-share`

**Request:**
```json
{
  "deviceId": "uuid"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "sessionId": "uuid",
    "deviceId": "uuid",
    "signalingRoomId": "screen-{deviceId}-{sessionId}",
    "status": "PENDING",
    "startedAt": "2026-05-19T12:00:00Z",
    "message": "WebRTC signaling room created..."
  }
}
```

### POST `/api/v1/monitoring/start-stream`

**Request:**
```json
{
  "deviceId": "uuid",
  "streamType": "SCREEN"
}
```

`streamType`: `SCREEN`, `CAMERA`, or `AUDIO`.

### POST `/api/v1/monitoring/stop-screen-share/{sessionId}`

**Response:** Session with `status: "ENDED"`.

### POST `/api/v1/monitoring/signaling/{roomId}`

Exchange WebRTC SDP/ICE.

**Request:**
```json
{
  "sessionId": "uuid",
  "messageType": "offer",
  "payload": "{...sdp json...}"
}
```

`messageType`: `offer`, `answer`, `ice`.

### GET `/api/v1/monitoring/signaling/{roomId}`

Returns last 50 signaling messages.

---

## Streaming (Child)

### GET `/api/v1/child/streaming/pending`

Lists `PENDING` stream sessions for the device.

---

## WebSocket (STOMP)

### Connect

- **URL:** `ws://host:8080/ws-native`
- **Headers:** `Authorization: Bearer <parent_access_token>`

### Subscribe (parent)

| Destination | Event payload |
|-------------|----------------|
| `/user/queue/locations` | `{ deviceId, id, latitude, longitude, accuracyM, recordedAt }` |
| `/user/queue/notifications` | `{ deviceId, id, packageName, title, body, postedAt }` |
| `/user/queue/device-status` | `{ deviceId, status, batteryLevel, charging, networkType, latitude, longitude, foregroundApp, lastSeenAt }` |
| `/user/queue/usage-stats` | `{ deviceId, id, packageName, usageMs, recordedAt }` |
| `/user/queue/alerts` | Alert object (same as REST) |
| `/user/queue/policy-updates` | `{ eventType, deviceId, policyVersion }` |
| `/user/queue/policy-violations` | `{ eventType, deviceId, violationType, alertId }` |
| `/user/queue/geofence-events` | `{ eventType, deviceId, geofenceName }` |
| `/user/queue/streaming-state` | `{ roomId, status, streamType, deviceId }` |
| `/topic/signaling/{roomId}` | WebRTC signaling payload |
| `/topic/device/{deviceId}/stream-request` | Child: incoming stream request |
| `/topic/device/{deviceId}/policy-updates` | Child: policy version changed |

### Example (JavaScript)

```javascript
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://10.0.2.2:8080/ws-native',
  connectHeaders: { Authorization: 'Bearer ' + accessToken },
  onConnect: () => {
    client.subscribe('/user/queue/notifications', msg => console.log(JSON.parse(msg.body)));
    client.subscribe('/user/queue/locations', msg => console.log(JSON.parse(msg.body)));
    client.subscribe('/user/queue/device-status', msg => console.log(JSON.parse(msg.body)));
  },
});
client.activate();
```

---

## Error codes

| HTTP | Code | Meaning |
|------|------|---------|
| 400 | `VALIDATION_ERROR` | Invalid request body |
| 401 | `UNAUTHORIZED` / `INVALID_CREDENTIALS` | Missing or bad token |
| 403 | `FORBIDDEN` | Device not owned by parent |
| 404 | `NOT_FOUND` / `DEVICE_NOT_FOUND` | Resource missing |
| 409 | `CONFLICT` / `USER_EXISTS` | Duplicate resource |
