#!/usr/bin/env bash
# Start Podman infra, Metro, build/install apps, auto-pair — child on phone, parent on emulator.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PHONE="${PHONE_SERIAL:-8HNNIBTK4PY9NRIJ}"
EMU="${EMU_SERIAL:-emulator-5554}"
ADB="${ADB:-$(command -v adb || true)}"
API="${API_BASE:-http://127.0.0.1:8080}"

log() { echo "==> $*"; }

if ! command -v podman >/dev/null 2>&1; then
  echo "podman not found. Install: brew install podman && podman machine start" >&2
  exit 1
fi

log "Podman infra (postgres, redis)..."
./scripts/dev/compose.sh up -d postgres redis 2>&1 | tail -5
log "TURN (native on host)..."
podman stop pcp-coturn 2>/dev/null || true
if ! pgrep -f "turnserver.*realm=pcp" >/dev/null 2>&1; then
  nohup ./scripts/dev/start-coturn.sh >/tmp/pcp-coturn.log 2>&1 &
  sleep 1
fi

log "Waiting for Postgres..."
for i in {1..30}; do
  if podman exec pcp-postgres pg_isready -U pcp -d parental_control >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

log "Backend health at $API ..."
if ! curl -sf -m 5 "$API/actuator/health" >/dev/null; then
  log "Backend not on :8080 — starting via Podman (kafka + backend)..."
  ./scripts/dev/compose.sh up -d kafka backend 2>&1 | tail -8
  for i in {1..60}; do
    if curl -sf -m 3 "$API/actuator/health" >/dev/null; then
      break
    fi
    sleep 2
  done
fi
curl -sf -m 5 "$API/actuator/health" >/dev/null || {
  echo "Backend still down. Run manually:" >&2
  echo "  cd backend && BILLING_ENFORCEMENT=false mvn spring-boot:run -DskipTests" >&2
  exit 1
}
log "Backend OK"

if [[ -n "$ADB" && -x "$ADB" ]]; then
  log "adb reverse (phone + emulator)..."
  "$ADB" -s "$PHONE" reverse tcp:8080 tcp:8080 2>/dev/null || true
  "$ADB" -s "$PHONE" reverse tcp:8082 tcp:8082 2>/dev/null || true
  "$ADB" -s "$PHONE" reverse tcp:3478 tcp:3478 2>/dev/null || true
  "$ADB" -s "$EMU" reverse tcp:8080 tcp:8080 2>/dev/null || true
  "$ADB" -s "$EMU" reverse tcp:8081 tcp:8081 2>/dev/null || true
  "$ADB" -s "$EMU" reverse tcp:3478 tcp:3478 2>/dev/null || true
fi

log "Stop old Metro processes..."
pkill -f "react-native start" 2>/dev/null || true
sleep 1

log "Start Metro — parent :8081, child :8082..."
(
  cd "$ROOT/parent-app"
  npm start -- --port 8081
) > /tmp/pcp-parent-metro.log 2>&1 &
PARENT_METRO_PID=$!

(
  cd "$ROOT/child-app"
  npm start -- --port 8082
) > /tmp/pcp-child-metro.log 2>&1 &
CHILD_METRO_PID=$!

sleep 5

log "Build & install child on phone ($PHONE)..."
(
  cd "$ROOT/child-app"
  npx react-native run-android --port 8082 --deviceId "$PHONE" --no-packager
)

log "Build & install parent on emulator ($EMU)..."
(
  cd "$ROOT/parent-app"
  npx react-native run-android --port 8081 --deviceId "$EMU" --no-packager
)

log "Launch parent app (auto-login via .env)..."
"$ADB" -s "$EMU" shell am start -n com.parentscaffold/.MainActivity >/dev/null 2>&1 || true

log "Auto-pair via API + adb broadcast..."
chmod +x "$ROOT/scripts/dev/auto-pair.sh"
"$ROOT/scripts/dev/auto-pair.sh"

log "Launch child app..."
"$ADB" -s "$PHONE" shell am start -n com.childmonitor/.MainActivity >/dev/null 2>&1 || true

log "Navigate parent to DeviceList (generate code screen)..."
sleep 8
"$ADB" -s "$EMU" shell input keyevent 4 >/dev/null 2>&1 || true

cat <<EOF

========================================
Dual-device dev run complete (Podman)
========================================
Podman:   podman ps  (postgres, redis, coturn)
Backend:  $API
Parent:   emulator $EMU — Metro :8081 — auto-login dev.parent@local.test
Child:    phone $PHONE — Metro :8082 — paired via DEBUG_APPLY_PAIR

Logs:
  tail -f /tmp/pcp-parent-metro.log
  tail -f /tmp/pcp-child-metro.log
  adb -s $PHONE logcat -s DebugPairingReceiver NativeStreamingCoord

Metro PIDs: parent=$PARENT_METRO_PID child=$CHILD_METRO_PID
Stop Metro: kill $PARENT_METRO_PID $CHILD_METRO_PID
========================================
EOF
