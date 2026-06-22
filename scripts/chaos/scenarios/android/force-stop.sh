#!/usr/bin/env bash
# AND-02: Force stop — boot recovery chain expected.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"
require_cmd adb

ADB=(adb $(adb_serial_args))
PKG=com.childmonitor

"${ADB[@]}" shell am force-stop "$PKG"
sleep 90
"${ADB[@]}" logcat -d -s PCP:BOOT_RECOVERY BootRecoveryEngine | tail -20
echo "Force-stop scenario complete — check boot recovery logs"
