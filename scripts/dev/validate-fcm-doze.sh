#!/usr/bin/env bash
# Validate FCM wake delivery while the child device is in Doze / idle mode.
#
# Prerequisites:
#   1. Child app built with google-services.json (./scripts/dev/install-google-services.sh)
#   2. Child device paired; fcm_token stored on backend
#   3. Backend: FCM_ENABLED=true, FCM_SERVER_KEY set
#   4. adb connected to child device
#   5. FCM_SERVER_KEY + DEVICE_FCM_TOKEN exported (or pass DEVICE_ID to fetch token)
#
# Usage:
#   export FCM_SERVER_KEY='...'
#   export DEVICE_FCM_TOKEN='...'   # or DEVICE_ID=<uuid> + API auth
#   ./scripts/dev/validate-fcm-doze.sh [adb-serial]
#
# On success: logcat shows ChildFcmMessaging / FcmWakeHandler within ~30s of force-idle + send.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ADB="${ADB:-$(command -v adb || true)}"
SERIAL="${1:-${PHONE_SERIAL:-}}"
API="${API_BASE:-http://127.0.0.1:8080}"
LOG_TAG='ChildFcmMessaging|FcmWakeHandler|FcmTokenRegistrar|MonitoringFgSvc'

if [[ -z "$ADB" || ! -x "$ADB" ]]; then
  echo "adb not found" >&2
  exit 1
fi

ADB_CMD=("$ADB")
if [[ -n "$SERIAL" ]]; then
  ADB_CMD+=(-s "$SERIAL")
fi

if ! "${ADB_CMD[@]}" get-state >/dev/null 2>&1; then
  echo "No device connected${SERIAL:+ ($SERIAL)}" >&2
  "${ADB_CMD[@]}" devices
  exit 1
fi

if [[ -z "${FCM_SERVER_KEY:-}" ]]; then
  echo "Set FCM_SERVER_KEY" >&2
  exit 1
fi

if [[ -z "${DEVICE_FCM_TOKEN:-}" && -n "${DEVICE_ID:-}" ]]; then
  echo "Fetch fcm_token for device $DEVICE_ID not automated — set DEVICE_FCM_TOKEN from DB:" >&2
  echo "  SELECT fcm_token FROM devices WHERE id = '$DEVICE_ID';" >&2
  exit 1
fi

if [[ -z "${DEVICE_FCM_TOKEN:-}" ]]; then
  echo "Set DEVICE_FCM_TOKEN (from devices.fcm_token)" >&2
  exit 1
fi

echo "==> Clearing logcat buffer..."
"${ADB_CMD[@]}" logcat -c

echo "==> Putting device into idle / Doze (best-effort)..."
"${ADB_CMD[@]}" shell dumpsys deviceidle enable 2>/dev/null || true
"${ADB_CMD[@]}" shell dumpsys battery unplug 2>/dev/null || true
"${ADB_CMD[@]}" shell dumpsys deviceidle force-idle 2>/dev/null || true

echo "==> Sending high-priority FCM STREAM_WAKE..."
export DEVICE_FCM_TOKEN
"$ROOT/scripts/dev/send-fcm-wake.sh" STREAM_WAKE

echo "==> Waiting up to 45s for FCM handler logs..."
DEADLINE=$((SECONDS + 45))
FOUND=0
while (( SECONDS < DEADLINE )); do
  if "${ADB_CMD[@]}" logcat -d -s "$LOG_TAG" 2>/dev/null | grep -qE 'FCM wake|onNewToken|FCM token'; then
    FOUND=1
    break
  fi
  sleep 2
done

echo "==> Restoring device idle state..."
"${ADB_CMD[@]}" shell dumpsys deviceidle unforce 2>/dev/null || true
"${ADB_CMD[@]}" shell dumpsys battery reset 2>/dev/null || true

echo "==> Relevant logcat:"
"${ADB_CMD[@]}" logcat -d | grep -E 'ChildFcmMessaging|FcmWakeHandler|FcmTokenRegistrar' | tail -30 || true

if [[ "$FOUND" -eq 1 ]]; then
  echo ""
  echo "PASS: FCM wake observed in logcat under forced idle."
  exit 0
fi

echo ""
echo "FAIL: No FCM wake logs within 45s." >&2
echo "Check: google-services.json installed, app reinstalled, child paired, token matches DB." >&2
exit 1
