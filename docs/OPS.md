# Operations & repository split

## Monorepo today

```
parentalControlApp/
├── backend/          → parental-control-backend
├── parent-app/       → parental-control-parent-app
├── child-app/        → parental-control-child-app
└── docker-compose.yml
```

## Split into three repositories

```bash
./scripts/split-repos.sh /path/to/output-dir
```

Creates three folders with git history optional (subtree) and copies README templates from `docs/templates/`.

## CI/CD

| Workflow | Path |
|----------|------|
| Backend | `.github/workflows/backend-ci.yml` |
| Parent app | `.github/workflows/parent-app-ci.yml` |
| Child app | `.github/workflows/child-app-ci.yml` |

## Podman (full stack)

```bash
./scripts/dev/compose.sh up -d
# Postgres, Redis, MinIO, Kafka, API
```

Enable Kafka ingest: `KAFKA_ENABLED=true` (default in compose for backend).

## Kubernetes (Helm)

```bash
helm upgrade --install pcp-api ./helm/parental-control-api \
  --set image.tag=latest \
  --set env.JWT_SECRET=... \
  --set env.KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

## Production checklist

- [ ] `JWT_SECRET`, `APP_ENCRYPTION_KEY` (32+ chars), `HMAC_REQUIRED=true`
- [ ] `APP_ENCRYPTION_ENABLED=true` for HMAC keys at rest
- [ ] Stripe keys + webhook secret
- [ ] TURN server for WebRTC
- [ ] Certificate pins in child `network_security_config.xml`
- [ ] Managed Postgres + Redis + Kafka
- [ ] Prometheus scrape `/actuator/prometheus`

## OpenAPI export

```bash
./scripts/export-openapi.sh
# Writes backend/docs/openapi/openapi.json (API must be running)
```
