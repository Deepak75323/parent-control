#!/usr/bin/env bash
# Start local dev stack via Podman: infra, adb reverse, fresh pairing code for dev parent.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

ADB="${ADB:-$(command -v adb || true)}"
PHONE="${PHONE_SERIAL:-8HNNIBTK4PY9NRIJ}"
EMU="${EMU_SERIAL:-emulator-5554}"
API="${API_BASE:-http://127.0.0.1:8080}"
EMAIL="${PARENT_EMAIL:-dev.parent@local.test}"
PASSWORD="${PARENT_PASSWORD:-DevPass123!}"
LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "")"

echo "==> Podman infra (postgres, redis)..."
./scripts/dev/compose.sh up -d postgres redis 2>/dev/null || true
echo "==> TURN (native on host — Podman coturn relays wrong IP on macOS)..."
podman stop pcp-coturn 2>/dev/null || true
if ! pgrep -f "turnserver.*realm=pcp" >/dev/null 2>&1; then
  nohup ./scripts/dev/start-coturn.sh >/tmp/pcp-coturn.log 2>&1 &
  sleep 1
fi

echo "==> Backend health..."
if ! curl -sf -m 3 "$API/actuator/health" >/dev/null; then
  echo "Backend not running on :8080. Start it:"
  echo "  cd backend && BILLING_ENFORCEMENT=false mvn spring-boot:run -DskipTests"
  exit 1
fi
echo "    API OK"

if [[ -n "$ADB" && -x "$ADB" ]]; then
  echo "==> adb reverse (phone + emulator)..."
  if "$ADB" devices | grep -q "$PHONE"; then
    "$ADB" -s "$PHONE" reverse tcp:8080 tcp:8080
    "$ADB" -s "$PHONE" reverse tcp:8082 tcp:8082
    "$ADB" -s "$PHONE" reverse tcp:3478 tcp:3478
    echo "    Phone ($PHONE): ports 8080 + 8082 + 3478 (TURN) reversed"
  else
    echo "    Phone $PHONE not connected — plug in USB and enable USB debugging"
  fi
  if "$ADB" devices | grep -q "$EMU"; then
    "$ADB" -s "$EMU" reverse tcp:8081 tcp:8081
    "$ADB" -s "$EMU" reverse tcp:8080 tcp:8080
    "$ADB" -s "$EMU" reverse tcp:3478 tcp:3478
    echo "    Emulator ($EMU): ports 8080 + 8081 + 3478 (TURN) reversed"
  fi
else
  echo "adb not found — child phone cannot reach http://127.0.0.1:8080 without it"
fi

echo "==> Fresh pairing code for $EMAIL..."
LOGIN_JSON=$(curl -sf -m 20 -X POST "$API/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" 2>/dev/null || true)

if [[ -z "$LOGIN_JSON" ]]; then
  echo "    Registering dev parent..."
  curl -sf -m 20 -X POST "$API/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"firstName\":\"Dev\",\"lastName\":\"Parent\",\"consentAccepted\":\"true\"}" >/dev/null
  LOGIN_JSON=$(curl -sf -m 20 -X POST "$API/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
fi

ACCESS_TOKEN=$(printf '%s' "$LOGIN_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")
CODE_JSON=$(curl -sf -m 20 -X POST "$API/api/v1/devices/pairing-code" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{}')
CODE=$(printf '%s' "$CODE_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['code'])")

cat <<EOF

========================================
Dev stack ready
========================================
Backend:      $API
Parent login: $EMAIL  (same as parent-app/.env DEV_AUTO_EMAIL)
Pairing code: ${CODE}  (expires in 15 min, one use)

Child phone (USB — recommended):
  1. Keep phone plugged into this Mac via USB
  2. Open child app → setup → Pair device
  3. Server line should show: ✓ Connected  http://127.0.0.1:8080
  4. Enter code: ${CODE}

Parent emulator:
  1. Open parent app → Devices → Generate pairing code (or use code above)
  2. Must be logged in as $EMAIL

Wi‑Fi only (no USB): set DEV_LAN_HOST in child-app/.env to your Mac IP
  Mac LAN IP: ${LAN_IP:-not detected — use USB or check Wi‑Fi}
========================================
EOF
