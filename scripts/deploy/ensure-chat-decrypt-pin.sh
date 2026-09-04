#!/usr/bin/env bash
# Chat decrypt PIN helper for deploy .env.
#
# Preferred: leave CHAT_DECRYPT_PIN empty so the first room owner creates the PIN
# in the portal (Decrypt chat → Create decrypt PIN).
#
# If .env already has a valid 4-digit PIN → no-op (does not reprint).
# Optional: --generate writes a one-time env PIN (legacy / headless deploys).
#
# Usage:
#   ./scripts/deploy/ensure-chat-decrypt-pin.sh
#   ENV_FILE=/path/to/.env ./scripts/deploy/ensure-chat-decrypt-pin.sh
#   ./scripts/deploy/ensure-chat-decrypt-pin.sh --generate
#   ./scripts/deploy/ensure-chat-decrypt-pin.sh --dry-run
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"
DRY_RUN=0
GENERATE=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --generate) GENERATE=1 ;;
  esac
done

is_valid_pin() {
  [[ "${1:-}" =~ ^[0-9]{4}$ ]]
}

generate_pin() {
  if command -v od >/dev/null 2>&1; then
    local n
    n="$(od -An -N2 -tu2 /dev/urandom | tr -d '[:space:]')"
    echo $((1000 + (n % 9000)))
  else
    echo $((1000 + (RANDOM % 9000)))
  fi
}

current_pin=""
if [[ -f "$ENV_FILE" ]] && grep -q '^CHAT_DECRYPT_PIN=' "$ENV_FILE"; then
  current_pin="$(grep '^CHAT_DECRYPT_PIN=' "$ENV_FILE" | tail -n1 | cut -d= -f2- | tr -d '\r' | tr -d '"' | tr -d "'")"
fi

if is_valid_pin "$current_pin"; then
  echo "CHAT_DECRYPT_PIN already set in $ENV_FILE (not reprinted)."
  exit 0
fi

if [[ "$GENERATE" -ne 1 ]]; then
  echo "CHAT_DECRYPT_PIN not set — room owners create it in the portal (Decrypt chat)."
  echo "For a one-time env PIN instead: $0 --generate"
  exit 0
fi

NEW_PIN="$(generate_pin)"
if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "[dry-run] would write CHAT_DECRYPT_PIN=$NEW_PIN to $ENV_FILE"
  exit 0
fi

mkdir -p "$(dirname "$ENV_FILE")"
touch "$ENV_FILE"
if grep -q '^CHAT_DECRYPT_PIN=' "$ENV_FILE"; then
  if [[ "$(uname -s)" == "Darwin" ]]; then
    sed -i.bak "s/^CHAT_DECRYPT_PIN=.*/CHAT_DECRYPT_PIN=${NEW_PIN}/" "$ENV_FILE"
  else
    sed -i.bak "s/^CHAT_DECRYPT_PIN=.*/CHAT_DECRYPT_PIN=${NEW_PIN}/" "$ENV_FILE"
  fi
  rm -f "${ENV_FILE}.bak"
else
  printf '\n# Optional env fallback for chat decrypt PIN (prefer portal create).\nCHAT_DECRYPT_PIN=%s\n' "$NEW_PIN" >>"$ENV_FILE"
fi

cat <<EOF

╔══════════════════════════════════════════════════════════╗
║  SAVE THIS — shown once (chat decrypt PIN)               ║
║                                                          ║
║  CHAT_DECRYPT_PIN = ${NEW_PIN}                                 ║
║                                                          ║
║  Room owners use this to Decrypt chat.                   ║
║  Forgot? Use portal Wipe & reset PIN (deletes room chat).║
╚══════════════════════════════════════════════════════════╝

EOF
