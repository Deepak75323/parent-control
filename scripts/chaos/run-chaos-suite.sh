#!/usr/bin/env bash
# Unified chaos engineering runner for Parental Control Platform.
# Usage:
#   ./scripts/chaos/run-chaos-suite.sh --suite ci-backend
#   ./scripts/chaos/run-chaos-suite.sh --suite nightly --device-serial <adb>
#   ./scripts/chaos/run-chaos-suite.sh --scenario BE-03
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHAOS_LIB="$ROOT/scripts/chaos/lib"
SCENARIOS="$ROOT/scripts/chaos/scenarios"
RESULTS_DIR="${RESULTS_DIR:-$ROOT/artifacts/chaos/$(date +%Y%m%d-%H%M%S)}"

SUITE="${SUITE:-ci-backend}"
SCENARIO=""
DEVICE_SERIAL="${DEVICE_SERIAL:-}"
API_HOST="${API_HOST:-http://localhost:8080}"
COMPOSE="$ROOT/scripts/dev/compose.sh"

source "$CHAOS_LIB/common.sh"
source "$CHAOS_LIB/scorecard.sh"

mkdir -p "$RESULTS_DIR"
scorecard_init "$RESULTS_DIR/scorecard.json" "$SUITE"

run_scenario() {
  local id="$1"
  local script="$2"
  if [[ ! -x "$script" ]]; then
    chmod +x "$script" 2>/dev/null || true
  fi
  log "Running scenario $id: $script"
  local start_ts
  start_ts=$(date +%s)
  local status="PASS"
  local evidence=""
  if ! DEVICE_SERIAL="$DEVICE_SERIAL" API_HOST="$API_HOST" COMPOSE="$COMPOSE" \
      bash "$script" >"$RESULTS_DIR/${id}.log" 2>&1; then
    status="FAIL"
    evidence="See $RESULTS_DIR/${id}.log"
  fi
  local end_ts
  end_ts=$(date +%s)
  scorecard_add "$id" "$id" "$status" "$((end_ts - start_ts))" "$evidence"
}

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]
  --suite ci-backend|nightly|oem-matrix   Run predefined suite (default: ci-backend)
  --scenario ID                           Run single scenario by matrix ID
  --device-serial SERIAL                  ADB serial for Android scenarios
  --api-host URL                          API base (default: http://localhost:8080)
  --results-dir PATH                      Output directory
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --suite) SUITE="$2"; shift 2 ;;
    --scenario) SCENARIO="$2"; shift 2 ;;
    --device-serial) DEVICE_SERIAL="$2"; shift 2 ;;
    --api-host) API_HOST="$2"; shift 2 ;;
    --results-dir) RESULTS_DIR="$2"; mkdir -p "$RESULTS_DIR"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) err "Unknown option: $1"; usage; exit 1 ;;
  ;;
  esac
done

scenario_script() {
  case "$1" in
    BE-01) echo "$SCENARIOS/backend/pod-kill.sh" ;;
    BE-02) echo "$SCENARIOS/backend/rolling-deploy.sh" ;;
    BE-03) echo "$SCENARIOS/backend/redis-outage.sh" ;;
    BE-04) echo "$SCENARIOS/backend/postgres-restart.sh" ;;
    BE-05) echo "$SCENARIOS/backend/kafka-outage.sh" ;;
    NET-06) echo "$ROOT/scripts/test/stomp-multi-pod-smoke.sh" ;;
    NET-01) echo "$SCENARIOS/network/airplane-mode.sh" ;;
    NET-02) echo "$SCENARIOS/network/api-unreachable.sh" ;;
    NET-05|STR-01) echo "$SCENARIOS/streaming/turn-outage.sh" ;;
    AND-01) echo "$SCENARIOS/android/process-kill.sh" ;;
    AND-02|STR-04) echo "$SCENARIOS/android/force-stop.sh" ;;
    AND-03) echo "$SCENARIOS/android/reboot-recovery.sh" ;;
    AND-08) echo "$SCENARIOS/android/workmanager-delay.sh" ;;
    BE-07) echo "$SCENARIOS/backend/command-burst-redis-outage.sh" ;;
    STR-BURST) echo "$SCENARIOS/streaming/stream-burst-turn-outage.sh" ;;
    *) return 1 ;;
  esac
}

if [[ -n "$SCENARIO" ]]; then
  script="$(scenario_script "$SCENARIO")" || { err "Unknown scenario $SCENARIO"; exit 1; }
  run_scenario "$SCENARIO" "$script"
else
  case "$SUITE" in
    ci-backend)
      run_scenario "BE-03" "$SCENARIOS/backend/redis-outage.sh"
      run_scenario "BE-04" "$SCENARIOS/backend/postgres-restart.sh"
      run_scenario "BE-07" "$SCENARIOS/backend/command-burst-redis-outage.sh"
      run_scenario "STR-BURST" "$SCENARIOS/streaming/stream-burst-turn-outage.sh"
      ;;
    nightly)
      run_scenario "BE-03" "$SCENARIOS/backend/redis-outage.sh"
      run_scenario "BE-04" "$SCENARIOS/backend/postgres-restart.sh"
      run_scenario "NET-02" "$SCENARIOS/network/api-unreachable.sh"
      run_scenario "NET-05" "$SCENARIOS/streaming/turn-outage.sh"
      run_scenario "BE-07" "$SCENARIOS/backend/command-burst-redis-outage.sh"
      run_scenario "STR-BURST" "$SCENARIOS/streaming/stream-burst-turn-outage.sh"
      run_scenario "NET-06" "$ROOT/scripts/test/stomp-multi-pod-smoke.sh"
      if [[ -n "$DEVICE_SERIAL" ]]; then
        run_scenario "AND-01" "$SCENARIOS/android/process-kill.sh"
        run_scenario "AND-02" "$SCENARIOS/android/force-stop.sh"
        run_scenario "AND-03" "$SCENARIOS/android/reboot-recovery.sh"
      else
        scorecard_add "AND-01" "process-kill" "SKIP" 0 "No DEVICE_SERIAL"
        scorecard_add "AND-02" "force-stop" "SKIP" 0 "No DEVICE_SERIAL"
        scorecard_add "AND-03" "reboot-recovery" "SKIP" 0 "No DEVICE_SERIAL"
      fi
      ;;
    oem-matrix)
      log "Delegating to OEM certification framework"
      "$ROOT/scripts/validation/oem/run-certification.sh" --oem auto ${DEVICE_SERIAL:+--serial "$DEVICE_SERIAL"}
      ;;
    *)
      err "Unknown suite: $SUITE"; exit 1
      ;;
  esac
fi

scorecard_finalize "$RESULTS_DIR/scorecard.json"
log "Scorecard: $RESULTS_DIR/scorecard.json"
scorecard_print_summary "$RESULTS_DIR/scorecard.json"
scorecard_upload "$RESULTS_DIR/scorecard.json"

failed=$(jq -r '.summary.failed // 0' "$RESULTS_DIR/scorecard.json")
[[ "$failed" -eq 0 ]] || exit 1
