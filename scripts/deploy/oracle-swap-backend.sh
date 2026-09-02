#!/usr/bin/env bash
# Blue/green backend swap on the Oracle VM.
# Run ON the VM (or via oracle-deploy-backend.sh from your laptop).
#
# 1. Starts pcp-backend-candidate on the inactive host port (8080 or 8081).
# 2. Waits for /actuator/health on that port (Flyway + startup must succeed).
# 3. Points nginx upstream at the candidate — old backend keeps running until this step.
# 4. Stops/removes the old pcp-backend container.
# 5. Renames candidate → pcp-backend.
#
# If the candidate never becomes healthy, the running backend is untouched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IMAGE="${BACKEND_IMAGE:-localhost/parentalcontrolapp_backend:latest}"
NETWORK="${PODMAN_NETWORK:-parentalcontrolapp_default}"
NAME=pcp-backend
CANDIDATE=pcp-backend-candidate
ACTIVE_PORT_FILE="${ACTIVE_PORT_FILE:-/var/lib/pcp/backend-active-port}"
UPSTREAM_CONF="${UPSTREAM_CONF:-/etc/nginx/pcp-backend-upstream.conf}"
ENV_SNAPSHOT="${ENV_SNAPSHOT:-/tmp/pcp-backend-env.txt}"
SA_HOST="${SA_HOST_PATH:-$ROOT/backend/secrets/firebase-service-account.json}"
CHAT_ATTACHMENTS_HOST="${CHAT_ATTACHMENTS_HOST:-/var/lib/pcp/chat-attachments}"
CHAT_ATTACHMENTS_CONTAINER="${CHAT_ATTACHMENTS_CONTAINER:-/app/data/chat-attachments}"
HEALTH_PATH=/actuator/health
HEALTH_RETRIES="${HEALTH_RETRIES:-80}"
HEALTH_SLEEP="${HEALTH_SLEEP:-3}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

read_active_port() {
  if [[ -f "$ACTIVE_PORT_FILE" ]]; then
    tr -d '[:space:]' <"$ACTIVE_PORT_FILE"
  else
    echo 8080
  fi
}

candidate_port_for() {
  local active="$1"
  if [[ "$active" == "8080" ]]; then
    echo 8081
  else
    echo 8080
  fi
}

capture_env_from_container() {
  local container="$1"
  podman inspect "$container" --format '{{range .Config.Env}}{{println .}}{{end}}' >"$ENV_SNAPSHOT"
}

env_run_args() {
  local line
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    [[ "$line" == PATH=* ]] && continue
    [[ "$line" == JAVA_* ]] && continue
    [[ "$line" == LANG=* ]] && continue
    [[ "$line" == LC_* ]] && continue
    [[ "$line" == HOME=* ]] && continue
    [[ "$line" == HOSTNAME=* ]] && continue
    printf '%s\0' "$line"
  done <"$ENV_SNAPSHOT"
}

wait_for_health() {
  local port="$1"
  local label="$2"
  local i
  for ((i = 1; i <= HEALTH_RETRIES; i++)); do
    if curl -sf "http://127.0.0.1:${port}${HEALTH_PATH}" >/dev/null 2>&1; then
      echo "==> ${label} healthy on port ${port}"
      curl -sS "http://127.0.0.1:${port}${HEALTH_PATH}"
      echo
      return 0
    fi
    if [[ "$label" == "candidate" ]] &&
      ! podman inspect "$CANDIDATE" --format '{{.State.Running}}' 2>/dev/null | grep -qx true; then
      echo "Candidate container exited before becoming healthy:" >&2
      podman logs --tail 100 "$CANDIDATE" >&2 || true
      return 1
    fi
    sleep "$HEALTH_SLEEP"
  done
  echo "Timed out waiting for ${label} health on port ${port}" >&2
  if [[ "$label" == "candidate" ]]; then
    podman logs --tail 100 "$CANDIDATE" >&2 || true
  fi
  return 1
}

