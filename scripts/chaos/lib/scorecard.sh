#!/usr/bin/env bash
# JSON scorecard helpers (requires jq)

scorecard_init() {
  local file="$1"
  local suite="$2"
  jq -n \
    --arg runId "$(uuidgen 2>/dev/null || date +%s)" \
    --arg startedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg suite "$suite" \
    '{
      runId: $runId,
      startedAt: $startedAt,
      environment: "local",
      suite: $suite,
      summary: { total: 0, passed: 0, failed: 0, skipped: 0, reliabilityScore: 0, mttrSecondsP50: 0, mttrSecondsP95: 0 },
      scenarios: []
    }' >"$file"
}

scorecard_add() {
  local file="$1"
  local id="$2"
  local name="$3"
  local status="$4"
  local mttr="$5"
  local evidence="$6"
  local tmp
  tmp=$(mktemp)
  jq \
    --arg id "$id" \
    --arg name "$name" \
    --arg status "$status" \
    --argjson mttr "$mttr" \
    --arg evidence "$evidence" \
    '.scenarios += [{
      id: $id,
      name: $name,
      status: $status,
      mttrSeconds: $mttr,
      recoveryExpectationMet: ($status == "PASS"),
      evidence: $evidence
    }]' "$file" >"$tmp" && mv "$tmp" "$file"
}

scorecard_finalize() {
  local file="$1"
  local tmp
  tmp=$(mktemp)
  jq '
    .finishedAt = (now | strftime("%Y-%m-%dT%H:%M:%SZ")) |
    .summary.total = (.scenarios | length) |
    .summary.passed = ([.scenarios[] | select(.status == "PASS")] | length) |
    .summary.failed = ([.scenarios[] | select(.status == "FAIL")] | length) |
    .summary.skipped = ([.scenarios[] | select(.status == "SKIP")] | length) |
    .summary.reliabilityScore = (
      if .summary.total == 0 then 0
      else ((.summary.passed / .summary.total) * 100)
      end
    ) |
    .summary.mttrSecondsP50 = (
      [.scenarios[] | select(.status == "PASS") | .mttrSeconds] | sort | if length > 0 then .[length/2|floor] else 0 end
    )
  ' "$file" >"$tmp" && mv "$tmp" "$file"
}

scorecard_print_summary() {
  local file="$1"
  jq -r '.summary | "Reliability score: \(.reliabilityScore)%  passed=\(.passed)/\(.total)  failed=\(.failed)  skipped=\(.skipped)  MTTR p50=\(.mttrSecondsP50)s"' "$file"
}

scorecard_upload() {
  local file="$1"
  local api="${API_HOST:-http://localhost:8080}"
  local token="${ADMIN_TOKEN:-}"
  [[ -f "$file" ]] || return 0
  [[ -n "$token" ]] || { log "ADMIN_TOKEN unset — skipping scorecard ingest"; return 0; }
  log "Uploading scorecard to $api/api/v1/admin/chaos/scorecard"
  curl -sf -X POST "$api/api/v1/admin/chaos/scorecard" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d @"$file" >/dev/null || err "Scorecard upload failed (non-fatal)"
}
