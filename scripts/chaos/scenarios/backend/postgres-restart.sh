#!/usr/bin/env bash
# BE-04: PostgreSQL restart — commands must persist.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"

log "Restarting Postgres"
compose_cmd restart postgres
wait_http "${API_HOST:-http://localhost:8080}/actuator/health/readiness" 120
log "Postgres restart recovery OK"
