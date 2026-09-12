#!/usr/bin/env bash
# Chat decrypt codes are created in the portal only. They must not be written to .env.
#
# Usage (kept so existing deploy scripts can call this):
#   ./scripts/deploy/ensure-chat-decrypt-pin.sh
set -euo pipefail

echo "Chat decrypt code is generated in the portal (Decrypt chat)."
echo "CHAT_DECRYPT_PIN in .env is ignored and must not store the secret."
echo "Do not paste the code into chat."
exit 0
