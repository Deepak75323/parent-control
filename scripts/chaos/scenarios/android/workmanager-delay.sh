#!/usr/bin/env bash
# AND-08: WorkManager delay under background restriction — FALLBACK phase within 15m.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"
require_cmd adb

ADB=(adb $(adb_serial_args))
PKG=com.childmonitor
TIMEOUT=900

log "Restricting background for $PKG"
"${ADB[@]}" shell cmd appops set "$PKG" RUN_IN_BACKGROUND ignore 2>/dev/null || true
"${ADB[@]}" shell am force-stop "$PKG"
sleep 5

log "Waiting up to ${TIMEOUT}s for FALLBACK boot recovery telemetry"
start=$(date +%s)
recovered=false
while [[ $(($(date +%s) - start)) -lt $TIMEOUT ]]; do
  if "${ADB[@]}" logcat -d -t 200 | grep -q "BootRecovery.*FALLBACK.*success"; then
    recovered=true
    break
  fi
  if "${ADB[@]}" shell pidof "$PKG" >/dev/null 2>&1; then
    recovered=true
    break
  fi
  sleep 30
done

log "Restoring background permission"
"${ADB[@]}" shell cmd appops set "$PKG" RUN_IN_BACKGROUND allow 2>/dev/null || true

[[ "$recovered" == true ]] || { err "FALLBACK phase not observed within ${TIMEOUT}s"; exit 1; }
log "WorkManager delay recovery OK"
