# Testing Device Owner + Silent Screen Capture (Phase 3.5)

## Prerequisites

- Java 21 backend running with Flyway V3 applied
- Child app rebuilt with `io.getstream:stream-webrtc-android` (see `child/android/app/build.gradle`)
- Android device or emulator **without** Google accounts (for Device Owner)
- USB debugging enabled

## M1 — Device Owner

```bash
# Factory reset device or use fresh emulator with no accounts
adb shell dpm set-device-owner com.childmonitor/.ChildDeviceAdminReceiver
```

1. Open child app → Permissions → **Full protection**
2. Tap **Check status** → Device Owner ✓
3. Try uninstalling child app → should be blocked

## M2 — MediaProjection (one-time)

1. On **Full protection** screen, tap **Grant screen capture**
2. Accept system dialog **once**
3. Tap **Check status** → Screen capture ✓
4. Complete pairing

## M3 — Silent screen stream

1. Parent app → set PIN under Settings → Parent PIN
2. Parent → device → Live monitoring → Screen share (enter PIN if prompted)
3. Child device should **not** show MediaProjection dialog again
4. Parent viewer shows video within ~10s on same network

## M4 — Protection level

- Heartbeat sends `protectionLevel: FULL` when DO + projection granted
- Parent device detail shows **Full (silent screen)** badge

## M5 — PIN & audit

- First silent screen requires parent PIN (until verified once)
- Backend logs row in `screen_capture_audit_log`
- Child → Service status → **Remove monitoring (revoke)** clears tokens and stops services

## TURN (production)

Set environment variables on backend:

```bash
TURN_URLS=turn:your.turn.server:3478
TURN_USERNAME=user
TURN_CREDENTIAL=secret
```

Parent/child clients fetch ICE config from `GET /api/v1/monitoring/webrtc-config`.

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `dpm set-device-owner` fails | Remove all accounts; factory reset |
| Native stream fails | Re-grant screen capture; check WebRTC dependency |
| No video on parent | Verify signaling room; check STUN/TURN |
| PIN required error | Settings → Parent PIN → Verify |
