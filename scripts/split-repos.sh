#!/usr/bin/env bash
# Split monorepo into three deployable repository folders.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/../parental-control-repos}"

mkdir -p "$OUT/parental-control-backend" "$OUT/parental-control-parent" "$OUT/parental-control-child"

echo "Copying backend..."
rsync -a --exclude target --exclude .env "$ROOT/backend/" "$OUT/parental-control-backend/"
cp "$ROOT/docs/templates/README-parental-control-backend.md" "$OUT/parental-control-backend/README.md"

echo "Copying parent..."
rsync -a --exclude node_modules "$ROOT/parent/" "$OUT/parental-control-parent/"
cp "$ROOT/docs/templates/README-parental-control-parent.md" "$OUT/parental-control-parent/README.md"

echo "Copying child..."
rsync -a --exclude node_modules "$ROOT/child/" "$OUT/parental-control-child/"
cp "$ROOT/docs/templates/README-parental-control-child.md" "$OUT/parental-control-child/README.md"

echo "Done. Repositories prepared under: $OUT"
echo "Next: cd each folder && git init && git remote add origin <url>"
