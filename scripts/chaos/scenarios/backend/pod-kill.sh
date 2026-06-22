#!/usr/bin/env bash
# BE-01: Pod kill (requires kubectl + K8s deployment).
set -euo pipefail
NAMESPACE="${NAMESPACE:-default}"
RELEASE="${RELEASE:-parental-control-api}"

POD=$(kubectl -n "$NAMESPACE" get pods -l app="$RELEASE" -o jsonpath='{.items[0].metadata.name}')
echo "Deleting pod $POD"
kubectl -n "$NAMESPACE" delete pod "$POD" --wait=false
kubectl -n "$NAMESPACE" rollout status "deploy/$RELEASE" --timeout=180s
echo "Pod kill recovery OK"
