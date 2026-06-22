#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/backend/docs/openapi"

mkdir -p "$OUT_DIR"
curl -sf "$API_URL/v3/api-docs" -o "$OUT_DIR/openapi.json"
echo "Exported $OUT_DIR/openapi.json"
