# Operational Runbooks

## Command delivery degraded (`CommandDeliverySLOBurnRate`)

1. Check Grafana **Command Throughput** panel — sent vs executed vs failed.
2. Query Prometheus: `topk(10, sum by (command_type, reason) (rate(command_failure_total[15m])))`
3. Inspect backend logs: `correlationId` + `commandId` in JSON logs.
4. Verify STOMP cluster: `stomp.cluster.fanout.total` and Redis connectivity.
5. Check child FCM wake: `FCM_ENABLED` and device `last_seen_at`.
6. If dead-letter spike: review child `CommandDurabilityTelemetry` via mobile ingest metrics.

## Stream startup failures (`StreamStartupSLOBurnRate`)

1. Panel **Stream Start / Failure** and **ICE Failures**.
2. Verify coturn: `turn.credentials.issued` and TURN secret sync.
3. Check MediaProjection state: `device.media_projection.granted.count`.
4. Trace session: filter logs by `sessionId` MDC field.
5. Review `webrtc.ice.failures` by `stream` tag.

## Stale heartbeats (`DeviceHeartbeatStale`)

1. Gauge `device_heartbeat_freshness_max_lag_seconds`.
2. Identify offline devices in parent app / DB `devices.last_seen_at`.
3. Child side: battery optimization, Doze, OEM kill switches.
4. Verify heartbeat HMAC + trust interceptor not blocking.

## HMAC attack spike (`HmacFailureSpike`)

1. `security.hmac.failures.total` by reason.
2. Check for replay: Redis nonce store health.
3. Rotate affected device HMAC via re-pair if theft suspected.

## API 5xx (`APIHighErrorRate`)

1. Spring Actuator health: `/actuator/health/readiness`
2. Postgres/Redis/Kafka connectivity.
3. Multi-pod: confirm `STOMP_CLUSTER_MODE=redis`.
4. Correlate traces in OTLP collector logs (dev) or Jaeger (prod).

## PagerDuty escalation

- **P1 (critical):** Command delivery SLO burn, API 5xx, HMAC spike → primary on-call
- **P2 (warning):** Stream ICE spike, heartbeat stale, STOMP disconnects → secondary / Slack

Set `PAGERDUTY_ROUTING_KEY` and `SLACK_WEBHOOK_URL` in Alertmanager config.
