# Final Security Certification — Post-Remediation Re-Audit

**Audit date:** 2026-06-18  
**Baseline:** [SECURITY-ADVERSARIAL-REVIEW.md](./SECURITY-ADVERSARIAL-REVIEW.md) (score 66/100)  
**Remediation:** [SECURITY-REMEDIATION-REPORT.md](./SECURITY-REMEDIATION-REPORT.md) (C1–C8)  
**Methodology:** Full codebase re-audit with code-path verification only — no findings without file/line evidence.

---

## Executive Summary

| Metric | Pre-remediation | Post-remediation |
|--------|-----------------|------------------|
| **Security readiness score** | 66/100 | **79/100** |
| **Critical findings (open)** | 2 (C1, C2) + 6 High | **0 Critical** |
| **High findings (open)** | 6 (C3–C8) | **3 High** |
| **Certification status** | Not ready | **Conditional pass** |

**Conditional pass** means: core P0 remediations (C1–C8) are implemented in source, but production certification requires operator actions (TLS cert Secret for coturn, SPKI pin injection, secret population) and closure of three remaining High findings documented below.

---

## 1. Threat Model (Re-run)

### 1.1 Assets (unchanged sensitivity)

| Asset | Sensitivity | Post-remediation control |
|-------|-------------|--------------------------|
| Parent JWT + refresh | Critical | Android: `ParentSecureAuthStore.kt` (EncryptedSharedPreferences) |
| Device JWT + HMAC key | Critical | `SecureCredentialStore.kt` (unchanged, strong) |
| K8s signing secrets | Critical | `deployment.yaml` `secretKeyRef` via `secretEnv` |
| WebRTC signaling | Critical | HMAC on child POST (`WebMvcConfig.java:38-43`) |
| STOMP topics | High | Deny-by-default (`StompAuthChannelInterceptor.java:57-60`) |
| TURN relay | High | TLS scaffold (`coturn.yaml:73-75`); UDP 3478 still exposed |
| Pairing codes | High | Rate limit wired (`WebMvcConfig.java:36-37`); **still logged** |

### 1.2 Threat actors & residual exposure

| Actor | Primary residual path | Evidence |
|-------|----------------------|----------|
| Malware on parent (Android) | In-memory JWT extraction | Tokens at rest encrypted (`ParentSecureAuthStore.kt:40-42`) |
| Network MITM | Cleartext TURN UDP 3478; pins not injected | `coturn.yaml:69` port 3478; `network_security_config.xml:12` placeholder pin |
| Pairing attacker | Log aggregation harvest | `DeviceService.java:104-105,119-121` |
| STOMP subscriber | Unknown topics | **Mitigated** — forbidden destination thrown |
| Infra insider | etcd Secret RBAC | Plain env removed from API Deployment |
| Motivated child (root) | Trust signal spoof, a11y kill | Unchanged — design residual |

### 1.3 STRIDE delta

| Threat | Pre | Post | Delta |
|--------|-----|------|-------|
| Spoofing (device signaling) | High | Low | HMAC registered on signaling |
| Tampering (SDP injection) | High | Low | Same |
| Information disclosure (K8s secrets) | Critical | Low | `secretKeyRef` pattern |
| Information disclosure (parent tokens) | Critical | Medium | Android encrypted; memory residual |
| Denial of service (pairing flood) | High | Low–Med | Rate limit on `/device/pair` |
| Elevation (STOMP topics) | High | Low | Deny-by-default |

### 1.4 Attack tree status

| Scenario | Pre | Post |
|----------|-----|------|
| ES-1 Parent AsyncStorage theft | **Open** | **Closed** (Android) |
| ES-2 LAN MITM pairing | **Open** | **Partial** (release cleartext off; pins placeholder) |
| ES-3 Pairing brute force | **Open** | **Mitigated** (rate limit) |
| ES-5 STOMP enumeration | **Open** | **Closed** |
| ES-6 Signaling without HMAC | **Open** | **Closed** |
| ES-8 K8s secret exfil from Pod spec | **Open** | **Closed** (sensitive keys in Secret) |

---

## 2. OWASP Mobile Top 10 (2024)

