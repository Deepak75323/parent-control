# Child App (React Native — Android)

Installed on the child's Android device for pairing, permissions, background monitoring, and policy enforcement.

## Structure

```
child/
├── android/
│   └── app/src/main/java/com/childmonitor/   # Kotlin services + tamper protection
├── src/
│   ├── screens/          # Setup, permissions, pairing, status
│   ├── services/         # API, heartbeat, storage
│   ├── nativeModules/    # JS bridge to Android
│   └── utils/
├── App.tsx
└── package.json
```

## Setup

```bash
cp .env.example .env
npm install
npm start
npm run android
```

## Native Android

Register `ChildMonitorNativePackage` in `MainApplication.kt` after generating the Android project.

Merge `android/app/src/main/AndroidManifest.xml` services and receivers into your generated RN manifest.

## Silent operation (parent access)

| Parent action | Child device behavior |
|---------------|----------------------|
| View dashboard / location / notifications | No alert (data already syncing in background) |
| Create or change policies | Silent policy sync (HTTP poll, no popup) |
| Start screen / camera / audio stream | Silent HTTP poll → WebRTC; **no child notification** |
| Low battery / geofence / violations | Alerts go to **parent app only** |

The foreground service uses `IMPORTANCE_MIN` silent notification (Android requirement). Screen share may still show the **system** MediaProjection consent dialog once per session — that is enforced by Android and cannot be hidden.

## Anti-tampering (Phase 3)

| Feature | API | Notes |
|---------|-----|--------|
| Device administrator | `DeviceAdminReceiver` | User must deactivate before uninstall |
| Hide launcher icon | `PackageManager.setComponentEnabledSetting` | Requires active device admin |
| Open app when hidden | Foreground notification tap | `TamperProtectionManager.getLaunchIntent` |
| Boot persistence | `BootReceiver` | Re-applies hide + restarts service |

### Permission setup flow

1. **Device administrator** — system activation screen (`ACTION_ADD_DEVICE_ADMIN`).
2. **Hide app from launcher** — toggle after admin is active.
3. After pairing, `applyTamperProtection()` runs from `startMonitoringSync`.

### Optional: Device Owner (development)

Device Owner enables `setUninstallBlocked` on the app package. It is **not** required for Device Admin or hide launcher.

```bash
# Factory-reset emulator or device with no accounts, then:
adb shell dpm set-device-owner com.childmonitor/.ChildDeviceAdminReceiver
```

Production Device Owner provisioning typically uses a QR code during device setup (Android Enterprise). Document consent before enabling.

## Legal

Lawful parental monitoring with informed consent only. Device administrator and hiding the launcher must be disclosed to the child and parent during setup.
