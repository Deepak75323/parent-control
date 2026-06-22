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
sleep 8

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

DEVICE_ID=$(printf '%s' "$DEVICES" | python3 -c "
import json, sys
devices = json.load(sys.stdin).get('data', [])
# Prefer Dev Child Phone, else freshest lastSeen
def key(d):
    name = (d.get('deviceName') or '').lower()
    bonus = 1 if 'dev child' in name else 0
    seen = d.get('lastSeenAt') or ''
    return (bonus, seen)
if not devices:
    sys.exit(1)
best = max(devices, key=key)
print(best['id'])
")

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
