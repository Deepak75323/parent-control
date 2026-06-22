#!/usr/bin/env bash
log() { echo "[chaos $(date +%H:%M:%S)] $*"; }
err() { echo "[chaos ERROR] $*" >&2; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { err "Missing command: $1"; exit 1; }
}

adb_serial_args() {
  if [[ -n "${DEVICE_SERIAL:-}" ]]; then
    echo "-s" "$DEVICE_SERIAL"
  fi
}

wait_http() {
  local url="$1"
  local timeout="${2:-60}"
  local i=0
  while [[ $i -lt $timeout ]]; do
    if curl -sf "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    i=$((i + 2))
  done
  return 1
}

compose_cmd() {
  local script="${COMPOSE:-./scripts/dev/compose.sh}"
  "$script" "$@"
}
