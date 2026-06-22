#!/usr/bin/env bash
# Local infra ONLY via Podman — never use the Docker CLI in this repo.
# Wraps docker-compose.yml: ./scripts/dev/compose.sh up -d postgres redis
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if ! command -v podman >/dev/null 2>&1; then
  echo "podman not found. Install Podman Desktop or: brew install podman" >&2
  exit 1
fi

if command -v docker >/dev/null 2>&1; then
  echo "Note: docker is installed but this project uses podman only. Use ./scripts/dev/compose.sh" >&2
fi

# macOS: ensure Podman is reachable before compose.
if [[ "$(uname -s)" == "Darwin" ]] && ! podman info >/dev/null 2>&1; then
  echo "Starting Podman machine..." >&2
  podman machine start >/dev/null 2>&1 || true
fi

exec podman compose "$@"
