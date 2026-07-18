#!/usr/bin/env bash
# Retry Oracle Cloud VM.Standard.A1.Flex launch across availability domains when
# "Out of capacity" is returned. Requires OCI CLI (oci) and scripts/deploy/oracle.env
#
# Usage:
#   cp scripts/deploy/oracle.env.example scripts/deploy/oracle.env
#   ./scripts/deploy/oracle-discover-ocids.sh    # fill oracle.env
#   ./scripts/deploy/oracle-retry-create-vm.sh
#
# Stop with Ctrl+C. Successful launch prints instance OCID and public IP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ORACLE_ENV_FILE:-$ROOT/scripts/deploy/oracle.env}"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }

if ! command -v oci >/dev/null 2>&1; then
  die "OCI CLI not found. Install: brew install oci-cli && oci setup config"
fi

[[ -f "$ENV_FILE" ]] || die "Missing $ENV_FILE — copy from oracle.env.example"

# shellcheck disable=SC1090
source "$ENV_FILE"

: "${OCI_COMPARTMENT_OCID:?Set OCI_COMPARTMENT_OCID in oracle.env}"
: "${OCI_SUBNET_OCID:?Set OCI_SUBNET_OCID in oracle.env}"
: "${OCI_IMAGE_OCID:?Set OCI_IMAGE_OCID in oracle.env}"
: "${SSH_PUBLIC_KEY_FILE:?Set SSH_PUBLIC_KEY_FILE in oracle.env}"

[[ -f "$SSH_PUBLIC_KEY_FILE" ]] || die "SSH public key not found: $SSH_PUBLIC_KEY_FILE"

INSTANCE_DISPLAY_NAME="${INSTANCE_DISPLAY_NAME:-pcp-server}"
SHAPE="${SHAPE:-VM.Standard.A1.Flex}"
OCPUS="${OCPUS:-1}"
MEMORY_GB="${MEMORY_GB:-6}"
BOOT_VOLUME_GB="${BOOT_VOLUME_GB:-50}"
RETRY_INTERVAL_SECONDS="${RETRY_INTERVAL_SECONDS:-120}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-0}"
OCI_REGION="${OCI_REGION:-}"
AVAILABILITY_DOMAINS="${AVAILABILITY_DOMAINS:-AD-1,AD-2,AD-3}"

REGION_ARGS=()
[[ -n "$OCI_REGION" ]] && REGION_ARGS=(--region "$OCI_REGION")

IFS=',' read -r -a AD_LIST <<< "${AVAILABILITY_DOMAINS// /}"

SHAPE_CONFIG="{\"ocpus\": ${OCPUS}, \"memoryInGBs\": ${MEMORY_GB}}"

attempt=0
ad_index=0

log "Shape: ${SHAPE} (${OCPUS} OCPU, ${MEMORY_GB} GB) — rotating ADs: ${AVAILABILITY_DOMAINS}"
log "Retry interval: ${RETRY_INTERVAL_SECONDS}s | MAX_ATTEMPTS=${MAX_ATTEMPTS:-unlimited}"

while true; do
  attempt=$((attempt + 1))
  if [[ "$MAX_ATTEMPTS" -gt 0 && "$attempt" -gt "$MAX_ATTEMPTS" ]]; then
    die "Reached MAX_ATTEMPTS=$MAX_ATTEMPTS without success"
  fi

  ad="${AD_LIST[$ad_index]}"
  ad_index=$(( (ad_index + 1) % ${#AD_LIST[@]} ))

  log "Attempt #${attempt} — availability domain ${ad}"

  err_file="$(mktemp)"
  set +e
  instance_ocid="$(oci compute instance launch \
    "${REGION_ARGS[@]}" \
    --availability-domain "$ad" \
    --compartment-id "$OCI_COMPARTMENT_OCID" \
    --display-name "$INSTANCE_DISPLAY_NAME" \
    --shape "$SHAPE" \
    --shape-config "$SHAPE_CONFIG" \
    --subnet-id "$OCI_SUBNET_OCID" \
    --image-id "$OCI_IMAGE_OCID" \
    --assign-public-ip true \
    --ssh-authorized-keys-file "$SSH_PUBLIC_KEY_FILE" \
    --boot-volume-size-in-gbs "$BOOT_VOLUME_GB" \
    --query 'data.id' --raw-output 2>"$err_file")"
  launch_status=$?
  err_msg="$(cat "$err_file")"
  rm -f "$err_file"
  set -e

  if [[ $launch_status -eq 0 && -n "$instance_ocid" && "$instance_ocid" != "null" ]]; then
    log "SUCCESS — instance created in ${ad}"
    log "Instance OCID: $instance_ocid"
    log "Waiting for RUNNING state..."

    oci compute instance get \
      "${REGION_ARGS[@]}" \
      --instance-id "$instance_ocid" \
      --wait-for-state RUNNING \
      --max-wait-seconds 900 \
      --wait-interval-seconds 15 \
      --query 'data.{name:"display-name",state:"lifecycle-state",ad:"availability-domain"}' \
      --output table

    vnic_attachment="$(oci compute vnic-attachment list \
      "${REGION_ARGS[@]}" \
      --compartment-id "$OCI_COMPARTMENT_OCID" \
      --instance-id "$instance_ocid" \
      --query 'data[0]."vnic-id"' --raw-output)"

    public_ip="$(oci network vnic get \
      "${REGION_ARGS[@]}" \
      --vnic-id "$vnic_attachment" \
      --query 'data."public-ip"' --raw-output)"

    private_ip_id="$(oci network vnic get \
      "${REGION_ARGS[@]}" \
      --vnic-id "$vnic_attachment" \
      --query 'data."private-ip-id"' --raw-output)"

    log "Public IP (ephemeral): ${public_ip:-none}"

    if [[ -n "${OCI_RESERVED_PUBLIC_IP_OCID:-}" ]]; then
      log "Attaching reserved public IP ${OCI_RESERVED_PUBLIC_IP_OCID}..."
      oci network public-ip update \
        "${REGION_ARGS[@]}" \
        --public-ip-id "$OCI_RESERVED_PUBLIC_IP_OCID" \
        --private-ip-id "$private_ip_id" \
        --wait-for-state ASSIGNED \
        --max-wait-seconds 300 \
        --wait-interval-seconds 10

      reserved_ip="$(oci network public-ip get \
        "${REGION_ARGS[@]}" \
        --public-ip-id "$OCI_RESERVED_PUBLIC_IP_OCID" \
        --query 'data."ip-address"' --raw-output)"
      log "Reserved public IP: $reserved_ip"
      log "SSH: ssh -i <private-key> ubuntu@${reserved_ip}"
    else
      log "SSH: ssh -i <private-key> ubuntu@${public_ip}"
    fi

    log "Next: open Security List ports (22,80,443,3478,49160-49200 UDP) then deploy the app."
    exit 0
  fi

  if printf '%s' "$err_msg" | grep -qi 'out of capacity\|out of host capacity'; then
    log "Out of capacity in ${ad} — next AD in ${RETRY_INTERVAL_SECONDS}s"
  else
    log "Launch failed (not capacity):"
    printf '%s\n' "$err_msg" >&2
    exit 1
  fi

  sleep "$RETRY_INTERVAL_SECONDS"
done
