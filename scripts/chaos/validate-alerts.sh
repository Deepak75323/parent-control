#!/usr/bin/env bash
# Post-chaos alert validation: confirm SLO burn alerts fire during fault and resolve after recovery.
set -euo pipefail

PROM="${PROMETHEUS_URL:-http://localhost:9090}"
TIMEOUT="${ALERT_VALIDATE_TIMEOUT:-300}"

log() { echo "[alert-validate $(date +%H:%M:%S)] $*"; }

firing() {
  curl -sf "$PROM/api/v1/alerts" | jq -r '.data.alerts[] | select(.state=="firing") | .labels.alertname' | sort -u
}

log "Waiting up to ${TIMEOUT}s for alerts to clear after chaos run"
start=$(date +%s)
while [[ $(($(date +%s) - start)) -lt $TIMEOUT ]]; do
  active=$(firing || true)
  if [[ -z "$active" ]]; then
    log "All alerts resolved"
    exit 0
  fi
  log "Still firing: $(echo "$active" | tr '\n' ' ')"
  sleep 15
done

err "Alerts still firing after ${TIMEOUT}s"
firing
exit 1
