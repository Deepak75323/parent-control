#!/usr/bin/env bash
# Install Firebase google-services.json for the child Android app.
#
# Option A — copy a local file:
#   ./scripts/dev/install-google-services.sh /path/to/google-services.json
#
# Option B — base64 env (CI / secrets manager):
#   export FIREBASE_GOOGLE_SERVICES_JSON_B64="$(base64 < google-services.json)"
#   ./scripts/dev/install-google-services.sh
#
# Download from Firebase Console → Project settings → Your apps → Android (com.childmonitor).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEST="$ROOT/child/android/app/google-services.json"

if [[ "${1:-}" != "" ]]; then
  if [[ ! -f "$1" ]]; then
    echo "File not found: $1" >&2
    exit 1
  fi
  cp "$1" "$DEST"
  echo "Installed google-services.json from $1"
  exit 0
fi

if [[ -n "${FIREBASE_GOOGLE_SERVICES_JSON_B64:-}" ]]; then
  echo "$FIREBASE_GOOGLE_SERVICES_JSON_B64" | base64 --decode > "$DEST"
  echo "Installed google-services.json from FIREBASE_GOOGLE_SERVICES_JSON_B64"
  exit 0
fi

if [[ -n "${FIREBASE_GOOGLE_SERVICES_JSON:-}" && -f "${FIREBASE_GOOGLE_SERVICES_JSON}" ]]; then
  cp "$FIREBASE_GOOGLE_SERVICES_JSON" "$DEST"
  echo "Installed google-services.json from FIREBASE_GOOGLE_SERVICES_JSON"
  exit 0
fi

echo "No google-services.json source provided." >&2
echo "  ./scripts/dev/install-google-services.sh /path/to/google-services.json" >&2
echo "  or set FIREBASE_GOOGLE_SERVICES_JSON_B64 / FIREBASE_GOOGLE_SERVICES_JSON" >&2
echo "Template: child/android/app/google-services.json.example" >&2
exit 1
