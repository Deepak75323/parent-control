#!/usr/bin/env bash
# API-only pairing for dev automation (no UI). Uses Podman-backed Postgres when needed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

API="${API_BASE:-http://127.0.0.1:8080}"
PHONE="${PHONE_SERIAL:-8HNNIBTK4PY9NRIJ}"
ADB="${ADB:-$(command -v adb || true)}"
EMAIL="${PARENT_EMAIL:-dev.parent@local.test}"
PASSWORD="${PARENT_PASSWORD:-DevPass123!}"
FIRST="${PARENT_FIRST_NAME:-Dev}"
LAST="${PARENT_LAST_NAME:-Parent}"

json_get() {
  local path="$1"
  python3 -c '
import json, sys
path = sys.argv[1].split(".")
cur = json.load(sys.stdin)
for p in path:
    cur = cur[p]
print(cur)
' "$path"
}

api_post() {
  local path="$1"
  local body="$2"
  local auth="${3:-}"
  if [[ -n "$auth" ]]; then
    curl -sf -m 30 -X POST "$API$path" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $auth" \
      -d "$body"
  else
    curl -sf -m 30 -X POST "$API$path" \
      -H "Content-Type: application/json" \
      -d "$body"
  fi
}

echo "==> Ensure parent account ($EMAIL)..."
if ! LOGIN_JSON=$(api_post "/api/v1/auth/login" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" 2>/dev/null); then
  echo "    Registering new parent..."
  api_post "/api/v1/auth/register" "$(cat <<EOF
{
  "email": "$EMAIL",
  "password": "$PASSWORD",
  "firstName": "$FIRST",
  "lastName": "$LAST",
  "consentAccepted": "true"
}
EOF
)" >/dev/null
  LOGIN_JSON=$(api_post "/api/v1/auth/login" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
fi

ACCESS_TOKEN=$(printf '%s' "$LOGIN_JSON" | json_get "data.accessToken")
echo "    Parent logged in"

echo "==> Create pairing code..."
CODE_JSON=$(api_post "/api/v1/devices/pairing-code" "{}" "$ACCESS_TOKEN")
PAIR_CODE=$(printf '%s' "$CODE_JSON" | json_get "data.code")
echo "    Code: $PAIR_CODE"

if [[ -z "$ADB" || ! -x "$ADB" ]]; then
  echo "adb not found — skipping device pair" >&2
  exit 1
fi

ANDROID_ID=$("$ADB" -s "$PHONE" shell settings get secure android_id | tr -d '\r')
if [[ -z "$ANDROID_ID" || "$ANDROID_ID" == "null" ]]; then
  echo "Could not read android_id from $PHONE" >&2
  exit 1
fi
echo "    Child androidId: $ANDROID_ID"

echo "==> Pair child via API..."
PAIR_JSON=$(api_post "/api/v1/device/pair" "$(cat <<EOF
{
  "pairingCode": "$PAIR_CODE",
  "deviceName": "Dev Child Phone",
  "deviceModel": "Physical",
  "androidId": "$ANDROID_ID",
  "consentAccepted": "true",
  "osVersion": "14",
  "appVersion": "1.0.0"
}
EOF
)")

DEVICE_TOKEN=$(printf '%s' "$PAIR_JSON" | json_get "data.deviceToken")
DEVICE_HMAC=$(printf '%s' "$PAIR_JSON" | json_get "data.deviceHmacKey")
PARENT_NAME=$(printf '%s' "$PAIR_JSON" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('data',{}).get('parentDisplayName') or 'Parent')" 2>/dev/null || echo "Parent")
DEVICE_ID=$(printf '%s' "$PAIR_JSON" | json_get "data.deviceId")
echo "    Paired deviceId: $DEVICE_ID"

CHILD_API="${CHILD_API_BASE:-http://127.0.0.1:8080}"
echo "==> Push credentials to child via adb broadcast..."
"$ADB" -s "$PHONE" shell am broadcast \
  -a com.childmonitor.DEBUG_APPLY_PAIR \
  --es apiBaseUrl "$CHILD_API" \
  --es deviceToken "$DEVICE_TOKEN" \
  --es deviceHmacKey "$DEVICE_HMAC" \
  --es parentDisplayName "$PARENT_NAME" \
  >/dev/null

sleep 2

echo "==> Verify device on parent account..."
LIST_JSON=$(curl -sf -m 20 "$API/api/v1/devices" -H "Authorization: Bearer $ACCESS_TOKEN")
DEVICE_COUNT=$(printf '%s' "$LIST_JSON" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('data',[])))")
echo "    Devices listed: $DEVICE_COUNT"

echo ""
echo "AUTO_PAIR_OK code=$PAIR_CODE deviceId=$DEVICE_ID devices=$DEVICE_COUNT parentToken=${ACCESS_TOKEN:0:20}..."
