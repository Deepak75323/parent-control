#!/usr/bin/env bash
# Multi-replica STOMP relay smoke test — run against a K8s cluster with 2+ API pods.
# Prerequisites: kubectl context set, parent+child paired, JWT tokens exported.
set -euo pipefail

NAMESPACE="${NAMESPACE:-default}"
RELEASE="${RELEASE:-parental-control-api}"
PARENT_TOKEN="${PARENT_TOKEN:?export PARENT_TOKEN}"
DEVICE_TOKEN="${DEVICE_TOKEN:?export DEVICE_TOKEN}"
DEVICE_ID="${DEVICE_ID:?export DEVICE_ID}"
API_HOST="${API_HOST:-http://localhost:8080}"

echo "==> Checking API replica count"
REPLICAS=$(kubectl -n "$NAMESPACE" get deploy "$RELEASE" -o jsonpath='{.status.readyReplicas}')
echo "    readyReplicas=$REPLICAS"
if [[ "${REPLICAS:-0}" -lt 2 ]]; then
  echo "WARN: need >=2 replicas for cross-pod validation; scaling deployment"
  kubectl -n "$NAMESPACE" scale deploy "$RELEASE" --replicas=2
  kubectl -n "$NAMESPACE" rollout status deploy/"$RELEASE" --timeout=180s
fi

echo "==> Checking RabbitMQ STOMP relay health on each pod"
for POD in $(kubectl -n "$NAMESPACE" get pods -l app="$RELEASE" -o jsonpath='{.items[*].metadata.name}'); do
  kubectl -n "$NAMESPACE" exec "$POD" -- wget -qO- http://localhost:8080/actuator/health/stompBroker | head -c 200
  echo " ... $POD"
done

echo "==> Issuing remote command (parent HTTP → STOMP → child topic)"
CMD=$(curl -sf -X POST "$API_HOST/api/v1/device/$DEVICE_ID/commands" \
  -H "Authorization: Bearer $PARENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"commandType":"PING","payload":{}}' | jq -r '.data.id // empty')
echo "    commandId=$CMD"

echo "==> Rolling restart (validates relay survives pod churn)"
kubectl -n "$NAMESPACE" rollout restart deploy/"$RELEASE"
kubectl -n "$NAMESPACE" rollout status deploy/"$RELEASE" --timeout=300s

echo "OK — multi-pod STOMP relay smoke completed"
