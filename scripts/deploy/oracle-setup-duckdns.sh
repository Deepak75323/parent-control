#!/usr/bin/env bash
# Point a DuckDNS hostname at the Oracle VM and keep it updated.
#
# One-time (browser — ~1 minute):
#   1. Open https://www.duckdns.org and sign in (GitHub is fine)
#   2. Create subdomain, e.g. parentcontrol  →  parentcontrol.duckdns.org
#   3. Copy your token from the top of the page
#
# Then:
#   DUCKDNS_DOMAIN=parentcontrol DUCKDNS_TOKEN=xxxxxxxx \
#     ./scripts/deploy/oracle-setup-duckdns.sh
set -euo pipefail

SSH_KEY="${SSH_KEY:-$HOME/Downloads/ssh-key-2026-07-13.key}"
HOST="${ORACLE_HOST:-ubuntu@80.225.196.181}"
ORACLE_IP="${ORACLE_IP:-80.225.196.181}"
DOMAIN="${DUCKDNS_DOMAIN:-}"
TOKEN="${DUCKDNS_TOKEN:-}"

if [[ -z "$DOMAIN" || -z "$TOKEN" ]]; then
  echo "Usage: DUCKDNS_DOMAIN=parentcontrol DUCKDNS_TOKEN=<token> $0" >&2
  echo "Create the subdomain + token at https://www.duckdns.org first." >&2
  exit 1
fi

DOMAIN="${DOMAIN%.duckdns.org}"
FQDN="${DOMAIN}.duckdns.org"
PORTAL_URL="http://${FQDN}/portal/"
ORIGIN="http://${FQDN}"

# Basic token hygiene (DuckDNS tokens are UUIDs)
if [[ ! "$TOKEN" =~ ^[0-9a-fA-F-]{36}$ ]]; then
  echo "Warning: token does not look like a DuckDNS UUID — continuing anyway." >&2
fi

echo "==> Updating DuckDNS ${FQDN} → ${ORACLE_IP}"
RESP="$(curl -fsS "https://www.duckdns.org/update?domains=${DOMAIN}&token=${TOKEN}&ip=${ORACLE_IP}")"
echo "    duckdns response: ${RESP}"
if [[ "$RESP" != "OK" ]]; then
  echo "DuckDNS update failed (expected OK)." >&2
  exit 1
fi

echo "==> Installing updater + nginx host on ${HOST}"
ssh -o BatchMode=yes -i "$SSH_KEY" "$HOST" bash -s -- "$DOMAIN" "$TOKEN" "$ORACLE_IP" "$FQDN" "$ORIGIN" <<'REMOTE'
set -euo pipefail
DOMAIN="$1"
TOKEN="$2"
ORACLE_IP="$3"
FQDN="$4"
ORIGIN="$5"

sudo mkdir -p /opt/pcp
sudo tee /opt/pcp/duckdns-update.sh >/dev/null <<SCRIPT
#!/usr/bin/env bash
curl -fsS "https://www.duckdns.org/update?domains=${DOMAIN}&token=${TOKEN}&ip=${ORACLE_IP}" \\
  -o /tmp/duckdns.log || true
SCRIPT
sudo chmod 700 /opt/pcp/duckdns-update.sh
sudo /opt/pcp/duckdns-update.sh
echo -n "    remote update: "; cat /tmp/duckdns.log; echo

echo "*/5 * * * * root /opt/pcp/duckdns-update.sh" | sudo tee /etc/cron.d/pcp-duckdns >/dev/null
sudo chmod 644 /etc/cron.d/pcp-duckdns

if [[ -f /etc/nginx/sites-available/pcp ]]; then
  sudo sed -i "s/server_name .*/server_name ${FQDN} ${ORACLE_IP} _;/" /etc/nginx/sites-available/pcp
  sudo nginx -t
  sudo systemctl reload nginx
fi

ENV=/home/ubuntu/parentalControlApp/.env
if [[ -f "$ENV" ]]; then
  for key in CORS_ALLOWED_ORIGINS WEBSOCKET_ALLOWED_ORIGINS; do
    if grep -q "^${key}=" "$ENV" && ! grep -F "${ORIGIN}" "$ENV" >/dev/null; then
      cur="$(grep "^${key}=" "$ENV" | head -1 | cut -d= -f2-)"
      sed -i "s|^${key}=.*|${key}=${cur},${ORIGIN}|" "$ENV"
    fi
  done
fi

echo "DuckDNS host configured for ${FQDN}"
REMOTE

echo "==> Resolving ${FQDN}"
for i in 1 2 3 4 5 6; do
  HIT="$(dig +short "$FQDN" A 2>/dev/null | head -1 || true)"
  echo "    attempt $i: ${HIT:-"(none yet)"}"
  if [[ "$HIT" == "$ORACLE_IP" ]]; then
    break
  fi
  sleep 5
done

curl -sS -o /dev/null -w "portal=%{http_code}\n" "${PORTAL_URL}" || true

echo ""
echo "Public URL:"
echo "  ${PORTAL_URL}login"
