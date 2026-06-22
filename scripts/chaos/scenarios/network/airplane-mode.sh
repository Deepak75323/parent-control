#!/usr/bin/env bash
# NET-01: Airplane mode toggle — requires paired device + API credentials in env.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"
require_cmd adb

ADB=(adb $(adb_serial_args))

if [[ -z "${DEVICE_ID:-}" ]]; then
  echo "SKIP: DEVICE_ID not set"
  exit 0
fi

"${ADB[@]}" shell cmd connectivity airplane-mode enable
sleep 15
"${ADB[@]}" shell cmd connectivity airplane-mode disable
sleep 30
echo "Airplane mode toggle complete — verify heartbeat via parent API"
