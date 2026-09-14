#!/usr/bin/env bash
# Build parent web portal and publish to Oracle nginx at /portal/
# Usage (from laptop): ./scripts/deploy/oracle-deploy-web-portal.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SSH_KEY="${SSH_KEY:-$HOME/Downloads/ssh-key-2026-07-13.key}"
HOST="${ORACLE_HOST:-ubuntu@80.225.196.181}"
REMOTE_WWW="/var/www/portal"

echo "==> Building web (base=/portal/)"
cd "$ROOT/web"

# Prefer keys from web/.env; fall back to repo-root .env (Oracle copy).
# shellcheck disable=SC1091
if [[ -f "$ROOT/web/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ROOT/web/.env"
  set +a
fi
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ROOT/.env"
  set +a
  export VITE_API_PAYLOAD_ENCRYPTION_KEY="${VITE_API_PAYLOAD_ENCRYPTION_KEY:-${API_PAYLOAD_ENCRYPTION_KEY:-}}"
  export VITE_API_PAYLOAD_ENCRYPTION_IV="${VITE_API_PAYLOAD_ENCRYPTION_IV:-${API_PAYLOAD_ENCRYPTION_IV:-}}"
  export VITE_API_PAYLOAD_ENCRYPTION_ENABLED="${VITE_API_PAYLOAD_ENCRYPTION_ENABLED:-${API_PAYLOAD_ENCRYPTION_ENABLED:-false}}"
fi

# Default OFF so existing plaintext mobile apps keep working.
export VITE_API_PAYLOAD_ENCRYPTION_ENABLED="${VITE_API_PAYLOAD_ENCRYPTION_ENABLED:-false}"
if [[ "${VITE_API_PAYLOAD_ENCRYPTION_ENABLED}" == "true" ]]; then
  if [[ -z "${VITE_API_PAYLOAD_ENCRYPTION_KEY:-}" || -z "${VITE_API_PAYLOAD_ENCRYPTION_IV:-}" ]]; then
    echo "VITE_API_PAYLOAD_ENCRYPTION_ENABLED=true but KEY/IV missing — refusing build." >&2
    exit 1
  fi
fi

echo "    payload encryption: ${VITE_API_PAYLOAD_ENCRYPTION_ENABLED}"

# Same-origin API/WS on Oracle — leave API/WS empty.
VITE_BASE=/portal/ VITE_API_BASE_URL= VITE_WS_URL= \
  VITE_API_PAYLOAD_ENCRYPTION_ENABLED="$VITE_API_PAYLOAD_ENCRYPTION_ENABLED" \
  VITE_API_PAYLOAD_ENCRYPTION_KEY="${VITE_API_PAYLOAD_ENCRYPTION_KEY:-}" \
  VITE_API_PAYLOAD_ENCRYPTION_IV="${VITE_API_PAYLOAD_ENCRYPTION_IV:-}" \
  npm run build

echo "==> Syncing dist + nginx config to $HOST"
rsync -az --delete \
  -e "ssh -i $SSH_KEY -o BatchMode=yes" \
  "$ROOT/web/dist/" \
  "$HOST:/tmp/pcp-web-dist/"

scp -o BatchMode=yes -i "$SSH_KEY" \
  "$ROOT/scripts/deploy/nginx/pcp-site.conf" \
  "$HOST:/tmp/pcp-site.conf"

ssh -o BatchMode=yes -i "$SSH_KEY" "$HOST" bash -s <<EOF
set -euo pipefail
sudo mkdir -p $REMOTE_WWW /var/lib/pcp
sudo rsync -a --delete /tmp/pcp-web-dist/ $REMOTE_WWW/
sudo chown -R www-data:www-data $REMOTE_WWW
# Do not clobber Certbot HTTPS config — only install the HTTP template when no LE certs exist.
# live/ is root-only; must use sudo or the glob fails and we wipe HTTPS.
if sudo test -d /etc/letsencrypt/live && sudo bash -c 'compgen -G "/etc/letsencrypt/live/*/fullchain.pem" >/dev/null'; then
  echo "Keeping existing nginx site (Let's Encrypt certs present)"
elif sudo grep -qE 'listen[[:space:]]+443' /etc/nginx/sites-enabled/pcp 2>/dev/null; then
  echo "Keeping existing nginx site (listen 443 already configured)"
else
  sudo cp /tmp/pcp-site.conf /etc/nginx/sites-available/pcp
  sudo ln -sfn /etc/nginx/sites-available/pcp /etc/nginx/sites-enabled/pcp
  sudo rm -f /etc/nginx/sites-enabled/default
fi
if [[ ! -f /etc/nginx/pcp-backend-upstream.conf ]]; then
  active_port=8080
  if [[ -f /var/lib/pcp/backend-active-port ]]; then
    active_port=\$(tr -d '[:space:]' </var/lib/pcp/backend-active-port)
  fi
  echo "server 127.0.0.1:\${active_port};" | sudo tee /etc/nginx/pcp-backend-upstream.conf >/dev/null
fi
sudo nginx -t
sudo systemctl reload nginx
curl -sS -o /dev/null -w "portal=%{http_code}\n" http://127.0.0.1/portal/ || true
curl -sS -o /dev/null -w "health=%{http_code}\n" http://127.0.0.1/actuator/health || true
curl -sS -o /dev/null -w "https_portal=%{http_code}\n" https://parentcontrol.duckdns.org/portal/ || true
EOF

echo ""
echo "Public URLs:"
echo "  https://parentcontrol.duckdns.org/portal/"
echo "  http://80.225.196.181/portal/  (HTTP — camera/mic may be blocked)"
echo ""
echo "Login with the same parent account as the mobile app."
