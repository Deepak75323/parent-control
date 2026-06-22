#!/usr/bin/env bash
# Run backend integration tests against local Podman Postgres + Redis.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

CONTAINER="${PCP_POSTGRES_CONTAINER:-pcp-postgres}"

if ! podman container exists "$CONTAINER" 2>/dev/null; then
  echo "Starting postgres + redis via compose..."
  ./scripts/dev/compose.sh up -d postgres redis
fi

for db in parental_control_auth_test parental_control_security_test parental_control_presence_test \
  parental_control_command_delivery_test parental_control_command_retry_test \
  parental_control_command_idempotency_test parental_control_chaos_reliability_test \
  parental_control_stomp_redis_test parental_control_stomp_relay_test; do
  echo "Recreating database ${db}..."
  podman exec "$CONTAINER" psql -U pcp -d postgres -c \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${db}' AND pid <> pg_backend_pid();" \
    2>/dev/null || true
  podman exec "$CONTAINER" psql -U pcp -d postgres -c "DROP DATABASE IF EXISTS ${db};" 2>/dev/null || true
  podman exec "$CONTAINER" psql -U pcp -d postgres -c "CREATE DATABASE ${db};"
done

echo "Running backend tests..."
cd "$ROOT/backend"
mvn test "$@"
