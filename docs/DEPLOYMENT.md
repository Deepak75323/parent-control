# Deployment Guide

## Local development

```bash
./scripts/dev/compose.sh up -d
```

Starts PostgreSQL, Redis, MinIO, and the backend API on port 8080 (via Podman).

## Production checklist

1. Set strong `JWT_SECRET` (256+ bits).
2. Use managed PostgreSQL (RDS, Cloud SQL).
3. Redis cluster for sessions and rate limiting.
4. TLS termination at load balancer.
5. Object storage (S3 / MinIO) for media streams.
6. FCM credentials for push notifications.
7. Horizontal pod autoscaling for backend (K8s).

## Backend container image

```bash
podman build -t parental-control-api ./backend
podman run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/parental_control \
  -e JWT_SECRET=your-secret \
  parental-control-api
```

## Mobile apps

Build release APK/AAB separately:

```bash
cd parent-app && cd android && ./gradlew assembleRelease
cd child-app && cd android && ./gradlew assembleRelease
```

Distribute via Play Store or MDM for enterprise Device Owner provisioning.

## CI

GitHub Actions runs `mvn verify` in `backend/` on push to `main` / `develop`.