| ID | Risk | Status | Code evidence |
|----|------|--------|---------------|
| **M1** | Improper credential usage | **Partial Pass** | Parent Android: `ParentSecureAuthStore.kt:28-34` (AES-256-GCM). iOS fallback path: `authStorage.ts:57` (AsyncStorage) — parent app has no first-party `ios/` project; Android-only shipping surface. |
| **M2** | Inadequate supply chain | **Partial Pass** | `ci.yml:14-15` gitleaks; `ci.yml:31-32` OWASP dependency-check (continue-on-error). |
| **M3** | Insecure auth/authorization | **Pass** (child) / **Partial** (parent) | Child `SecureCredentialStore.kt:27-32`; parent secure bridge `parentSecureAuthBridge.ts:10-18`. |
| **M4** | Insufficient input validation | **Pass** | Jakarta `@Valid` on DTOs (unchanged). |
| **M5** | Insecure communication | **Partial Fail** | Parent release pin placeholder: `parent/.../release/.../network_security_config.xml:11-12`. Debug cleartext: `parent/.../debug/AndroidManifest.xml:7`. Child release same placeholder: `child/.../release/.../network_security_config.xml:11-12`. |
| **M6** | Inadequate privacy controls | **Partial** | Pairing codes in logs (`DeviceService.java:104-105`). |
| **M7** | Insufficient binary protections | **Fail** | Broad ProGuard keep: `child/android/app/proguard-rules.pro:9-15` (`-keep class com.childmonitor.** { *; }`). |
| **M8** | Security misconfiguration | **Partial Pass** | Prod boot gate: `ProductionSecurityValidator.java:39-90`. Helm secrets split: `values.yaml:110-116`. Swagger still public: `SecurityConfig.java:30-33`. |
| **M9** | Insecure data storage | **Pass** (child) / **Pass** (parent Android) | Child encrypted creds; parent `ParentSecureAuthStore.kt`. Integrity token cache still plain: `MonitoringPrefs.kt:201-215`. |
| **M10** | Insufficient cryptography | **Partial Pass** | HMAC + encrypted prefs strong; transport pinning not operational until CI injects SPKI. |

**Mobile summary:** 4 Pass / 5 Partial / 1 Fail → improved from 2 Pass / 4 Partial / 4 Fail.

---

## 3. OWASP API Top 10 (2023)

| ID | Risk | Status | Code evidence |
|----|------|--------|---------------|
| **API1** | Broken object level auth | **Pass** | `DeviceAccessService.java:24-35` ownership checks. |
| **API2** | Broken authentication | **Partial** | Refresh JWT allowed outside monitoring paths: `JwtAuthenticationFilter.java:88-91` only gates `isParentMonitoringPath()` — excludes `/api/v1/billing/**`, `/api/v1/alerts/**`, `/api/v1/audit/**`. Pairing rate limit: `WebMvcConfig.java:36-37`. |
| **API3** | Broken object property level auth | **Pass** | Unchanged. |
| **API4** | Unrestricted resource consumption | **Partial** | Pairing + auth rate limits wired. TURN TTL 24h: `values.yaml:108`. Redis down → limit bypass: `RateLimitService.java:51-53`. |
| **API5** | Broken function level auth | **Pass** | STOMP deny-by-default: `StompAuthChannelInterceptor.java:57-60`. Role checks: `SecurityConfig.java:49-75`. |
| **API6** | Unrestricted access to sensitive flow | **Partial** | Pairing public by design (`SecurityConfig.java:28`); rate limited. **Pairing codes in logs** (`DeviceService.java:104-105`). |
| **API7** | SSRF | **Pass** | No user-controlled URL fetch (unchanged). |
| **API8** | Security misconfiguration | **Partial Pass** | Secrets via `secretKeyRef` (`deployment.yaml:26-32`). Ingress no TLS block: `ingress.yaml:16-30`. Swagger public in prod config. |
| **API9** | Improper inventory management | **Partial** | OpenAPI exposed (`SecurityConfig.java:30-33`). |
| **API10** | Unsafe consumption of APIs | **Pass** | Stripe webhook verifies when secret set: `StripeWebhookService.java:111-140`. Razorpay stub unsigned: `BillingController.java:64-68`. |

**API summary:** 5 Pass / 5 Partial / 0 Fail → improved from 3 Pass / 5 Partial / 2 Fail.

---

## 4. MASVS Assessment

### MSTG-STORAGE

