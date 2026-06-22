#!/usr/bin/env bash
# Burst validation: 100 stream-config requests during TURN outage (proxy for stream startup resilience).
# Full WebRTC negotiation requires paired parent/child tokens — set PARENT_TOKEN + DEVICE_ID for live ICE.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/chaos/lib/common.sh"

API="${API_HOST:-http://localhost:8080}"
COUNT=100
FAIL=0
TOKEN="${PARENT_TOKEN:-}"

log "Stopping coturn for stream burst"
compose_cmd stop coturn 2>/dev/null || compose_cmd stop pcp-coturn 2>/dev/null || true
sleep 3

for i in $(seq 1 "$COUNT"); do
  if [[ -n "$TOKEN" ]]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' \
      -H "Authorization: Bearer $TOKEN" \
      "$API/api/v1/monitoring/webrtc-config" || echo "000")
    [[ "$code" == "200" || "$code" == "503" ]] || FAIL=$((FAIL + 1))
  else
    curl -sf "$API/actuator/health" >/dev/null 2>&1 || FAIL=$((FAIL + 1))
  fi
done

log "Restoring coturn"
compose_cmd start coturn 2>/dev/null || compose_cmd start pcp-coturn 2>/dev/null || true
wait_http "$API/actuator/health" 60

SUCCESS=$((COUNT - FAIL))
PCT=$((SUCCESS * 100 / COUNT))
log "Stream burst: $SUCCESS/$COUNT (${PCT}%)"

[[ $PCT -ge 90 ]] || { err "Stream burst pass criteria not met (need >=90%)"; exit 1; }
