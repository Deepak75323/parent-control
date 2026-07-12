#!/usr/bin/env bash
# Send FCM data wake via HTTP v1 using backend service account JSON.
# Usage:
#   ./scripts/dev/send-fcm-v1-wake.sh STREAM_WAKE <device_fcm_token>
#   ./scripts/dev/send-fcm-v1-wake.sh DEVICE_COMMAND <token> <commandId> <commandType>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ACTION="${1:-STREAM_WAKE}"
TOKEN="${2:-${DEVICE_FCM_TOKEN:-}}"
COMMAND_ID="${3:-}"
COMMAND_TYPE="${4:-}"

SA_FILE="${FCM_SERVICE_ACCOUNT_FILE:-$ROOT/backend/secrets/firebase-service-account.json}"
if [[ ! -f "$SA_FILE" ]]; then
  echo "Service account not found: $SA_FILE" >&2
  exit 1
fi
if [[ -z "$TOKEN" ]]; then
  echo "Usage: $0 STREAM_WAKE <fcm_token>" >&2
  exit 1
fi

python3 <<PY
import json, sys, urllib.request
from google.oauth2 import service_account
from google.auth.transport.requests import Request

sa_path = "$SA_FILE"
action = "$ACTION"
device_token = "$TOKEN"
command_id = "$COMMAND_ID"
command_type = "$COMMAND_TYPE"

with open(sa_path) as f:
    info = json.load(f)
project_id = info["project_id"]

creds = service_account.Credentials.from_service_account_file(
    sa_path, scopes=["https://www.googleapis.com/auth/firebase.messaging"]
)
creds.refresh(Request())
access_token = creds.token

data = {"action": action}
if action == "DEVICE_COMMAND" and command_id and command_type:
    data["commandId"] = command_id
    data["commandType"] = command_type

body = {
    "message": {
        "token": device_token,
        "data": data,
        "android": {"priority": "HIGH"},
    }
}
url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
req = urllib.request.Request(
    url,
    data=json.dumps(body).encode(),
    headers={
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json",
    },
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        print(resp.read().decode())
        print(f"HTTP {resp.status} — FCM v1 sent action={action}")
except urllib.error.HTTPError as e:
    print(e.read().decode(), file=sys.stderr)
    print(f"HTTP {e.code} — FCM v1 failed", file=sys.stderr)
    sys.exit(1)
PY
