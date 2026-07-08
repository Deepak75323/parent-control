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

if [[ -z "${TURN_EXTERNAL_HOST:-}" ]]; then
  TURN_EXTERNAL_HOST="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo 127.0.0.1)"
  export TURN_EXTERNAL_HOST
fi

# Netty/Lettuce on some macOS setups fail async DNS for "localhost" (NXDOMAIN).
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/parental_control}"
SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST/localhost/127.0.0.1}"
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL/localhost/127.0.0.1}"
export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://127.0.0.1:4318/v1/traces}"
OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT/localhost/127.0.0.1}"
# Local jar mode: avoid flaky macOS localhost DNS and optional OTLP collector.
export OBSERVABILITY_TRACING_ENABLED=false
export MANAGEMENT_TRACING_ENABLED=false
export MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED=false
export KAFKA_ENABLED="${KAFKA_ENABLED:-false}"
export SPRING_DATA_REDIS_HOST SPRING_DATASOURCE_URL OTEL_EXPORTER_OTLP_ENDPOINT OBSERVABILITY_TRACING_ENABLED KAFKA_ENABLED MANAGEMENT_TRACING_ENABLED MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED

echo "==> Backend env:"
echo "    FCM_ENABLED=${FCM_ENABLED:-false}"
echo "    FCM_SERVICE_ACCOUNT_FILE=${FCM_SERVICE_ACCOUNT_FILE:-<unset>}"
echo "    BILLING_ENFORCEMENT=${BILLING_ENFORCEMENT:-false}"
echo "    TURN_EXTERNAL_HOST=${TURN_EXTERNAL_HOST}"
echo "    SPRING_DATA_REDIS_HOST=${SPRING_DATA_REDIS_HOST}"
echo "    SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL}"
echo ""

cd "$ROOT/backend"

jar_path() {
  ls -1 target/parental-control-api-*.jar 2>/dev/null | grep -v original | head -1
}

JAR="$(jar_path || true)"
if [[ -z "$JAR" || "${BACKEND_FORCE_BUILD:-}" == "1" ]]; then
  echo "==> Building backend jar..."
  mvn -q package -DskipTests
  JAR="$(jar_path)"
fi

if [[ "${BACKEND_USE_MVN_RUN:-}" == "1" ]]; then
  exec mvn spring-boot:run -DskipTests
fi

echo "==> Starting $JAR"
exec java -jar "$JAR"
