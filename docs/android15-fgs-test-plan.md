# Android 15 Foreground Service Timeout — Test Plan

Target app: `com.childmonitor` (child)  
Target SDK: 35 (Android 15)  
Device/emulator: Android 15 (API 35) required for full matrix; Android 14 for legacy-path regression.

## Prerequisites

- Paired child device with backend + FCM configured
- `adb` access to test device
- Backend running (`./scripts/dev/dev-up.sh` or equivalent)

## Enable accelerated timeout (lab only)

```bash
adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS com.childmonitor
adb shell device_config put activity_manager data_sync_fgs_timeout_duration 120000
```

Reset after testing:

```bash
adb shell am compat disable FGS_INTRODUCE_TIME_LIMITS com.childmonitor
adb shell device_config delete activity_manager data_sync_fgs_timeout_duration
```

---

## 1. Burst FGS lifecycle (Android 15+)

| Step | Action | Expected |
|------|--------|----------|
| 1.1 | Pair device, open child app once | Short FGS notification appears ~90s then disappears |
| 1.2 | `adb logcat -s MonitoringFgSvc MonitoringLauncher` | Log: `Starting FGS burst` then `Finishing FGS burst reason=burst_duration_elapsed` |
| 1.3 | Verify no crash | No `RemoteServiceException` / `did not stop within its timeout` |

**Pass criteria:** FGS runs ≤ 90s, stops cleanly, no ANR/crash.

---

## 2. `onTimeout()` graceful shutdown

| Step | Action | Expected |
|------|--------|----------|
| 2.1 | Enable accelerated timeout (120s above) | — |
| 2.2 | Force burst FGS: `adb shell am start-foreground-service -n com.childmonitor/.MonitoringForegroundService -a com.childmonitor.action.MONITORING_BURST` | FGS starts |
| 2.3 | Wait for timeout (~2 min with lab config) | Log: `dataSync FGS timeout — stopping gracefully` |
| 2.4 | Check logcat | `MonitoringBackoff: FGS backoff reason=timeout` |
| 2.5 | Check process | App does **not** crash; no restart loop of FGS every few seconds |

**Pass criteria:** `stopSelf()` within timeout window; exponential backoff recorded; WorkManager one-shot scheduled.

---

## 3. No restart loops

| Step | Action | Expected |
|------|--------|----------|
| 3.1 | Swipe child app from recents | Log: `Task removed — scheduling WorkManager (no FGS restart loop)` |
| 3.2 | Monitor logcat 5 minutes | No repeated `Starting FGS burst` more than once without FCM/user trigger |
| 3.3 | After FGS timeout | No immediate `startForegroundService(MonitoringForegroundService)` from service itself |

**Pass criteria:** FGS start rate bounded; backoff respected after timeout.

---

## 4. Boot path (Android 15)

| Step | Action | Expected |
|------|--------|----------|
| 4.1 | Reboot paired device | No `ForegroundServiceStartNotAllowedException` for `dataSync` |
| 4.2 | Logcat after boot | `WorkManager-only wake reason=BOOT` |
| 4.3 | Wait 15–20 min | Heartbeat received on parent dashboard (WorkManager periodic) |

**Pass criteria:** Boot does not start illegal FGS; monitoring resumes via WorkManager.

---

## 5. Command delivery preserved

| Step | Action | Expected |
|------|--------|----------|
| 5.1 | Background child app (no active stream) | — |
| 5.2 | Parent sends TAP command | FCM wake → command executes on child |
| 5.3 | Logcat | `FcmWakeHandler: FCM wake action=DEVICE_COMMAND` |
| 5.4 | Disable network briefly, re-enable, wait for WorkManager cycle | Pending commands picked up on next worker run |

**Pass criteria:** Commands succeed via FCM; fallback poll via WorkManager within 15 min.

---

## 6. Stream monitoring preserved

| Step | Action | Expected |
|------|--------|----------|
| 6.1 | Parent starts screen share | `ScreenCaptureForegroundService` starts; stream connects |
| 6.2 | During stream, monitoring burst ends | Coordinator stays alive (`activeStream=true`) |
| 6.3 | Parent stops stream | Stream FGS stops; coordinator tears down when idle |
| 6.4 | Camera/audio stream | Typed FGS starts on demand only (no idle pre-warm on API 35) |

**Pass criteria:** Streams work; no idle camera/mic FGS pre-warm on Android 15+.

---

## 7. Heartbeat / monitoring preserved

| Step | Action | Expected |
|------|--------|----------|
| 7.1 | Leave device backgrounded 30+ min | Parent shows device online with recent `lastSeenAt` |
| 7.2 | Logcat | Periodic `MonitoringBurstWorker: WorkManager monitoring cycle` |
| 7.3 | FCM stream wake with app killed | `STREAM_REQUEST` starts burst + stream handler |

**Pass criteria:** Heartbeats continue without 24/7 dataSync FGS.

---

## 8. Exponential backoff

| Step | Action | Expected |
|------|--------|----------|
| 8.1 | Trigger two consecutive FGS timeouts (lab config) | Second backoff delay ≥ 2× first (~2 min → ~4 min with base 60s) |
| 8.2 | FCM command wake during backoff | Command still executes (wake lock path, bypass backoff for FCM burst when `force=true`) |
| 8.3 | Successful burst completion | Log shows backoff counter reset (`recordFgsSuccess`) |

**Pass criteria:** Backoff increases on failure; FCM/commands not blocked; success resets backoff.

---

## 9. Legacy regression (Android 14 device/emulator)

| Step | Action | Expected |
|------|--------|----------|
| 9.1 | Run on API 34 device | Perpetual monitoring FGS behavior (legacy path) |
| 9.2 | Command + stream tests | Same functional results as before |

**Pass criteria:** No regression on pre-Android-15 devices.

---

## 10. Unit tests (local)

```bash
cd child/android
./gradlew :app:testDebugUnitTest --tests "com.childmonitor.monitoring.MonitoringBackoffPolicyTest"
```

**Pass criteria:** All backoff policy tests green.

---

## Failure signatures to watch

| Log / exception | Meaning |
|-----------------|---------|
| `RemoteServiceException: ... dataSync did not stop within its timeout` | Missing or broken `onTimeout()` |
| `ForegroundServiceStartNotAllowedException ... dataSync` | Illegal FGS start (boot/backoff) |
| Repeated `Starting FGS burst` every few seconds | Restart loop — fail |
| `ForegroundServiceDidNotStartInTimeException` | `startForeground()` not called within 5s |

---

## Sign-off checklist

- [ ] Test 1 — Burst lifecycle
- [ ] Test 2 — onTimeout
- [ ] Test 3 — No restart loops
- [ ] Test 4 — Boot WorkManager-only
- [ ] Test 5 — Command delivery
- [ ] Test 6 — Stream monitoring
- [ ] Test 7 — Heartbeat
- [ ] Test 8 — Backoff
- [ ] Test 9 — Legacy API 34
- [ ] Test 10 — Unit tests