| Control | L1 | Evidence |
|---------|-----|----------|
| Sensitive data not in plaintext | **Pass** (Android) | `ParentSecureAuthStore.kt`, `SecureCredentialStore.kt` |
| No sensitive data in logs | **Fail** | `DeviceService.java:104-105` logs pairing code |
| Integrity token cache | **Partial** | `MonitoringPrefs.kt:202-215` uses `MODE_PRIVATE` not encrypted |

### MSTG-NETWORK

| Control | L1 | Evidence |
|---------|-----|----------|
| TLS for all traffic | **Partial** | Base cleartext false: `parent/.../main/.../network_security_config.xml:2-7`. Debug exception: `debug/AndroidManifest.xml:7`. |
| Certificate pinning | **Fail** (operational) | Placeholder: `release/.../network_security_config.xml:12` |

### MSTG-PLATFORM

| Control | L1 | Evidence |
|---------|-----|----------|
| `allowBackup=false` | **Pass** | `parent/.../AndroidManifest.xml:10`; child manifest line 32 |
| Debug-only dangerous receivers | **Pass** | `DebugPairingReceiver.kt:23-25` `BuildConfig.DEBUG` guard |

### MSTG-AUTH

| Control | L1 | Evidence |
|---------|-----|----------|
| Device HMAC on mutations | **Pass** | `DeviceSecurityInterceptor.java:47-53`; signaling path registered |
| Refresh token scope | **Partial** | `JwtAuthenticationFilter.java:88-91` |

### MASVS level readiness

| Level | Pre | Post |
|-------|-----|------|
| **L1** | ~75% | **~86%** |
| **L2** | ~55% | **~62%** |
| **R** (resiliency) | ~60% | **~65%** |

**L1 blockers:** MSTG-NETWORK pinning (operational), MSTG-STORAGE log hygiene.

---

## 5. Infrastructure Security Review

### 5.1 Kubernetes / Helm

| Control | Status | Evidence |
|---------|--------|----------|
| API secrets not in plain env | **Pass** | `deployment.yaml:26-32` |
| Secret template | **Pass** | `api-secret.yaml:1-13` |
| NetworkPolicy (API) | **Pass** | `networkpolicy.yaml:1-39` |
| NetworkPolicy (TURN) | **Pass** | `networkpolicy.yaml:40-73` |
| Coturn auth via Secret | **Pass** | `coturn.yaml:85-89` |
| Coturn TLS cert mount | **Fail** (config) | `tls.enabled: true` but `certSecret: ""` (`values.yaml:64-68`); volume only when certSecret set (`coturn.yaml:90-94`) |
| Default weak chart secrets | **Partial** | `values.yaml:59` `authSecret: pcp_turn_secret_change_in_prod` |
| Ingress TLS | **Not configured** | `ingress.yaml` — no `tls:` stanza |
| RabbitMQ default password | **Partial** | `values.yaml:38-39` `password: pcp_secret` (when enabled) |

### 5.2 Backend production gates

| Control | Status | Evidence |
|---------|--------|----------|
| JWT secret default blocked | **Pass** | `ProductionSecurityValidator.java:39-42` |
| HMAC required in prod | **Pass** | `ProductionSecurityValidator.java:43-46` |
| Play Integrity + trust coupling | **Pass** | `ProductionSecurityValidator.java:87-90` |
| STOMP cluster mode required | **Pass** | `ProductionSecurityValidator.java:63-68` |

### 5.3 Observability exposure

| Control | Status | Evidence |
|---------|--------|----------|
| Prometheus auth | **Pass** (default) | `MetricsScrapeAuthFilter.java:37-50` — 403 without token; `prometheus-public` default false (`application.properties`) |
| Prometheus in SecurityConfig permitAll | **Partial** | `SecurityConfig.java:37` — mitigated by filter |

### 5.4 CI/CD

| Control | Status | Evidence |
|---------|--------|----------|
| Secret scanning | **Pass** | `ci.yml:14-15` |
| Security integration tests | **Pass** (when infra up) | `SecurityIntegrationTest.java`, `PairingRateLimitIntegrationTest.java` |
| OWASP dependency check | **Partial** | `ci.yml:32` `continue-on-error: true` |

---

## 6. Remediation Verification (C1–C8)

