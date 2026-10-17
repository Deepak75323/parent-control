#!/usr/bin/env bash
# Build backend on the Oracle VM and swap it in without killing the old JAR first.
#
# Usage (from laptop):
#   ./scripts/deploy/oracle-deploy-backend.sh
#   BACKEND_IMAGE_TAG=chat-release ./scripts/deploy/oracle-deploy-backend.sh
#
# If the new image fails health checks, the previous backend keeps serving traffic.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SSH_KEY="${SSH_KEY:-$HOME/Downloads/ssh-key-2026-07-13.key}"
HOST="${ORACLE_HOST:-ubuntu@80.225.196.181}"
REMOTE_ROOT="${REMOTE_ROOT:-~/parentalControlApp}"
IMAGE_TAG="${BACKEND_IMAGE_TAG:-release-$(date +%Y%m%d%H%M)}"
IMAGE="localhost/parentalcontrolapp_backend:${IMAGE_TAG}"

if [[ ! -f "$SSH_KEY" ]]; then
  echo "SSH key not found: $SSH_KEY" >&2
  exit 1
fi

SSH=(ssh -o BatchMode=yes -i "$SSH_KEY" "$HOST")
RSYNC_SSH=(ssh -i "$SSH_KEY" -o BatchMode=yes)

echo "==> Syncing backend sources to $HOST"
rsync -az --delete \
  --exclude target \
  --exclude .env \
  -e "${RSYNC_SSH[*]}" \
  "$ROOT/backend/" \
  "$HOST:${REMOTE_ROOT}/backend/"

rsync -az \
  -e "${RSYNC_SSH[*]}" \
  "$ROOT/scripts/deploy/oracle-swap-backend.sh" \
  "$ROOT/scripts/deploy/ensure-chat-decrypt-pin.sh" \
  "$ROOT/scripts/deploy/nginx/pcp-site.conf" \
  "$HOST:${REMOTE_ROOT}/scripts/deploy/"

rsync -az \
  -e "${RSYNC_SSH[*]}" \
  "$ROOT/scripts/deploy/nginx/pcp-site.conf" \
  "$HOST:${REMOTE_ROOT}/scripts/deploy/nginx/"

echo "==> Building image $IMAGE on VM"
"${SSH[@]}" bash -s <<REMOTE
set -euo pipefail
cd ${REMOTE_ROOT}
chmod +x scripts/deploy/oracle-swap-backend.sh scripts/deploy/ensure-chat-decrypt-pin.sh
# Create-once PIN at server setup (no-op if already set; prints once when created).
./scripts/deploy/ensure-chat-decrypt-pin.sh
podman build -t "$IMAGE" -f backend/Dockerfile backend/
REMOTE

echo "==> Blue/green swap (old backend stays up until candidate is healthy)"
"${SSH[@]}" bash -s <<REMOTE
set -euo pipefail
cd ${REMOTE_ROOT}
BACKEND_IMAGE="$IMAGE" ./scripts/deploy/oracle-swap-backend.sh
REMOTE

echo ""
echo "Backend deploy finished."
echo "  image=$IMAGE"
echo "  health: http://80.225.196.181/actuator/health"
