#!/usr/bin/env bash
# Live screen-share debug: pair, trigger stream via API, capture logs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PHONE="${PHONE_SERIAL:-8HNNIBTK4PY9NRIJ}"
EMU="${EMU_SERIAL:-emulator-5554}"
API="${API_BASE:-http://127.0.0.1:8080}"
EMAIL="${PARENT_EMAIL:-dev.parent@local.test}"
PASSWORD="${PARENT_PASSWORD:-DevPass123!}"
LOG_DIR="${LOG_DIR:-/tmp/pcp-screen-debug}"
mkdir -p "$LOG_DIR"
TS="$(date +%Y%m%d-%H%M%S)"
CHILD_LOG="$LOG_DIR/child-$TS.log"
PARENT_LOG="$LOG_DIR/parent-$TS.log"

log() { echo "==> $*"; }

log "adb reverse..."
adb -s "$PHONE" reverse tcp:8080 tcp:8080 2>/dev/null || true
adb -s "$PHONE" reverse tcp:8082 tcp:8082 2>/dev/null || true
adb -s "$EMU" reverse tcp:8080 tcp:8080 2>/dev/null || true
adb -s "$EMU" reverse tcp:8081 tcp:8081 2>/dev/null || true

log "Auto-pair child..."
"$ROOT/scripts/dev/auto-pair.sh" 2>&1 | tee "$LOG_DIR/auto-pair-$TS.log"

log "Clear logcat buffers..."
adb -s "$PHONE" logcat -c
adb -s "$EMU" logcat -c

log "Start log capture (background)..."
adb -s "$PHONE" logcat -v time \
  ReactNativeJS:V PCP_FLOW:V NativeStreamingCoord:V HiddenScreenBridge:V \
  ScreenCaptureController:V MediaProjSessionKeeper:V DeviceHttpClient:V \
  *:S > "$CHILD_LOG" &
CHILD_PID=$!
adb -s "$EMU" logcat -v time ReactNativeJS:V *:S > "$PARENT_LOG" &
PARENT_PID=$!

cleanup() {
  kill "$CHILD_PID" "$PARENT_PID" 2>/dev/null || true
}
trap cleanup EXIT

log "Restart child + parent apps..."
adb -s "$PHONE" shell am force-stop com.childmonitor
adb -s "$EMU" shell am force-stop com.parentscaffold
sleep 2
adb -s "$PHONE" shell am start -n com.childmonitor/.MainActivity
adb -s "$EMU" shell am start -n com.parentscaffold/.MainActivity
sleep 5

log "Re-apply pairing credentials after child restart (optional — skip if child already on Xiaomi device)..."
if [[ "${SKIP_REPAIR:-}" != "1" ]]; then
  "$ROOT/scripts/dev/auto-pair.sh" 2>&1 | tee "$LOG_DIR/auto-pair-post-restart-$TS.log" | tail -5
  sleep 5
else
  log "SKIP_REPAIR=1 — keeping existing child credentials (avoids duplicate device confusion)"
fi

log "Parent login + list devices..."
LOGIN=$(curl -sf -m 20 -X POST "$API/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(printf '%s' "$LOGIN" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")

DEVICES=$(curl -sf -m 20 "$API/api/v1/devices" -H "Authorization: Bearer $TOKEN")
echo "$DEVICES" | python3 -c "
import json, sys
for d in json.load(sys.stdin).get('data', []):
    print(f\"  deviceId={d['id']} name={d.get('deviceName')} lastSeen={d.get('lastSeenAt')}\")
"

PHONE_MODEL=$("$ADB" -s "$PHONE" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n' || true)

DEVICE_ID=$(printf '%s' "$DEVICES" | python3 -c "
import json, sys, os
devices = json.load(sys.stdin).get('data', [])
if not devices:
    sys.exit(1)
phone_model = os.environ.get('PHONE_MODEL', '').strip()

def score(d):
    name = (d.get('deviceName') or '')
    s = 0
    if d.get('mediaProjectionGranted') and d.get('mediaProjectionConsentState') == 'GRANTED_ACTIVE':
        s += 1000
    if phone_model and phone_model in name:
        s += 500
    if 'xiaomi' in name.lower() or 'dev child' not in name.lower():
        s += 50
    s += hash(d.get('lastSeenAt') or '')  # tie-breaker via lastSeen string sort below
    return s

def last_seen(d):
    return d.get('lastSeenAt') or ''

# Prefer GRANTED_ACTIVE + phone model match; else freshest Xiaomi-like device
active = [d for d in devices if d.get('mediaProjectionConsentState') == 'GRANTED_ACTIVE' and d.get('mediaProjectionGranted')]
if active:
    best = max(active, key=last_seen)
else:
    model_matches = [d for d in devices if phone_model and phone_model in (d.get('deviceName') or '')]
    if model_matches:
        best = max(model_matches, key=last_seen)
    else:
        non_dev = [d for d in devices if 'dev child phone' not in (d.get('deviceName') or '').lower()]
        pool = non_dev if non_dev else devices
        best = max(pool, key=last_seen)
print(best['id'])
" PHONE_MODEL="$PHONE_MODEL")

log "Trigger SCREEN stream on device $DEVICE_ID..."
STREAM=$(curl -sf -m 30 -X POST "$API/api/v1/monitoring/start-stream" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"streamType\":\"SCREEN\"}")
echo "$STREAM" | python3 -m json.tool

ROOM=$(printf '%s' "$STREAM" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['signalingRoomId'])")
SESSION=$(printf '%s' "$STREAM" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['sessionId'])")

log "Waiting 45s for child to publish offer..."
sleep 45

log "Signaling messages for room $ROOM..."
curl -sf -m 20 "$API/api/v1/monitoring/signaling/$ROOM" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import json, sys
data = json.load(sys.stdin).get('data', [])
print(f'  total messages: {len(data)}')
for m in data:
    print(f\"  {m.get('messageType')} from {m.get('senderRole')} id={str(m.get('id',''))[:8]}\")
"

log "Child log highlights (PCP / SCREEN / ABORT)..."
grep -E 'PCP:|SCREEN|ABORT|stream-request|startStream|offer|ERROR|NativeStreaming|HiddenScreen' "$CHILD_LOG" | tail -80 || echo "  (no matches yet)"

log "Logs saved:"
echo "  $CHILD_LOG"
echo "  $PARENT_LOG"
echo ""
echo "Session: room=$ROOM session=$SESSION device=$DEVICE_ID"
