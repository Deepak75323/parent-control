#!/usr/bin/env bash
# Quick fix: route the phone's localhost:8080 to your Mac backend (required for USB dev).
set -euo pipefail

ADB="${ADB:-$(command -v adb || true)}"
PHONE="${PHONE_SERIAL:-8HNNIBTK4PY9NRIJ}"

if [[ -z "$ADB" || ! -x "$ADB" ]]; then
  echo "adb not found. Install Android platform-tools." >&2
  exit 1
fi

if ! "$ADB" devices | grep -q "$PHONE"; then
  echo "Phone $PHONE not found. Connect via USB and enable USB debugging." >&2
  "$ADB" devices
  exit 1
fi

"$ADB" -s "$PHONE" reverse tcp:8080 tcp:8080
"$ADB" -s "$PHONE" reverse tcp:8082 tcp:8082

echo "OK — phone localhost:8080 now points to your Mac backend."
echo "Reverse tunnels:"
"$ADB" -s "$PHONE" reverse --list
