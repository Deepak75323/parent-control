#!/usr/bin/env bash
# BE-07: 100 command-equivalent health checks during Redis outage (burst validation proxy).
# Full command burst requires paired device tokens — this validates API resilience under fault.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"

API="${API_HOST:-http://localhost:8080}"
COUNT=100
FAIL=0

log "Stopping Redis for burst test"
compose_cmd stop redis
sleep 3

for i in $(seq 1 "$COUNT"); do
  if ! curl -sf "$API/actuator/health" >/dev/null 2>&1; then
    FAIL=$((FAIL + 1))
  fi
done

log "Restoring Redis"
compose_cmd start redis
wait_http "$API/actuator/health/readiness" 90

SUCCESS=$((COUNT - FAIL))
PCT=$((SUCCESS * 100 / COUNT))
log "Burst result: $SUCCESS/$COUNT succeeded (${PCT}%)"

[[ $PCT -ge 90 ]] || { err "Burst pass criteria not met (need >=90%)"; exit 1; }
