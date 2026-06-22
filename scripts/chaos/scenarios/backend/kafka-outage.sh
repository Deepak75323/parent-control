#!/usr/bin/env bash
# BE-05: Kafka outage — core API remains healthy.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"

if ! compose_cmd ps --format json 2>/dev/null | grep -q kafka; then
  log "Kafka not running — SKIP"
  exit 0
fi

log "Stopping Kafka"
compose_cmd stop kafka
sleep 3
wait_http "${API_HOST:-http://localhost:8080}/actuator/health" 30 || true
log "Starting Kafka"
compose_cmd start kafka
sleep 15
wait_http "${API_HOST:-http://localhost:8080}/actuator/health" 60
log "Kafka outage recovery OK"
