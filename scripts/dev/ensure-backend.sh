#!/usr/bin/env bash
# Start local backend (jar) if :8080 is not healthy. Safe to run repeatedly.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
API="${API_BASE:-http://127.0.0.1:8080}"
PID_FILE="/tmp/pcp-backend.pid"
LOG_FILE="/tmp/pcp-backend.log"

log() { echo "==> $*"; }

if curl -sf -m 3 "$API/actuator/health" >/dev/null 2>&1; then
  log "Backend already healthy at $API"
  exit 0
fi

log "Podman infra (postgres, redis)..."
"$ROOT/scripts/dev/compose.sh" up -d postgres redis 2>&1 | tail -3

if [[ -f "$PID_FILE" ]]; then
  old_pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -n "$old_pid" ]] && kill -0 "$old_pid" 2>/dev/null; then
    log "Stopping unresponsive backend pid=$old_pid"
    kill "$old_pid" 2>/dev/null || true
    sleep 2
  fi
fi

log "Starting backend jar (logs: $LOG_FILE)..."
nohup "$ROOT/scripts/dev/start-backend-local.sh" >>"$LOG_FILE" 2>&1 &
echo $! >"$PID_FILE"

for i in {1..45}; do
  if curl -sf -m 3 "$API/actuator/health" >/dev/null 2>&1; then
    log "Backend healthy after ${i} attempt(s)"
    curl -sf "$API/actuator/health"
    echo
    exit 0
  fi
  sleep 2
done

log "Backend failed to become healthy. Last log lines:"
tail -20 "$LOG_FILE" >&2 || true
exit 1
