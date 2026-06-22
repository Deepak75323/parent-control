#!/usr/bin/env bash
# Start Spring Boot backend with backend/.env loaded (FCM, DB, etc.).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="$ROOT/backend/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE — copy backend/.env.example to backend/.env" >&2
  exit 1
fi

# Export KEY=VALUE lines (skip comments and blanks).
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

# Paths in .env are relative to repo root.
if [[ -n "${FCM_SERVICE_ACCOUNT_FILE:-}" && ! "$FCM_SERVICE_ACCOUNT_FILE" = /* ]]; then
  export FCM_SERVICE_ACCOUNT_FILE="$ROOT/$FCM_SERVICE_ACCOUNT_FILE"
fi

echo "==> Backend env:"
echo "    FCM_ENABLED=${FCM_ENABLED:-false}"
echo "    FCM_SERVICE_ACCOUNT_FILE=${FCM_SERVICE_ACCOUNT_FILE:-<unset>}"
echo "    BILLING_ENFORCEMENT=${BILLING_ENFORCEMENT:-false}"
echo ""

cd "$ROOT/backend"
exec mvn spring-boot:run -DskipTests
