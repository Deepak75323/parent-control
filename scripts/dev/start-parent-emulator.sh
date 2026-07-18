#!/usr/bin/env bash
# Durable parent AVD launcher — headless, low memory, restarts via launchd KeepAlive.
# Usage:
#   ./scripts/dev/start-parent-emulator.sh          # ensure running + launch parent app
#   ./scripts/dev/start-parent-emulator.sh --install-agent
#   ./scripts/dev/start-parent-emulator.sh --stop
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
EMU_BIN="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"
AVD_NAME="${AVD_NAME:-parent_emulator}"
LABEL="com.parentalcontrol.parent-emulator"
PLIST="$HOME/Library/LaunchAgents/${LABEL}.plist"
LOG="/tmp/parent_emulator_keepalive.log"
PIDFILE="/tmp/parent_emulator_keepalive.pid"
APK="${PARENT_APK:-$ROOT/dist/parent-oracle-debug.apk}"

# Lean flags: avoid host GL ColorBuffer crashes under memory pressure.
# Audio must stay enabled — -no-audio makes WebRTC playback fail with ranchu pcmWrite I/O errors.
EMU_ARGS=(
  -avd "$AVD_NAME"
  -memory 1024
  -cores 2
  -gpu guest
  -no-boot-anim
  -no-window
  -no-snapshot-save
  -netdelay none
  -netspeed full
)

log() { echo "==> $*"; }

install_agent() {
  mkdir -p "$HOME/Library/LaunchAgents"
  cat >"$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>${EMU_BIN}</string>
    <string>-avd</string>
    <string>${AVD_NAME}</string>
    <string>-memory</string>
    <string>1024</string>
    <string>-cores</string>
    <string>2</string>
    <string>-gpu</string>
    <string>guest</string>
    <string>-no-boot-anim</string>
    <string>-no-window</string>
    <string>-no-snapshot-save</string>
    <string>-netdelay</string>
    <string>none</string>
    <string>-netspeed</string>
    <string>full</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>ANDROID_HOME</key>
    <string>${ANDROID_HOME}</string>
    <key>PATH</key>
    <string>${ANDROID_HOME}/emulator:${ANDROID_HOME}/platform-tools:/usr/bin:/bin</string>
  </dict>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>ThrottleInterval</key>
  <integer>45</integer>
  <key>ProcessType</key>
  <string>Interactive</string>
  <key>StandardOutPath</key>
  <string>${LOG}</string>
  <key>StandardErrorPath</key>
  <string>${LOG}</string>
  <key>WorkingDirectory</key>
  <string>/tmp</string>
</dict>
</plist>
EOF
  launchctl bootout "gui/$(id -u)/${LABEL}" 2>/dev/null || true
  launchctl bootstrap "gui/$(id -u)" "$PLIST"
  launchctl enable "gui/$(id -u)/${LABEL}" 2>/dev/null || true
  launchctl kickstart -k "gui/$(id -u)/${LABEL}" 2>/dev/null || launchctl kickstart "gui/$(id -u)/${LABEL}"
  log "LaunchAgent installed: $PLIST (KeepAlive, 1GB RAM, guest GPU, headless)"
}

stop_agent() {
  launchctl bootout "gui/$(id -u)/${LABEL}" 2>/dev/null || true
  pkill -f "qemu-system.*${AVD_NAME}|emulator -avd ${AVD_NAME}" 2>/dev/null || true
  log "Stopped parent emulator agent"
}

wait_online() {
  local i emu
  for i in $(seq 1 120); do
    emu="$("$ADB" devices 2>/dev/null | awk '/^emulator-.*device$/{print $1; exit}')"
    if [[ -n "$emu" ]]; then
      if [[ "$("$ADB" -s "$emu" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
        echo "$emu"
        return 0
      fi
    fi
    sleep 2
  done
  return 1
}

ensure_app() {
  local emu="$1"
  local path
  path="$("$ADB" -s "$emu" shell pm path com.parentscaffold 2>/dev/null | tr -d '\r' | head -1)"
  if [[ -z "$path" ]]; then
    [[ -f "$APK" ]] || { log "Missing APK: $APK"; return 1; }
    log "Installing $APK"
    "$ADB" -s "$emu" install -r "$APK"
  fi
  "$ADB" -s "$emu" shell am force-stop com.parentscaffold >/dev/null 2>&1 || true
  "$ADB" -s "$emu" shell am start -n com.parentscaffold/.MainActivity >/dev/null
  sleep 2
  log "parent pid=$("$ADB" -s "$emu" shell pidof com.parentscaffold | tr -d '\r')"
}

case "${1:-}" in
  --install-agent) install_agent; exit 0 ;;
  --stop) stop_agent; exit 0 ;;
  --help|-h)
    echo "Usage: $0 [--install-agent|--stop]"
    exit 0
    ;;
esac

# Persist lean AVD defaults (idempotent).
AVD_CFG="$HOME/.android/avd/${AVD_NAME}.avd/config.ini"
if [[ -f "$AVD_CFG" ]]; then
  sed -i.bak \
    -e 's/^hw.ramSize=.*/hw.ramSize=1024M/' \
    -e 's/^hw.cpu.ncore=.*/hw.cpu.ncore=2/' \
    -e 's/^hw.gpu.mode=.*/hw.gpu.mode=guest/' \
    "$AVD_CFG" 2>/dev/null || true
fi

# Prefer launchd-managed instance.
if [[ ! -f "$PLIST" ]]; then
  install_agent
else
  # Ensure agent is loaded.
  if ! launchctl print "gui/$(id -u)/${LABEL}" >/dev/null 2>&1; then
    install_agent
  else
    launchctl kickstart -k "gui/$(id -u)/${LABEL}" 2>/dev/null || true
  fi
fi

log "Waiting for emulator..."
EMU="$(wait_online)" || {
  log "Emulator failed to come online. Tail of $LOG:"
  tail -40 "$LOG" || true
  exit 1
}
log "Online: $EMU"
ensure_app "$EMU"
log "Done. Emulator is launchd-managed (auto-restarts). Stop with: $0 --stop"
