#!/usr/bin/env bash
# NET-05 / STR-01: TURN outage during streaming.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"

log "Stopping coturn"
compose_cmd stop coturn 2>/dev/null || podman stop pcp-coturn 2>/dev/null || true
sleep 5
log "Starting coturn"
compose_cmd start coturn 2>/dev/null || podman start pcp-coturn 2>/dev/null || true
sleep 5
log "TURN outage cycle OK"
