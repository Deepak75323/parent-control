# Release Validation Matrix

Automated evidence: backend `mvn verify`, parent `npm test`.  
Manual device rows require physical hardware — mark **Pending QA** until executed.

## E2E Matrix (RB2)

| Scenario | Test Steps | Expected | Actual | Pass |
|----------|------------|----------|--------|------|
| Pairing | Parent code → child pair → credentials saved | ONLINE, JWT issued | Integration test `AuthIntegrationTest.pairChildDevice` | **Pass** (CI) |
| Heartbeat | Child heartbeat POST | status ONLINE, lastSeenAt updated | `DevicePresenceIntegrationTest` | **Pass** (CI) |
| Stale → recovery | Mark OFFLINE → heartbeat | Returns ONLINE (not 401) | `DevicePresenceIntegrationTest.staleOfflineDeviceCanHeartbeatBackToOnline` | **Pass** (CI) |
| Screen Share | Parent start → child offer → parent answer → RTCView | Live video, ICE connected | Pending QA on device | **Pending** |
| Audio | Start AUDIO stream | Parent hears mic | Pending QA | **Pending** |
| Camera | Start CAMERA stream | Parent sees camera | Pending QA | **Pending** |
| Remote Control | TAP/SWIPE during SCREEN | Child executes gesture | Pending QA | **Pending** |
| Accessibility | Disable → re-enable | Violation reported; control blocked | Pending QA | **Pending** |
| Device Owner | ADB provision → kiosk | Kiosk active, silent screen | Pending QA | **Pending** |
| Revoke | REVOKE_MONITORING command | REVOKED + tokenVersion++, 401 | `SecurityIntegrationTest.rejectsRevokedDeviceToken` | **Pass** (CI) |
| Reboot | Reboot child | MFS restarts; projection re-grant | Pending QA | **Pending** |
| Network Loss | Airplane 30s | ICE disconnected → grace/reconnect UX | Code: 8s grace + Reconnect button | **Partial** |
| Network Restore | Re-enable WiFi | Reconnect or auto recovery poll | Code: `reconnectParentViewer` | **Partial** |
| Background | Home child app | Stream continues (FGS) | Pending QA | **Pending** |
| Screen Off | Turn off display | Audio/camera FGS continues | Pending QA | **Pending** |
| Launcher Hidden | Hide launcher alias | MFS + native coordinator alive | Pending QA | **Pending** |
| Recents Swipe | Swipe child from recents | MFS `onTaskRemoved` restart | Pending QA | **Pending** |
| Process Kill | `adb shell am force-stop` | MFS restarts; projection lost | Pending QA | **Pending** |

## FCM + Doze (RB3)

| Check | Evidence |
|-------|----------|
| `google-services.json` optional with warning | `child/android/app/build.gradle` |
| Token persist + upload | `FcmWakeHandler.onNewToken`, `FcmTokenRegistrar` |
| High-priority data wake | `FcmWakeService` priority=high |
| STREAM_REQUEST routes to coordinator | `FcmWakeHandler.handleMessage` + `NativeStreamingCoordinator.handleStreamRequestFromWake` |
| Prod FCM required when enabled | `ProductionSecurityValidator` |
| Doze on device | **Pending QA** — capture `adb logcat FcmWakeHandler` |

## Remote Control Accuracy (RB4)

| Check | Implementation | Device QA |
|-------|----------------|-----------|
| Contain-fit mapping | `mapContainTouchToNorm` | Unit test **Pass** |
| Video aspect from track | `videoAspectFromStream` | Unit test **Pass** |
| Rotation on child | `getRealMetrics` (rotation-aware) | Pending QA |
| Multi-resolution | Normalized 0–1 coords | Pending QA |

## OEM QA (RB9)

| OEM | Screen | Audio | Camera | Remote | FCM | Background |
|-----|--------|-------|--------|--------|-----|------------|
| Samsung | Pending | Pending | Pending | Pending | Pending | Pending |
| Xiaomi | Pending | Pending | Pending | Pending | Pending | Pending |
| Oppo | Pending | Pending | Pending | Pending | Pending | Pending |
| Vivo | Pending | Pending | Pending | Pending | Pending | Pending |
| OnePlus | Pending | Pending | Pending | Pending | Pending | Pending |

Use `OemPowerWhitelistHelper` battery + MIUI autostart before each OEM run.

## Production Checklist (RB5)

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `JWT_SECRET` ≥ 256-bit (not default)
- [ ] `APP_ENCRYPTION_KEY` set
- [ ] `HMAC_REQUIRED=true`
- [ ] `FCM_ENABLED=true` + `FCM_SERVER_KEY`
- [ ] `PLAY_INTEGRITY_REQUIRED=true` (recommended)
- [ ] TLS termination at ingress (not in app chart)
- [ ] Release APK: `RELEASE_STORE_FILE` set (no debug fallback)
- [ ] R8/ProGuard enabled on child + parent release builds
