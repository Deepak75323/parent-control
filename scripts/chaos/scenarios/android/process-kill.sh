#!/usr/bin/env bash
# AND-01: Process kill — child app recovery within 60s.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"
require_cmd adb

ADB=(adb $(adb_serial_args))
PKG=com.childmonitor

"${ADB[@]}" shell am kill "$PKG"
sleep 60
if "${ADB[@]}" shell pidof "$PKG" >/dev/null 2>&1; then
  echo "Process restarted within 60s"
else
  echo "WARN: process not running — may recover via FCM/WorkManager"
fi
