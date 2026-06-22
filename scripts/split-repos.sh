#!/usr/bin/env bash
# Split monorepo into three deployable repository folders.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/../parental-control-repos}"

mkdir -p "$OUT/parental-control-backend" "$OUT/parental-control-parent-app" "$OUT/parental-control-child-app"

echo "Copying backend..."
rsync -a --exclude target --exclude .env "$ROOT/backend/" "$OUT/parental-control-backend/"
cp "$ROOT/docs/templates/README-parental-control-backend.md" "$OUT/parental-control-backend/README.md"

echo "Copying parent-app..."
rsync -a --exclude node_modules "$ROOT/parent-app/" "$OUT/parental-control-parent-app/"
cp "$ROOT/docs/templates/README-parental-control-parent-app.md" "$OUT/parental-control-parent-app/README.md"

echo "Copying child-app..."
rsync -a --exclude node_modules "$ROOT/child-app/" "$OUT/parental-control-child-app/"
cp "$ROOT/docs/templates/README-parental-control-child-app.md" "$OUT/parental-control-child-app/README.md"

echo "Done. Repositories prepared under: $OUT"
echo "Next: cd each folder && git init && git remote add origin <url>"
