#!/usr/bin/env bash
# BE-03: Redis outage — API should recover when Redis returns.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"

require_cmd curl
require_cmd jq

log "Stopping Redis"
compose_cmd stop redis

sleep 5
# HMAC nonce store may fail; health may still pass if DB up
if curl -sf "${API_HOST:-http://localhost:8080}/actuator/health" >/dev/null; then
  log "API health reachable during Redis outage (expected)"
else
  log "WARN: API health failed during Redis outage"
fi

log "Restoring Redis"
compose_cmd start redis
wait_http "${API_HOST:-http://localhost:8080}/actuator/health/readiness" 90

log "Redis outage recovery OK"
