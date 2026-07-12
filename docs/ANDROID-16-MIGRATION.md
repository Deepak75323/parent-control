# Android 16 (API 36) Migration & Compliance Program

Full migration deliverable for the parental control child app (`com.childmonitor`).

**Baseline:** targetSdk 35, readiness 45/100  
**Post-migration:** targetSdk 36, estimated readiness **78/100** (automated); **90+** after device-farm validation

---

## 1. Android 16 Impact Assessment

| Platform area | Android 16 impact | Current mitigation |
|---------------|-------------------|-------------------|
| **Foreground services** | Continued typed FGS enforcement; dataSync 6h/24h cap; `onTimeout` mandatory | `MonitoringPlatform.useBurstFgs()`, `TypedStreamFgsTimeout` |
| **MediaProjection** | Single VirtualDisplay per instance; FGS before `getMediaProjection`; no BOOT FGS | `MediaProjectionCaptureGate`, `ScreenCaptureForegroundService` |
| **Accessibility** | No new breaking changes; sideload restrictions unchanged | `AppBlockingAccessibilityService`, Device Owner |
| **Background activity launch** | Stricter BAL checks for hidden child app | `Android16CompatibilityLayer.launchStreamBootstrap` → headless fallback |
| **AlarmManager** | Exact alarms require permission (API 31+) | WorkManager replaces alarm-driven recovery |
| **WorkManager** | Expedited quota limits (API 31+) | `BootRecoveryWorker` with `RUN_AS_NON_EXPEDITED` fallback |
| **Notifications** | POST_NOTIFICATIONS required (API 33+) | Manifest + runtime prompt in setup |
| **Power management** | Stronger app standby; edge-to-edge mandatory at target 36 | Burst FGS + OEM whitelist (P1-10); `applyWindowInsets` |
| **Non-SDK interfaces** | Greylist/blocklist expansion | Guarded `SystemProperties` reads in OEM diagnostics |

---

## 2. Compatibility Risks

See [`infra/android/android16-risk-matrix.yaml`](../infra/android/android16-risk-matrix.yaml).

**Critical (mitigated):** dual VirtualDisplay, BOOT mediaProjection FGS  
**High (monitored):** OEM battery kill, typed FGS timeout during long streams  
**Medium:** non-SDK OEM property reads, accessibility on non-DO devices

---

## 3. Architecture Changes

```
┌──────────────────────────────────────────────────────────────┐
│                   AndroidPlatform (API 24–36+)                │
│  useBurstFgs | requiresMediaProjectionCaptureGate | edgeToEdge │
└────────────┬───────────────────────────────┬─────────────────┘
             │                               │
   Android16FeatureFlags          Android16CompatibilityLayer
   (runtime toggles)              (BAL fallback, insets, boot FGS)
             │                               │
   Android16ReadinessEvaluator    MonitoringPlatform / ScreenFgsStartupGate
   (0–100 fleet score)            (delegates to AndroidPlatform)
```

---

## 4. File-by-File Changes

| File | Change |
|------|--------|
| `child/android/build.gradle` | `compileSdkVersion` / `targetSdkVersion` → **36** |
| `platform/AndroidPlatform.kt` | **NEW** — central API capability detection |
| `platform/Android16FeatureFlags.kt` | **NEW** — runtime migration flags |
| `platform/Android16CompatibilityLayer.kt` | **NEW** — BAL/edge-to-edge/boot fallbacks |
| `platform/Android16ReadinessEvaluator.kt` | **NEW** — 0–100 readiness score |
| `monitoring/MonitoringPlatform.kt` | Delegates to `AndroidPlatform` |
| `monitoring/MonitoringWorkScheduler.kt` | `isScheduled()` for readiness |
| `MainActivity.kt` | Edge-to-edge via compatibility layer |
| `ScreenFgsStartupGate.kt` | API 36 cold-start delay (10s) |
| `AndroidManifest.xml` | `enableOnBackInvokedCallback` on MainActivity |
| `OemDiagnosticsCollector.kt` | android16 readiness + platform capabilities |
| `HeartbeatSyncManager.kt` | `android16ReadinessScore` in heartbeat |
| `infra/android/android16-*.yaml` | Risk + compatibility matrices |
| `scripts/validation/android16/run-android16-validation.sh` | Device validation suite |
| `backend/V17__device_android16_readiness.sql` | Fleet column + metrics |
| Tests | `Android16PlatformTest`, `Android16ManifestComplianceTest`, instrumented readiness |

