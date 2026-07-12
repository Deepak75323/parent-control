# WebRTC native compatibility (P2-03)

Child Android ships **two** WebRTC stacks by design:

| Layer | Artifact | Role |
|-------|----------|------|
| Native capture / publish | `io.getstream:stream-webrtc-android:1.1.3` | `org.webrtc.*` used by `NativeStreamPublisher`, capturers |
| React Native JS bridge | `react-native-webrtc` (^124.0.5) | JS `RTCPeerConnection` for camera/audio paths |

Gradle **excludes** `org.jitsi:webrtc` so only Stream’s native binaries are packaged. RN-WebRTC’s Java layer must therefore remain ABI-compatible with Stream’s `org.webrtc` JNI.

## Pin policy

1. Do **not** bump `stream-webrtc-android` or `react-native-webrtc` in the same PR without running the matrix below.
2. After any bump, update the “Verified” row.
3. Prefer aligning majors: Stream 1.1.x ↔ RN-WebRTC 124.x (Chromium M124-era).

## Verified matrix

| Date | stream-webrtc-android | react-native-webrtc | Notes |
|------|----------------------|---------------------|-------|
| 2026-07 | 1.1.3 | 124.0.5 | Jitsi AAR excluded; native screen + JS camera smoke |

## Smoke checks after a bump

```bash
cd child/android
./gradlew :app:dependencies --configuration debugRuntimeClasspath | rg 'webrtc|getstream'
# Expect: stream-webrtc-android present; org.jitsi:webrtc absent

# Device: start SCREEN stream (native) then CAMERA stream (RN) — no UnsatisfiedLinkError
```

## Long-term

Consolidate on one native library when RN-WebRTC can drive MediaProjection VirtualDisplay without Stream’s capturer helpers. Until then this dual-stack + exclude is intentional.
