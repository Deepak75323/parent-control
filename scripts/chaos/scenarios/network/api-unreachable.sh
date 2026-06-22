#!/usr/bin/env bash
# NET-02: Pause backend container — simulates API outage.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"

log "Pausing backend"
podman pause pcp-backend 2>/dev/null || compose_cmd pause backend
sleep 10
log "Unpausing backend"
podman unpause pcp-backend 2>/dev/null || compose_cmd unpause backend
wait_http "${API_HOST:-http://localhost:8080}/actuator/health/readiness" 120
log "API unreachable recovery OK"
