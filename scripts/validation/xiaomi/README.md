# Xiaomi Validation Automation Framework

Automates the Xiaomi 16-test validation matrix for **Redmi / POCO / Xiaomi** on **Android 13–16** (MIUI / HyperOS).

## Quick start

```bash
# 1. Start stack + pair child (debug build on Xiaomi device)
./scripts/dev/dev-up.sh
export ADB_SERIAL=<xiaomi-serial>
export PARENT_EMAIL=parent@example.com
export PARENT_PASSWORD=password

# 2. Configure MIUI overrides after manual settings
cp scripts/validation/xiaomi/xiaomi-overrides.example.json \
   scripts/validation/xiaomi/xiaomi-overrides.json

# 3. Run full validation (use XIAOMI_FAST=1 for ~15 min smoke)
./scripts/validation/xiaomi/run-xiaomi-validation.sh

# Fast smoke (2 min background, skip reboot)
XIAOMI_FAST=1 XIAOMI_ALLOW_MANUAL_PRECHECK=1 \
  ./scripts/validation/xiaomi/run-xiaomi-validation.sh
```

## Required Xiaomi settings (manual once per device)

| Setting | Path |
|--------|------|
| Autostart ON | Security → Autostart → Child Monitor |
| Battery unrestricted | Settings → Apps → Child Monitor → Battery → No restrictions |
| Recents lock | Recent apps → drag down lock icon |
| Notifications | Allow POST_NOTIFICATIONS |
| Accessibility | Enabled for Child Monitor |
| Device Owner | `adb shell dpm set-device-owner com.childmonitor/.deviceowner.ChildDeviceAdminReceiver` |

Record autostart/recents in `xiaomi-overrides.json` (no public MIUI API).

## Phases

| Phase | Output |
|-------|--------|
| 1 Pre-check | `xiaomi-precheck-report.json` — fail fast |
| 2 Discovery | `xiaomi-context.json` — IDs, tokens from API/ADB/DB |
| 3 Automated | XIA-01,02,03,10,11,13,14,15,16 |
| 4 Xiaomi-specific | Battery, recents, screen-off |
| 5 Process kill | `am force-stop` recovery |
| 6 Doze | `force-idle` + TAP/streams |
| 7 Logs | `logs/xiaomi/<run-id>/` |
| 8 Pass/fail engine | Thresholds in `lib/pass_fail.py` |
| 9 Manual assisted | XIA-04–09,12 guides in HTML report |
| 10 Report | `reports/xiaomi-validation.html` |

## Debug broadcast

```bash
adb shell am broadcast -a com.childmonitor.VALIDATION_DUMP
adb logcat -d | grep XIAOMI_VALIDATION
```

## Coverage

- **Automated (~80%)**: pairing, heartbeat, FCM wake, background, screen off, process kill, reboot, network loss/restore, doze, OEM survival heuristics
- **Manual (~20%)**: screen/audio/camera quality, remote-control accuracy, overnight screen-off, launcher/kiosk UX

## Production readiness

Passing this matrix on a physical Xiaomi device is required for **CONDITIONAL GO** (with Samsung). Estimated full run: **45–90 minutes**; fast smoke: **~15 minutes**.
