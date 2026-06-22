#!/usr/bin/env bash
# Xiaomi Validation Automation — entrypoint
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

export API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
export PARENT_EMAIL="${PARENT_EMAIL:-parent@example.com}"
export PARENT_PASSWORD="${PARENT_PASSWORD:-password}"

SERIAL="${ADB_SERIAL:-}"
EXTRA=()
if [[ -n "$SERIAL" ]]; then
  EXTRA+=(--serial "$SERIAL")
fi
if [[ "${XIAOMI_FAST:-}" == "1" ]]; then
  EXTRA+=(--fast)
fi
if [[ "${XIAOMI_ALLOW_MANUAL_PRECHECK:-}" == "1" ]]; then
  EXTRA+=(--allow-manual-precheck)
fi

echo "== Xiaomi Validation Framework =="
echo "API: $API_BASE_URL"
echo "Device: ${SERIAL:-default}"

# Ensure backend reachable
if ! curl -sf "${API_BASE_URL}/actuator/health" >/dev/null 2>&1; then
  if ! curl -sf "${API_BASE_URL}/api/v1/health" >/dev/null 2>&1; then
    echo "WARN: backend health check failed — ensure ./scripts/dev/dev-up.sh is running"
  fi
fi

python3 "$ROOT/scripts/validation/xiaomi/xiaomi_validate.py" "${EXTRA[@]}" "$@"
EXIT=$?

echo ""
echo "Artifacts:"
echo "  scripts/validation/xiaomi/xiaomi-precheck-report.json"
echo "  scripts/validation/xiaomi/xiaomi-context.json"
echo "  logs/xiaomi/<run-id>/"
echo "  reports/xiaomi-validation.html"

exit $EXIT
