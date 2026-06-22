#!/usr/bin/env bash
# BE-02: Rolling deployment during active traffic.
set -euo pipefail
NAMESPACE="${NAMESPACE:-default}"
RELEASE="${RELEASE:-parental-control-api}"
API_HOST="${API_HOST:-http://localhost:8080}"

kubectl -n "$NAMESPACE" rollout restart "deploy/$RELEASE"
for i in $(seq 1 30); do
  curl -sf "$API_HOST/actuator/health" >/dev/null || exit 1
  sleep 2
done
kubectl -n "$NAMESPACE" rollout status "deploy/$RELEASE" --timeout=300s
echo "Rolling deploy OK"
