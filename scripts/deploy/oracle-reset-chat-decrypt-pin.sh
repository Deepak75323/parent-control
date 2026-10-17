#!/usr/bin/env bash
# Reset the chat decrypt PIN on the Oracle VM (forgot-PIN recovery).
#
# Generates a new 4-digit PIN, writes it to .env, prints it once, then
# blue/green swaps the backend so the running container picks it up.
#
# Usage (from laptop):
#   ./scripts/deploy/oracle-reset-chat-decrypt-pin.sh
#
# Or on the VM:
#   ENV_FILE=~/parentalControlApp/.env ./scripts/deploy/ensure-chat-decrypt-pin.sh  # only if missing
#   # force reset:
#   sed -i 's/^CHAT_DECRYPT_PIN=.*/CHAT_DECRYPT_PIN=/' ~/parentalControlApp/.env
#   ./scripts/deploy/ensure-chat-decrypt-pin.sh
#   BACKEND_IMAGE=… ./scripts/deploy/oracle-swap-backend.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SSH_KEY="${SSH_KEY:-$HOME/Downloads/ssh-key-2026-07-13.key}"
HOST="${ORACLE_HOST:-ubuntu@80.225.196.181}"
REMOTE_ROOT="${REMOTE_ROOT:-~/parentalControlApp}"

if [[ ! -f "$SSH_KEY" ]]; then
  echo "SSH key not found: $SSH_KEY" >&2
  exit 1
fi

SSH=(ssh -o BatchMode=yes -i "$SSH_KEY" "$HOST")

# Sync helper scripts so the VM has the latest ensure/reset logic.
rsync -az \
  -e "ssh -i $SSH_KEY -o BatchMode=yes" \
  "$ROOT/scripts/deploy/ensure-chat-decrypt-pin.sh" \
  "$ROOT/scripts/deploy/oracle-swap-backend.sh" \
  "$HOST:${REMOTE_ROOT}/scripts/deploy/"

echo "==> Forcing a new CHAT_DECRYPT_PIN on $HOST"
"${SSH[@]}" bash -s <<'REMOTE'
set -euo pipefail
cd ~/parentalControlApp
chmod +x scripts/deploy/ensure-chat-decrypt-pin.sh scripts/deploy/oracle-swap-backend.sh
ENV_FILE="$PWD/.env"
touch "$ENV_FILE"
# Clear existing so ensure regenerates and prints once.
if grep -q '^CHAT_DECRYPT_PIN=' "$ENV_FILE"; then
  if [[ "$(uname -s)" == "Darwin" ]]; then
    sed -i.bak 's/^CHAT_DECRYPT_PIN=.*/CHAT_DECRYPT_PIN=/' "$ENV_FILE"
  else
    sed -i.bak 's/^CHAT_DECRYPT_PIN=.*/CHAT_DECRYPT_PIN=/' "$ENV_FILE"
  fi
  rm -f "${ENV_FILE}.bak"
else
  printf '\nCHAT_DECRYPT_PIN=\n' >>"$ENV_FILE"
fi
./scripts/deploy/ensure-chat-decrypt-pin.sh
# Reload backend with the new env (reuse current image).
IMAGE="$(podman inspect pcp-backend --format '{{.ImageName}}' 2>/dev/null || true)"
if [[ -z "$IMAGE" ]]; then
  IMAGE="$(podman inspect pcp-backend --format '{{.Config.Image}}' 2>/dev/null || true)"
fi
if [[ -z "$IMAGE" ]]; then
  echo "No running pcp-backend — PIN written to .env; start/deploy backend next." >&2
  exit 0
fi
BACKEND_IMAGE="$IMAGE" ./scripts/deploy/oracle-swap-backend.sh
REMOTE

echo ""
echo "PIN reset finished. Copy the CHAT_DECRYPT_PIN from the banner above and share it out-of-band with room owners."
