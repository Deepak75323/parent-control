#!/usr/bin/env bash
# Open psql in the Postgres container (Podman).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

CONTAINER="${PCP_POSTGRES_CONTAINER:-pcp-postgres}"

if ! podman container exists "$CONTAINER" 2>/dev/null; then
  echo "Container $CONTAINER not found. Start infra: ./scripts/dev/compose.sh up -d postgres" >&2
  exit 1
fi

exec podman exec -it "$CONTAINER" psql -U pcp -d parental_control "$@"
