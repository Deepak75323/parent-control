#!/usr/bin/env bash
# Install Firebase service account JSON for backend FCM HTTP v1.
#
# Download from Firebase Console → Project settings → Service accounts → Generate new private key
# Use the SAME Firebase project as child google-services.json (project_id must match).
#
# Usage:
#   ./scripts/dev/install-firebase-service-account.sh /path/to/parent-448bf-firebase-adminsdk-....json
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEST="$ROOT/backend/secrets/firebase-service-account.json"
CHILD_GS="$ROOT/child/android/app/google-services.json"

if [[ "${1:-}" == "" ]]; then
  echo "Usage: $0 /path/to/firebase-adminsdk-....json" >&2
  exit 1
fi
if [[ ! -f "$1" ]]; then
  echo "File not found: $1" >&2
  exit 1
fi

mkdir -p "$(dirname "$DEST")"
cp "$1" "$DEST"
chmod 600 "$DEST"

SA_PROJECT=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['project_id'])" "$DEST")
echo "Installed service account → $DEST"
echo "  project_id: $SA_PROJECT"

if [[ -f "$CHILD_GS" ]]; then
  CHILD_PROJECT=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['project_info']['project_id'])" "$CHILD_GS")
  if [[ "$SA_PROJECT" != "$CHILD_PROJECT" ]]; then
    echo "" >&2
    echo "WARNING: project mismatch!" >&2
    echo "  Service account: $SA_PROJECT" >&2
    echo "  Child google-services.json: $CHILD_PROJECT" >&2
    echo "  Re-download the key from Firebase project '$CHILD_PROJECT'." >&2
    exit 1
  fi
  echo "  OK — matches child google-services.json"
fi

echo ""
echo "Add to backend/.env:"
echo "  FCM_ENABLED=true"
echo "  FCM_SERVICE_ACCOUNT_FILE=backend/secrets/firebase-service-account.json"