---

## 5. SDK 36 Migration Plan

| Phase | Timeline | Actions |
|-------|----------|---------|
| **P0 — Build** | Week 1 | compileSdk/targetSdk 36; platform layer; unit tests ✅ |
| **P1 — Validate** | Week 2–3 | Emulator API 36; Pixel beta; run validation script |
| **P2 — OEM** | Week 3–4 | Samsung/Xiaomi/OnePlus API 36 betas via P1-10 certification |
| **P3 — Canary** | Week 5 | 5% fleet with `android16ReadinessScore ≥ 80` |
| **P4 — GA** | Week 8+ | Play targetSdk 36 (Google deadline ~Apr 2026) |

---

## 6. Validation Strategy

```bash
chmod +x scripts/validation/android16/run-android16-validation.sh
./scripts/validation/android16/run-android16-validation.sh
```

### Regression checklist

| # | Test | Pass |
|---|------|------|
| 1 | Screen share (silent DO) | Video within 60s on API 36 |
| 2 | Camera stream | FGS camera + hidden capture |
| 3 | Audio stream | FGS microphone + hidden capture |
| 4 | Remote control | Accessibility inject on API 36 |
| 5 | App blocking | Block overlay on launch |
| 6 | FGS burst | ≤90s, no crash, WorkManager follow-up |
| 7 | FGS onTimeout | Graceful stop, backoff |
| 8 | Process death | Recovery within 20 min |
| 9 | Reboot | No BOOT FGS exception; heartbeat |
| 10 | Network handoff | P1-7 ICE recovery during stream |

### Device matrix

| Device | Priority |
|--------|----------|
| Pixel (API 36 emulator/device) | P0 |
| Samsung One UI 8 / API 36 beta | P1 |
| Xiaomi HyperOS API 36 | P1 |
| OnePlus OxygenOS 16 | P2 |

---

## 7. Rollout Plan

1. Internal dogfood on API 36 emulator + 1 physical Pixel beta.
2. Gate canary on `android16ReadinessScore ≥ 70` from heartbeat.
3. Block production rollout for devices with `android16Blockers` containing `ACCESSIBILITY_DISABLED`.
4. Monitor `device.android16.readiness.score` and `device.android16.readiness.low.total`.
5. Full rollout when fleet median score ≥ 85 for 7 days.

---

## 8. Production Readiness Impact

| Initiative | Android 16 interaction |
|------------|------------------------|
| P1-7 WebRTC lifecycle | Typed FGS timeout + ICE recovery during stream |
| P1-6 STOMP ownership | WorkManager/boot recovery unchanged |
| P1-10 OEM certification | android16 score feeds certification pre-gate |
| P0 FGS timeout (Android 15) | Extended to API 36 with platform abstraction |
| MediaProjection gate | Required for API 36 VirtualDisplay enforcement |

---

## 9. Future Android Roadmap

| Release | Expected focus | Preparation |
|---------|----------------|-------------|
| **Android 16 (API 36)** | Edge-to-edge, FGS enforcement, projection hardening | This program |
| **Android 17 (API 37)** | Likely stricter background sensors / privacy | Capability detection pattern in `AndroidPlatform` |
| **Play policy** | targetSdk +36 within 12 months of release | Canary + fleet readiness scoring |

---

## 10. Final Readiness Score

| Dimension | Before | After (automated) | After (device farm) |
|-----------|--------|-------------------|---------------------|
| Build / SDK | 35 | **36** | 36 |
| Platform abstraction | 20 | **85** | 85 |
| FGS compliance | 70 | **85** | 90 |
| MediaProjection | 50 | **80** | 90 |
| Recovery paths | 65 | **80** | 85 |
| OEM matrix | 40 | **55** | 80 (with device tests) |
| Validation automation | 10 | **65** | 90 |
| **Overall** | **45** | **78** | **90+** |

**Blockers to reach 90+:** Manual validation on Pixel + Samsung + Xiaomi API 36; long-stream FGS timeout soak test; remote control on API 36 accessibility.

---

## Quick reference

```bash
# Compile
cd child/android && ./gradlew :app:compileDebugKotlin

# Unit tests
./gradlew :app:testDebugUnitTest --tests 'com.childmonitor.platform.Android16*'

# Android 15 FGS lab (still applies on 16)
adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS com.childmonitor

# Logcat
adb logcat -s Android16Compat MonitoringFgSvc PCP_LIFECYCLE CAPTURE_GATE
```
