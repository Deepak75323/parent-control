#!/usr/bin/env bash
# AND-03: Reboot recovery — requires DEVICE_ID + parent API token for online check.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"
require_cmd adb

if [[ -z "${DEVICE_ID:-}" ]] || [[ -z "${PARENT_TOKEN:-}" ]]; then
  echo "SKIP: DEVICE_ID and PARENT_TOKEN required for online verification"
  exit 0
fi

ADB=(adb $(adb_serial_args))
"${ADB[@]}" reboot
echo "Waiting for device..."
"${ADB[@]}" wait-for-device
sleep 120
API="${API_HOST:-http://localhost:8080}"
for i in $(seq 1 40); do
  STATUS=$(curl -sf -H "Authorization: Bearer $PARENT_TOKEN" \
    "$API/api/v1/devices/$DEVICE_ID" | jq -r '.data.status // empty')
  if [[ "$STATUS" == "ONLINE" ]]; then
    echo "Device ONLINE after reboot"
    exit 0
  fi
  sleep 15
done
echo "FAIL: device not ONLINE within 10 minutes"
exit 1
