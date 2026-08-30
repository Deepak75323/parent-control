#!/usr/bin/env bash
# Recreate pcp-backend on the Oracle VM with SPRING_PROFILES_ACTIVE=prod.
# Run ON the VM from ~/parentalControlApp (or pass ENV_FILE).
#
# Prerequisites in .env:
#   STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET (test keys are fine)
#   STRIPE_PRICE_STANDARD, STRIPE_PRICE_FAMILY
#   SMTP_* (Mailpit on this host is OK for staging)
#   Postgres JDBC URL with sslmode=require
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"
IMAGE="${BACKEND_IMAGE:-localhost/parentalcontrolapp_backend:latest}"
NETWORK="${PODMAN_NETWORK:-parentalcontrolapp_default}"
NAME=pcp-backend

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

require() {
  local k="$1"
  if [[ -z "${!k:-}" ]]; then
    echo "Refusing to start prod backend: $k is empty in $ENV_FILE" >&2
    exit 1
  fi
}

require SPRING_DATASOURCE_PASSWORD
require JWT_SECRET
require APP_ENCRYPTION_KEY
require TURN_SECRET
require STRIPE_SECRET_KEY
require STRIPE_WEBHOOK_SECRET
require SMTP_HOST

export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:?}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-pcp}"
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-redis}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6379}"
export HMAC_REQUIRED=true
export BILLING_ENFORCEMENT=true
export APP_ENCRYPTION_ENABLED=true
# Payload AES is opt-in — leave false so existing mobile clients keep working.
export API_PAYLOAD_ENCRYPTION_ENABLED="${API_PAYLOAD_ENCRYPTION_ENABLED:-false}"
if [[ "${API_PAYLOAD_ENCRYPTION_ENABLED}" == "true" ]]; then
  require API_PAYLOAD_ENCRYPTION_KEY
  require API_PAYLOAD_ENCRYPTION_IV
fi
export API_PAYLOAD_ENCRYPTION_KEY="${API_PAYLOAD_ENCRYPTION_KEY:-}"
export API_PAYLOAD_ENCRYPTION_IV="${API_PAYLOAD_ENCRYPTION_IV:-}"
export FCM_ENABLED="${FCM_ENABLED:-true}"
export FCM_SERVICE_ACCOUNT_FILE="${FCM_SERVICE_ACCOUNT_FILE:-/app/secrets/firebase-service-account.json}"
export PLAY_INTEGRITY_REQUIRED="${PLAY_INTEGRITY_REQUIRED:-false}"
export DEVICE_TRUST_ENFORCEMENT="${DEVICE_TRUST_ENFORCEMENT:-false}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:?}"
export WEBSOCKET_ALLOWED_ORIGINS="${WEBSOCKET_ALLOWED_ORIGINS:?}"
export TURN_EXTERNAL_HOST="${TURN_EXTERNAL_HOST:?}"
export KAFKA_ENABLED=false
export STOMP_RELAY_ENABLED=false
export STOMP_CLUSTER_MODE=redis
export OBSERVABILITY_PROMETHEUS_PUBLIC=false
export OBSERVABILITY_TRACING_ENABLED=false
export TAMPER_MASTER_PIN_ENABLED=false
export TAMPER_MASTER_PIN=
export ADMIN_ENDPOINTS_ENABLED=false

SA_HOST="${SA_HOST_PATH:-$ROOT/backend/secrets/firebase-service-account.json}"
if [[ "${FCM_ENABLED}" == "true" && ! -f "$SA_HOST" ]]; then
  echo "FCM_ENABLED=true but missing $SA_HOST" >&2
  exit 1
fi

echo "==> Stopping $NAME (if present)"
podman rm -f "$NAME" 2>/dev/null || true

echo "==> NOTE: For zero-downtime deploys on Oracle, prefer:"
echo "       ./scripts/deploy/oracle-deploy-backend.sh"
echo "     This script stops the backend before the replacement is healthy."

echo "==> Starting $NAME with profile=prod"
# shellcheck disable=SC2086
podman run -d --name "$NAME" --network "$NETWORK" \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE \
  -e SPRING_DATASOURCE_URL \
  -e SPRING_DATASOURCE_USERNAME \
  -e SPRING_DATASOURCE_PASSWORD \
  -e SPRING_DATA_REDIS_HOST \
  -e SPRING_DATA_REDIS_PORT \
  -e JWT_SECRET \
  -e APP_ENCRYPTION_KEY \
  -e APP_ENCRYPTION_ENABLED \
  -e API_PAYLOAD_ENCRYPTION_ENABLED \
  -e API_PAYLOAD_ENCRYPTION_KEY \
  -e API_PAYLOAD_ENCRYPTION_IV \
  -e HMAC_REQUIRED \
  -e BILLING_ENFORCEMENT \
  -e STRIPE_SECRET_KEY \
  -e STRIPE_WEBHOOK_SECRET \
  -e STRIPE_PRICE_STANDARD \
  -e STRIPE_PRICE_FAMILY \
  -e CHECKOUT_SUCCESS_URL \
  -e CHECKOUT_CANCEL_URL \
  -e CORS_ALLOWED_ORIGINS \
  -e WEBSOCKET_ALLOWED_ORIGINS \
  -e TURN_SECRET \
  -e TURN_EXTERNAL_HOST \
  -e SMTP_HOST \
  -e SMTP_PORT \
  -e SMTP_USERNAME \
  -e SMTP_PASSWORD \
  -e SMTP_FROM \
  -e PASSWORD_RESET_URL_BASE \
  -e EMAIL_VERIFICATION_URL_BASE \
  -e FCM_ENABLED \
  -e FCM_SERVICE_ACCOUNT_FILE \
  -e PLAY_INTEGRITY_REQUIRED \
  -e DEVICE_TRUST_ENFORCEMENT \
  -e KAFKA_ENABLED \
  -e STOMP_RELAY_ENABLED \
  -e STOMP_CLUSTER_MODE \
  -e OBSERVABILITY_PROMETHEUS_PUBLIC \
  -e OBSERVABILITY_TRACING_ENABLED \
  -e TAMPER_MASTER_PIN_ENABLED \
  -e TAMPER_MASTER_PIN \
  -e ADMIN_ENDPOINTS_ENABLED \
  ${FCM_ENABLED:+-v "$SA_HOST:/app/secrets/firebase-service-account.json:ro"} \
  "$IMAGE"

echo "==> Waiting for health..."
for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null; then
    curl -sS http://127.0.0.1:8080/actuator/health
    echo
    echo "Prod backend is up."
    exit 0
  fi
  if ! podman inspect "$NAME" --format '{{.State.Running}}' 2>/dev/null | grep -qx true; then
    echo "Container exited — last logs:" >&2
    podman logs --tail 80 "$NAME" >&2 || true
    exit 1
  fi
  sleep 2
done

echo "Timed out waiting for health — logs:" >&2
podman logs --tail 80 "$NAME" >&2 || true
exit 1
