#!/usr/bin/env bash
# Print OCIDs and availability domains for scripts/deploy/oracle.env
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ORACLE_ENV_FILE:-$ROOT/scripts/deploy/oracle.env}"

if ! command -v oci >/dev/null 2>&1; then
  echo "Install OCI CLI first: https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/cliinstall.htm" >&2
  echo "  brew install oci-cli   # macOS" >&2
  echo "Then run: oci setup config" >&2
  exit 1
fi

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

TENANCY="${OCI_TENANCY_OCID:-}"
COMPARTMENT="${OCI_COMPARTMENT_OCID:-$TENANCY}"
REGION="${OCI_REGION:-ap-mumbai-1}"

if [[ -z "$TENANCY" ]]; then
  TENANCY="$(oci iam compartment list --all --compartment-id-in-subtree true \
    --query 'data[?name==`dm75323` || `"level"`==`0`].compartment-id | [0]' --raw-output 2>/dev/null || true)"
  if [[ -z "$TENANCY" || "$TENANCY" == "null" ]]; then
    echo "Set OCI_TENANCY_OCID in $ENV_FILE or pass via env." >&2
    echo "Find it: Console → Profile (top right) → Tenancy: ... → Copy OCID" >&2
    exit 1
  fi
fi

echo "=== Tenancy / compartment ==="
echo "OCI_TENANCY_OCID=$TENANCY"
echo "OCI_COMPARTMENT_OCID=${COMPARTMENT:-$TENANCY}"
echo "OCI_REGION=$REGION"
echo

echo "=== Availability domains (use in AVAILABILITY_DOMAINS) ==="
oci iam availability-domain list \
  --compartment-id "$TENANCY" \
  --query 'data[*].name' --output table
echo

echo "=== Public subnets (pick OCI_SUBNET_OCID — ProhibitPublicIpOnVnic=false) ==="
oci network subnet list \
  --compartment-id "${COMPARTMENT:-$TENANCY}" \
  --region "$REGION" \
  --query 'data[*].{name:"display-name",ocid:id,public:"prohibit-public-ip-on-vnic",cidr:"cidr-block"}' \
  --output table 2>/dev/null || echo "(No subnets yet — create a VCN with a public subnet in the console first.)"
echo

echo "=== Ubuntu 22.04 aarch64 images for A1.Flex ==="
oci compute image list \
  --compartment-id "${COMPARTMENT:-$TENANCY}" \
  --region "$REGION" \
  --operating-system "Canonical Ubuntu" \
  --operating-system-version "22.04" \
  --shape "VM.Standard.A1.Flex" \
  --sort-by TIMECREATED \
  --sort-order DESC \
  --query 'data[0:5].{name:"display-name",ocid:id,created:"time-created"}' \
  --output table
echo

echo "=== Reserved public IPs (optional OCI_RESERVED_PUBLIC_IP_OCID) ==="
oci network public-ip list \
  --compartment-id "${COMPARTMENT:-$TENANCY}" \
  --region "$REGION" \
  --scope RESERVED \
  --query 'data[*].{name:"display-name",ocid:id,address:"ip-address",state:"lifecycle-state"}' \
  --output table 2>/dev/null || true
echo

echo "Copy values into: cp scripts/deploy/oracle.env.example scripts/deploy/oracle.env"
