# Service Level Objectives — Parental Control Platform

## Command delivery
- **SLI:** `sum(rate(command_executed_total{success="true"}[30d])) / sum(rate(command_sent_total[30d]))`
- **SLO:** 99.5% delivered and acknowledged within 120s
- **Error budget:** 0.5% monthly (~3.6h of bad minutes at steady load)

## Stream startup success
- **SLI:** `1 - (sum(rate(stream_failure_total[30d])) / sum(rate(stream_start_total[30d])))`
- **SLO:** 95% of stream sessions reach ACTIVE within 30s
- **Measurement:** `stream.start.latency.seconds` p95 < 30s

## Device availability
- **SLI:** fraction of fleet with `device.heartbeat.freshness.max_lag_seconds` < 180
- **SLO:** 99% of paired devices heartbeat within 3 minutes

## API availability
- **SLI:** `1 - (5xx / total)` from `http_server_requests_seconds_count`
- **SLO:** 99.9% monthly

## Boot / process recovery
- **SLI:** `sum(rate(device_boot_recovery_total{success="true"}[30d])) / sum(rate(device_boot_recovery_total[30d]))`
- **SLO:** 98% successful recovery within 5 minutes of boot

## Burn-rate alerts
- **Fast burn (critical):** 14.4× budget consumption in 1h → page on-call
- **Slow burn (warning):** 6× budget consumption in 6h → Slack `#pcp-alerts`

Prometheus rules in `infra/observability/prometheus/alerts.yml` implement simplified 5m/10m burn detectors.
