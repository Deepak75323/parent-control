#!/usr/bin/env bash
# Send a high-priority FCM data wake to a paired child device (legacy HTTP API).
#
# Requires:
#   FCM_SERVER_KEY — Firebase Console → Project settings → Cloud Messaging → Server key
#   DEVICE_FCM_TOKEN — child's fcm_token (from DB or heartbeat)
#
# Usage:
#   export FCM_SERVER_KEY='...'
#   export DEVICE_FCM_TOKEN='...'
#   ./scripts/dev/send-fcm-wake.sh DEVICE_COMMAND <commandId> <commandType>
#   ./scripts/dev/send-fcm-wake.sh STREAM_WAKE
set -euo pipefail

ACTION="${1:-STREAM_WAKE}"
COMMAND_ID="${2:-}"
COMMAND_TYPE="${3:-}"

if [[ -z "${FCM_SERVER_KEY:-}" ]]; then
  echo "Set FCM_SERVER_KEY (Firebase legacy server key)" >&2
  exit 1
fi
if [[ -z "${DEVICE_FCM_TOKEN:-}" ]]; then
  echo "Set DEVICE_FCM_TOKEN (child device fcm_token)" >&2
  exit 1
fi

DATA="\"action\":\"$ACTION\""
if [[ "$ACTION" == "DEVICE_COMMAND" ]]; then
  if [[ -z "$COMMAND_ID" || -z "$COMMAND_TYPE" ]]; then
    echo "Usage: $0 DEVICE_COMMAND <commandId> <commandType>" >&2
    exit 1
  fi
  DATA="$DATA,\"commandId\":\"$COMMAND_ID\",\"commandType\":\"$COMMAND_TYPE\""
fi

BODY=$(cat <<EOF
{
  "to": "$DEVICE_FCM_TOKEN",
  "priority": "high",
  "data": { $DATA }
}
EOF
)

echo "Sending FCM action=$ACTION ..."
HTTP_CODE=$(curl -sS -o /tmp/pcp-fcm-response.json -w "%{http_code}" \
  -X POST "https://fcm.googleapis.com/fcm/send" \
  -H "Authorization: key=$FCM_SERVER_KEY" \
  -H "Content-Type: application/json" \
  -d "$BODY")

cat /tmp/pcp-fcm-response.json
echo ""
if [[ "$HTTP_CODE" != "200" ]]; then
  echo "FCM HTTP $HTTP_CODE" >&2
  exit 1
fi
echo "FCM sent (HTTP $HTTP_CODE)"