write_upstream() {
  local port="$1"
  echo "server 127.0.0.1:${port};" | sudo tee "$UPSTREAM_CONF" >/dev/null
  sudo nginx -t
  sudo systemctl reload nginx
}

cleanup_candidate() {
  podman rm -f "$CANDIDATE" 2>/dev/null || true
}

main() {
  require_cmd podman
  require_cmd curl
  require_cmd sudo

  if ! podman image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "Image not found: $IMAGE" >&2
    exit 1
  fi

  if ! podman network exists "$NETWORK"; then
    echo "Podman network not found: $NETWORK" >&2
    exit 1
  fi

  sudo mkdir -p "$(dirname "$ACTIVE_PORT_FILE")"
  sudo mkdir -p "$(dirname "$UPSTREAM_CONF")"

  local active_port candidate_port
  active_port="$(read_active_port)"
  candidate_port="$(candidate_port_for "$active_port")"

  echo "==> Blue/green swap"
  echo "    image=$IMAGE"
  echo "    network=$NETWORK (fixed — never inferred from old container)"
  echo "    active_port=$active_port candidate_port=$candidate_port"

  if podman container exists "$NAME" 2>/dev/null; then
    capture_env_from_container "$NAME"
  elif [[ ! -s "$ENV_SNAPSHOT" ]]; then
    echo "No running $NAME and no env snapshot at $ENV_SNAPSHOT" >&2
    exit 1
  fi

  echo "==> Env snapshot: $(wc -l <"$ENV_SNAPSHOT" | tr -d ' ') vars"

  cleanup_candidate

  local -a run_args=()
  local env_line
  while IFS= read -r -d '' env_line; do
    run_args+=(-e "$env_line")
  done < <(env_run_args)

  local -a mount_args=()
  if [[ -f "$SA_HOST" ]]; then
    # Container runs as spring (uid 100); host-owned 600 files bind-mount unreadable.
    chmod 644 "$SA_HOST"
    mount_args=(-v "$SA_HOST:/app/secrets/firebase-service-account.json:ro")
  fi
  sudo mkdir -p "$CHAT_ATTACHMENTS_HOST"
  # JRE image runs as spring (uid 1000).
  sudo chown -R 1000:1000 "$CHAT_ATTACHMENTS_HOST"
  mount_args+=(-v "${CHAT_ATTACHMENTS_HOST}:${CHAT_ATTACHMENTS_CONTAINER}")
  run_args+=(-e "CHAT_ATTACHMENTS_PATH=${CHAT_ATTACHMENTS_CONTAINER}")

  echo "==> Starting $CANDIDATE on host port $candidate_port"
  # shellcheck disable=SC2068
  podman run -d --name "$CANDIDATE" --network "$NETWORK" \
    -p "${candidate_port}:8080" \
    ${run_args[@]} \
    ${mount_args[@]:-} \
    "$IMAGE"

  if ! wait_for_health "$candidate_port" "candidate"; then
    echo "==> Deploy aborted — $NAME on port $active_port is unchanged" >&2
    cleanup_candidate
    exit 1
  fi

  echo "==> Switching nginx upstream to port $candidate_port"
  if [[ ! -f "$UPSTREAM_CONF" ]]; then
    echo "server 127.0.0.1:${active_port};" | sudo tee "$UPSTREAM_CONF" >/dev/null
  fi
  write_upstream "$candidate_port"

  if podman container exists "$NAME" 2>/dev/null; then
    echo "==> Stopping previous $NAME (port $active_port)"
    podman stop "$NAME" >/dev/null
    podman rm "$NAME" >/dev/null
  fi

  echo "==> Promoting $CANDIDATE → $NAME"
  podman rename "$CANDIDATE" "$NAME"
  echo "$candidate_port" | sudo tee "$ACTIVE_PORT_FILE" >/dev/null

  echo "==> Swap complete — live backend on port $candidate_port"
  curl -sS -o /dev/null -w "public_health=%{http_code}\n" "http://127.0.0.1${HEALTH_PATH}" || true
}

main "$@"
