#!/usr/bin/env bash
# Build debug APKs pointed at the Oracle staging API (parent/.env + child/.env).
# Sets EMBEDDED_BUNDLE=true so phones work without Metro on your Mac.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export EMBEDDED_BUNDLE=true

log() { echo "==> $*"; }

build_app() {
  local app="$1"
  local dir="$ROOT/$app"
  cd "$dir"

  if [[ -f .env ]]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
  fi
  export API_BASE_URL="${API_BASE_URL:-http://80.225.196.181}"
  export WS_BASE_URL="${WS_BASE_URL:-$API_BASE_URL/ws}"
  export WS_NATIVE_URL="${WS_NATIVE_URL:-${API_BASE_URL/http/ws}/ws-native}"
  export ENABLE_SSL_PINNING="${ENABLE_SSL_PINNING:-false}"
  export EMBEDDED_BUNDLE=true

  log "$app: API_BASE_URL=$API_BASE_URL EMBEDDED_BUNDLE=$EMBEDDED_BUNDLE"
  log "$app: npm install (if needed)..."
  if [[ ! -d node_modules ]] || [[ ! -d node_modules/babel-plugin-transform-inline-environment-variables ]]; then
    npm install
  fi

  log "$app: assembleDebug (Gradle embeds JS)..."
  cd android
  ./gradlew assembleDebug
  cd ..

  local apk
  apk="$(ls -1t android/app/build/outputs/apk/debug/*.apk | head -1)"
  cp -f "$apk" "$ROOT/dist/${app}-oracle-debug.apk"
  log "$app APK -> $ROOT/dist/${app}-oracle-debug.apk"
}

mkdir -p "$ROOT/dist"
build_app parent
build_app child

log "Done."
echo ""
echo "Install (USB):"
echo "  adb install -r $ROOT/dist/parent-oracle-debug.apk"
echo "  adb install -r $ROOT/dist/child-oracle-debug.apk"
echo ""
echo "Or copy APKs from dist/ to each phone and open them."
echo "API: http://80.225.196.181"