| ID | Original | Verified status | Evidence |
|----|----------|-----------------|----------|
| **C1** | Critical | **Remediated** (Android) | `ParentSecureAuthStore.kt`, `authStorage.ts:49-57` |
| **C2** | Critical | **Remediated** | `deployment.yaml:26-32`, `api-secret.yaml` |
| **C3** | High | **Remediated** | `WebMvcConfig.java:38-43` |
| **C4** | High | **Remediated** | `WebMvcConfig.java:36-37` |
| **C5** | High | **Remediated** | `StompAuthChannelInterceptor.java:57-60` |
| **C6** | High | **Partial** | TLS flags without cert Secret by default |
| **C7** | High | **Remediated** | `networkpolicy.yaml` |
| **C8** | High | **Partial** | Network config scaffold; placeholder pins |

---

## 7. Remaining Critical & High Findings

### Critical: **None** (in source, post-remediation)

No Critical finding has active vulnerable code on the primary Android deployment path. Empty `JWT_SECRET` in chart defaults is blocked at runtime by `ProductionSecurityValidator.java:39-42` when `SPRING_PROFILES_ACTIVE=prod`.

---

### High findings (open)

#### RH-1 — TURN TLS enabled without certificate provisioning

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **CWE** | CWE-319 |
| **Evidence** | `helm/.../values.yaml:64-68` — `tls.enabled: true`, `certSecret: ""`. `coturn.yaml:73-75` passes `--cert`/`--pkey`; volume mount conditional on `certSecret` (`coturn.yaml:90-94`). UDP 3478 remains open (`coturn.yaml:69`). |
| **Exploit** | TURN credentials and relay metadata observable on cleartext UDP 3478; TLS path non-functional until Secret mounted. |
| **Remediation** | Set `coturn.tls.certSecret`; document clients to prefer `turns:` on 5349; consider disabling 3478 in hardened profile. |

#### RH-2 — Certificate pinning not operational in release builds

| Field | Detail |
|-------|--------|
| **Severity** | High (until CI injects pins) |
| **CWE** | CWE-295 |
| **Evidence** | `parent/android/app/src/release/res/xml/network_security_config.xml:11-12` and `child/android/app/src/release/res/xml/network_security_config.xml:11-12` — `REPLACE_WITH_BASE64_SPKI_PIN=`. |
| **Exploit** | Corporate CA or LAN MITM can intercept HTTPS API traffic on release builds if operator removes pin-set or pins are never set. |
| **Remediation** | CI step to inject SPKI pin from production cert; fail release build if placeholder remains. |

#### RH-3 — Active pairing codes written to application logs

| Field | Detail |
|-------|--------|
| **Severity** | High |
| **CWE** | CWE-532 |
| **Evidence** | `backend/.../DeviceService.java:104-105` — `log.info("Created pairing code {} ...", code, ...)`. `DeviceService.java:119-121` — logs pair attempt with code. |
| **Exploit** | Log platform access (Splunk, Loki, support export) yields live pairing codes → device hijack within TTL. |
| **Remediation** | Log code prefix only or hash; structured audit event without code value. |

---

### Elevated Medium (not High, documented)

| ID | Finding | Evidence |
|----|---------|----------|
| M-C9 | Refresh JWT on billing/alerts/audit paths | `JwtAuthenticationFilter.java:88-91`, `SecurityConfig.java:64-67` |
| M-C12 | Razorpay webhook unsigned | `BillingController.java:64-68` |
| M-C11 | MediaProjection auto-approve via Accessibility | `MediaProjectionConsentAutomation.kt:38-39` |
| M-C15 | Integrity token in plain prefs | `MonitoringPrefs.kt:202-215` |
| M-INF | Swagger/OpenAPI public | `SecurityConfig.java:30-33` |
| M-INF | TURN credential TTL 24h | `values.yaml:108`, `TurnCredentialService.java:26` |

---

## 8. Security Readiness Score (Recalculated)

Scoring uses the same domain weights as the adversarial review; scores reflect **verified post-remediation** state.

