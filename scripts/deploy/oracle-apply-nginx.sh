#!/usr/bin/env bash
# Install durable nginx config for Oracle single-VM staging.
# Run ON the VM from ~/parentalControlApp (or pass ROOT).
#
# Prevents recurrence of the 2026-07-14 outage where worker_connections=768
# was exhausted by /ws-native reconnect storms → public HTTP 500s while the
# backend itself was still healthy on :8080.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SITE_SRC="${ROOT}/scripts/deploy/nginx/pcp-site.conf"
WORKER_CONNECTIONS="${NGINX_WORKER_CONNECTIONS:-4096}"

if [[ ! -f "$SITE_SRC" ]]; then
  echo "Missing $SITE_SRC" >&2
  exit 1
fi

if [[ "$(id -u)" -ne 0 ]] && ! command -v sudo >/dev/null 2>&1; then
  echo "Need root or sudo to write /etc/nginx" >&2
  exit 1
fi

run() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  else
    sudo "$@"
  fi
}

echo "==> Raising worker_connections to ${WORKER_CONNECTIONS}"
run sed -i -E "s/worker_connections[[:space:]]+[0-9]+;/worker_connections ${WORKER_CONNECTIONS};/" /etc/nginx/nginx.conf
grep -n 'worker_connections' /etc/nginx/nginx.conf || true

echo "==> Installing site config"
run cp "$SITE_SRC" /etc/nginx/sites-available/pcp
run ln -sfn /etc/nginx/sites-available/pcp /etc/nginx/sites-enabled/pcp
# Drop Ubuntu default site if it conflicts with default_server.
if [[ -e /etc/nginx/sites-enabled/default ]]; then
  run rm -f /etc/nginx/sites-enabled/default
fi

echo "==> Validating and reloading nginx"
run nginx -t
# Restart (not reload) so workers drop any stuck connection pile.
run systemctl restart nginx

echo "==> Smoke check"
if curl -sf --max-time 5 http://127.0.0.1/actuator/health >/dev/null; then
  curl -sS --max-time 5 http://127.0.0.1/actuator/health
  echo
  echo "Nginx apply OK."
else
  echo "Health via nginx failed — last error log:" >&2
  run tail -20 /var/log/nginx/error.log >&2 || true
  exit 1
fi
