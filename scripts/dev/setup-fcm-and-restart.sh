#!/usr/bin/env bash
# Install Firebase on child, rebuild, re-pair, restart child + parent.
#
# Usage:
#   ./scripts/dev/setup-fcm-and-restart.sh /path/to/google-services.json
#   FIREBASE_GOOGLE_SERVICES_JSON=/path/to/file ./scripts/dev/setup-fcm-and-restart.sh
#
# Optional env:
#   PHONE_SERIAL=8HNNIBTK4PY9NRIJ  EMU_SERIAL=emulator-5554
#   FCM_SERVER_KEY=...             (backend wake tests)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PHONE="${PHONE_SERIAL:-8HNNIBTK4PY9NRIJ}"
EMU="${EMU_SERIAL:-emulator-5554}"
ADB="${ADB:-$(command -v adb || true)}"
GS_JSON="${1:-${FIREBASE_GOOGLE_SERVICES_JSON:-}}"

log() { echo "==> $*"; }

if [[ -z "$GS_JSON" || ! -f "$GS_JSON" ]]; then
  echo "google-services.json required." >&2
  echo "  Firebase Console → Project settings → Your apps → Android (com.childmonitor) → Download" >&2
  echo "  ./scripts/dev/setup-fcm-and-restart.sh ~/Downloads/google-services.json" >&2
  exit 1
fi

log "Install google-services.json..."
./scripts/dev/install-google-services.sh "$GS_JSON"

if [[ -z "$ADB" || ! -x "$ADB" ]]; then
  echo "adb not found" >&2
  exit 1
fi

log "Wait for devices (phone=$PHONE, emu=$EMU)..."
"$ADB" start-server >/dev/null 2>&1 || true
for _ in $(seq 1 30); do
  if "$ADB" devices | grep -qE "${PHONE}.*device|${EMU}.*device"; then
    break
  fi
  sleep 2
done

if "$ADB" devices | grep -q "$PHONE"; then
  log "adb reverse on phone..."
  "$ADB" -s "$PHONE" reverse tcp:8080 tcp:8080 2>/dev/null || true
  "$ADB" -s "$PHONE" reverse tcp:8082 tcp:8082 2>/dev/null || true
fi
if "$ADB" devices | grep -q "$EMU"; then
  log "adb reverse on emulator..."
  "$ADB" -s "$EMU" reverse tcp:8080 tcp:8080 2>/dev/null || true
  "$ADB" -s "$EMU" reverse tcp:8081 tcp:8081 2>/dev/null || true
fi

log "Build & install child (FCM enabled) on $PHONE..."
if "$ADB" devices | grep -q "$PHONE"; then
  (
    cd "$ROOT/child-app"
    npx react-native run-android --port 8082 --deviceId "$PHONE" --no-packager
  )
else
  echo "WARN: phone $PHONE not connected — skipping child install" >&2
fi

log "Restart parent on $EMU..."
if "$ADB" devices | grep -q "$EMU"; then
  (
    cd "$ROOT/parent-app"
    npx react-native run-android --port 8081 --deviceId "$EMU" --no-packager
  ) || true
  "$ADB" -s "$EMU" shell am force-stop com.parentscaffold 2>/dev/null || true
  "$ADB" -s "$EMU" shell am start -n com.parentscaffold/.MainActivity
fi

if "$ADB" devices | grep -q "$PHONE"; then
  log "Auto-pair + restart child..."
  PHONE_SERIAL="$PHONE" ./scripts/dev/auto-pair.sh
  "$ADB" -s "$PHONE" shell am force-stop com.childmonitor 2>/dev/null || true
  "$ADB" -s "$PHONE" shell am start -n com.childmonitor/.MainActivity
  sleep 12
  log "FCM log check..."
  "$ADB" -s "$PHONE" logcat -d -s FcmTokenRegistrar,FcmWakeHandler | tail -15 || true
  log "DB token check..."
  ./scripts/dev/psql.sh -t -A -c \
    "SELECT CASE WHEN fcm_token IS NULL OR fcm_token = '' THEN 'missing' ELSE 'present' END FROM devices WHERE android_id = \"$("$ADB" -s "$PHONE" shell settings get secure android_id | tr -d '\r')\";" 2>/dev/null || true
fi

cat <<EOF

Done. If token still missing:
  1. Open child app on phone and wait 30s for heartbeat
  2. ./scripts/dev/psql.sh -c "SELECT LEFT(fcm_token,40) FROM devices WHERE android_id = '...';"
  3. Backend wakes: export FCM_ENABLED=true FCM_SERVER_KEY=... and restart backend
EOF
