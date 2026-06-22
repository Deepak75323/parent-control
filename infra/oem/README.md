# OEM Certification Program (P1-10)

Multi-OEM device certification for Parental Control Platform.

## Quick start

```bash
# Auto-detect OEM from connected device
./scripts/validation/oem/run-certification.sh --oem auto --serial <adb>

# Xiaomi full suite (16 tests)
./scripts/validation/oem/run-certification.sh --oem xiaomi

# Chaos / nightly OEM matrix
./scripts/chaos/run-chaos-suite.sh --suite oem-matrix --device-serial <adb>
```

## Docs

- **Full program (10 sections):** [`docs/OEM-CERTIFICATION-PROGRAM.md`](../../docs/OEM-CERTIFICATION-PROGRAM.md)
- **Behavior matrix:** [`behavior-matrix.yaml`](behavior-matrix.yaml)
- **Xiaomi deep suite:** [`../validation/xiaomi/README.md`](../validation/xiaomi/README.md)

## Backend ingest

```bash
curl -X POST http://localhost:8080/api/v1/admin/certification/report \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d @reports/oem-certification.json
```

## Dashboard

`infra/observability/grafana/dashboards/pcp-oem-reliability.json`
