#!/usr/bin/env bash
# P1-10: Unified OEM certification runner
# Usage:
#   ./scripts/validation/oem/run-certification.sh --oem xiaomi
#   ./scripts/validation/oem/run-certification.sh --oem all --serial <adb>
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OEM="${OEM:-}"
SERIAL="${ADB_SERIAL:-}"
FAST="${CERT_FAST:-0}"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]
  --oem VENDOR     xiaomi|samsung|oppo|vivo|realme|oneplus|motorola|pixel|all|auto
  --serial SERIAL  ADB device serial
  --fast           Short background/reboot windows
  --upload         POST report to API (requires ADMIN_TOKEN, DEVICE_ID)
EOF
}

UPLOAD=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --oem) OEM="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --fast) FAST=1; shift ;;
    --upload) UPLOAD=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1"; usage; exit 1 ;;
  esac
done

detect_oem() {
  local m
  m=$(adb ${SERIAL:+-s "$SERIAL"} shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]')
  case "$m" in
    xiaomi|redmi|poco) echo xiaomi ;;
    samsung) echo samsung ;;
    oppo) echo oppo ;;
    vivo) echo vivo ;;
    realme) echo realme ;;
    oneplus) echo oneplus ;;
    motorola|lenovo) echo motorola ;;
    google) echo pixel ;;
    *) echo generic ;;
  esac
}

if [[ -z "$OEM" || "$OEM" == "auto" ]]; then
  OEM=$(detect_oem)
  echo "Auto-detected OEM: $OEM"
fi

run_vendor() {
  local vendor="$1"
  echo "== OEM Certification: $vendor =="
  case "$vendor" in
    xiaomi)
      export ADB_SERIAL="$SERIAL"
      [[ "$FAST" == "1" ]] && export XIAOMI_FAST=1
      "$ROOT/scripts/validation/xiaomi/run-xiaomi-validation.sh"
      ;;
    samsung|oppo|vivo|realme|oneplus|motorola|pixel|generic)
      python3 "$ROOT/scripts/validation/oem/certify.py" \
        --vendor "$vendor" ${SERIAL:+--serial "$SERIAL"} ${FAST:+--fast}
      ;;
    *)
      echo "Unknown OEM: $vendor"; return 1 ;;
  esac
}

if [[ "$OEM" == "all" ]]; then
  FAIL=0
  for v in xiaomi samsung oppo vivo realme oneplus motorola pixel; do
    run_vendor "$v" || FAIL=1
  done
  exit $FAIL
fi

run_vendor "$OEM"

if [[ "$UPLOAD" == "1" && -f "$ROOT/reports/oem-certification.json" ]]; then
  API="${API_BASE_URL:-http://localhost:8080}"
  curl -sf -X POST "$API/api/v1/admin/certification/report" \
    -H "Authorization: Bearer ${ADMIN_TOKEN:?ADMIN_TOKEN required}" \
    -H "Content-Type: application/json" \
    -d @"$ROOT/reports/oem-certification.json" || echo "WARN: certification upload failed"
fi