| Domain | Weight | Pre | Post | Weighted |
|--------|--------|-----|------|----------|
| Authentication & session | 15% | 62 | **78** | 11.7 |
| Authorization & access control | 15% | 78 | **88** | 13.2 |
| Mobile hardening (child) | 15% | 72 | **74** | 11.1 |
| Mobile hardening (parent) | 10% | 45 | **72** | 7.2 |
| Transport & crypto | 10% | 55 | **68** | 6.8 |
| Streaming / WebRTC | 10% | 70 | **82** | 8.2 |
| Infrastructure & secrets | 15% | 48 | **72** | 10.8 |
| Device trust & anti-tamper | 5% | 75 | **75** | 3.75 |
| Compliance & privacy | 5% | 65 | **68** | 3.4 |
| Detection & IR | 5% | 60 | **60** | 3.0 |
| **Overall** | **100%** | **66** | | **79.0** |

**Interpretation:** +13 points from P0 remediations. **79/100** = production-capable with documented High findings and operator checklist. **88/100** projected after RH-1–RH-3 closure and P1 items.

---

## 9. Certification Decision

### Verdict: **CONDITIONAL SECURITY CERTIFICATION**

| Criterion | Result |
|-----------|--------|
| All Critical findings closed in code | **Yes** |
| All High findings closed | **No** (3 open: RH-1, RH-2, RH-3) |
| MASVS L1 ready | **No** — pinning + log hygiene blockers |
| OWASP API Top 10 — no Fail ratings | **Yes** |
| OWASP Mobile Top 10 — no Critical gaps on Android parent | **Yes** (M5/M7 partial) |
| Infrastructure deployable without secret leakage | **Yes** (with `secretEnv` + ESO) |
| Automated security tests present | **Yes** |

### Certification scope

| Component | Certified? | Conditions |
|-----------|------------|------------|
| Backend API (Spring) | **Conditional** | Populate Secrets; fix pairing logs; close refresh scope (P1) |
| Child Android app | **Conditional** | Inject release SPKI pin; integrity cache hardening (P1) |
| Parent Android app | **Conditional** | Inject release SPKI pin |
| Helm / K8s chart | **Conditional** | `coturn.tls.certSecret`, `secrets.existingSecret`, Ingress TLS |
| iOS parent app | **N/A** | No first-party iOS target in repo |

### Required actions before full certification (S1 milestone)

1. **RH-3** — Remove pairing codes from INFO logs (`DeviceService.java:104-105,119-121`).
2. **RH-2** — CI pin injection for both apps' `release/res/xml/network_security_config.xml`.
3. **RH-1** — Provision coturn TLS Secret; validate TURNS connectivity.
4. Run `SecurityIntegrationTest` + `PairingRateLimitIntegrationTest` green in CI with Postgres/Redis.
5. Third-party penetration test (recommended before enterprise sales).

### Sign-off matrix

| Role | Status | Date |
|------|--------|------|
| Implementation (C1–C8) | Complete | 2026-06-18 |
| Re-audit / certification | Conditional pass | 2026-06-18 |
| Security lead approval | Pending RH-1–RH-3 | — |
| External pentest | Not performed | — |

---

## 10. Evidence Index (remediation artifacts)

```
parent/android/.../ParentSecureAuthStore.kt
parent/android/.../ParentNativeModule.kt
parent/src/services/authStorage.ts
parent/src/services/parentSecureAuthBridge.ts
parent/src/__tests__/authStorage.test.ts

backend/.../WebMvcConfig.java
backend/.../StompAuthChannelInterceptor.java
backend/.../DeviceSecurityInterceptor.java
backend/src/test/.../SecurityIntegrationTest.java
backend/src/test/.../PairingRateLimitIntegrationTest.java
backend/src/test/.../StompAuthChannelInterceptorTest.java

helm/parental-control-api/templates/deployment.yaml
helm/parental-control-api/templates/api-secret.yaml
helm/parental-control-api/templates/networkpolicy.yaml
helm/parental-control-api/templates/coturn.yaml
helm/parental-control-api/values.yaml
```

---

## Appendix A — Control regression watchlist

Monitor these paths in future PRs:

- `WebMvcConfig.addInterceptors` — signaling and pairing paths must stay registered
- `StompAuthChannelInterceptor` — deny-by-default `else` branch must not be removed
- `deployment.yaml` — no sensitive keys in plain `env` loop
- `authStorage.ts` — Android must prefer `resolveSecureAuthBridge()` over AsyncStorage
- `DeviceService` — no pairing code values in logs

---

*Document version: 1.0 — Final certification re-audit post C1–C8 remediation*
