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
"$ADB" -s "$PHONE" reverse tcp:3478 tcp:3478

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if ! pgrep -f "turnserver.*realm=pcp" >/dev/null 2>&1; then
  nohup "$ROOT/scripts/dev/start-coturn.sh" >/tmp/pcp-coturn-nohup.log 2>&1 &
  for _ in 1 2 3 4 5; do
    sleep 1
    pgrep -f "turnserver.*realm=pcp" >/dev/null 2>&1 && break
  done
fi
if ! pgrep -f "turnserver.*realm=pcp" >/dev/null 2>&1; then
  echo "WARN — coturn did not start. Run: nohup ./scripts/dev/start-coturn.sh &" >&2
fi

echo "OK — phone localhost:8080 now points to your Mac backend (TURN on :3478 if coturn started)."
echo "Reverse tunnels:"
"$ADB" -s "$PHONE" reverse --list
