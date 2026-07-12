# Security Remediation Report — Critical & High Findings (C1–C8)

**Source:** [SECURITY-ADVERSARIAL-REVIEW.md](./SECURITY-ADVERSARIAL-REVIEW.md)  
**Date:** 2026-06-18  
**Scope:** Verification, remediation, and test coverage for findings C1 through C8.

---

## C1 — Parent JWT/refresh in AsyncStorage

### Verification Status
**Confirmed** (pre-remediation). **Remediated** on Android.

### Evidence

**Pre-remediation (vulnerable pattern):** Parent auth tokens were persisted via `AsyncStorage.setItem` under key `pcp_parent_auth_v1` — world-readable on a compromised device without root.

**Post-remediation files:**

| File | Lines | Role |
|------|-------|------|
| `parent/android/.../ParentSecureAuthStore.kt` | 1–52 | AES-256-GCM EncryptedSharedPreferences |
| `parent/android/.../ParentNativeModule.kt` | 19–44 | RN bridge: `saveSecureAuth` / `loadSecureAuth` / `clearSecureAuth` |
| `parent/src/services/parentSecureAuthBridge.ts` | 1–19 | Runtime bridge resolution |
| `parent/src/services/authStorage.ts` | 49–57 | Secure path preferred; AsyncStorage only as iOS / fallback |

**Vulnerable code (before):**
```typescript
// parent/src/services/authStorage.ts (pre-fix)
const AUTH_KEY = 'pcp_parent_auth_v1';
await AsyncStorage.setItem(AUTH_KEY, JSON.stringify(auth));
```

**Fixed code (after):**
```kotlin
// ParentSecureAuthStore.kt:36-38
fun saveAuthJson(context: Context, json: String) {
    prefs(context).edit().putString(KEY_AUTH_JSON, json).commit()
}
```

```typescript
// authStorage.ts:49-57
export async function savePersistedAuth(auth: PersistedAuth): Promise<void> {
  const json = JSON.stringify(auth);
  const bridge = resolveSecureAuthBridge();
  if (bridge) {
    await bridge.saveSecureAuth(json);
    await AsyncStorage.removeItem(LEGACY_AUTH_KEY);
    return;
  }
  await AsyncStorage.setItem(LEGACY_AUTH_KEY, json);
}
```

### Exploitability
Malware or backup extraction on a parent device could read `AsyncStorage` XML/SQLite and obtain access + refresh JWTs → full account takeover (list devices, start streams, send commands) until token expiry/revocation.

### Fix
- Added `ParentSecureAuthStore` using `androidx.security:security-crypto` EncryptedSharedPreferences.
- Exposed native methods on `ParentNativeModule`.
- Refactored `authStorage.ts` to use secure bridge on Android with one-time migration from legacy AsyncStorage key.
- iOS still uses AsyncStorage (no Keychain bridge yet) — documented as residual risk.

### Tests
| Type | File | Coverage |
|------|------|----------|
| Unit | `parent/src/__tests__/authStorage.test.ts` | Save/load/migrate/clear via mocked bridge |
| Integration | Manual: install parent debug APK, login, verify `parent_secure_auth_prefs` encrypted |
| Regression | `authStorage.test.ts` — asserts AsyncStorage not used when bridge present |

### Residual Risk
**Medium.** Android tokens are now hardware-backed encrypted at rest. Residual paths: (1) iOS AsyncStorage until Keychain bridge added; (2) in-memory JWT during active session; (3) rooted device with runtime memory dump. **Estimated reduction: Critical → Medium.**

---

## C2 — K8s API secrets as plain env

### Verification Status
**Confirmed** (pre-remediation). **Remediated.**

### Evidence

**Before:** `helm/parental-control-api/templates/deployment.yaml` rendered all `Values.env` as plain `value:` fields, including `JWT_SECRET`, `APP_ENCRYPTION_KEY`, `FCM_SERVER_KEY`, `METRICS_SCRAPE_TOKEN`, `TURN_SECRET`.

**Vulnerable code (before):**
```yaml
# deployment.yaml (pre-fix, lines 21-25)
env:
  {{- range $k, $v := .Values.env }}
  - name: {{ $k }}
    value: {{ $v | quote }}
  {{- end }}
```

**Fixed code (after):**
```yaml
# deployment.yaml:21-32
env:
  {{- range $k, $v := .Values.env }}
  - name: {{ $k }}
    value: {{ $v | quote }}
  {{- end }}
  {{- range $k, $_ := .Values.secretEnv }}
  - name: {{ $k }}
    valueFrom:
      secretKeyRef:
        name: {{ include "parental-control-api.secretName" $ }}
        key: {{ $k }}
  {{- end }}
```

| File | Lines |
|------|-------|
| `helm/.../templates/api-secret.yaml` | 1–13 |
| `helm/.../templates/_helpers.tpl` | 1–7 |
| `helm/.../values.yaml` | `secrets:` block + `secretEnv:` (lines 20–24, 99–105) |

### Exploitability
Anyone with `kubectl get pod -o yaml`, etcd backup access, or cluster observability tools could read JWT signing keys, encryption keys, and FCM credentials from Pod spec env vars → forge tokens, decrypt data at rest, push arbitrary notifications.

### Fix
- Split `values.yaml` into non-sensitive `env` and sensitive `secretEnv`.
- Added `api-secret.yaml` template (created when `secrets.create=true` and no `existingSecret`).
- Deployment references `secretKeyRef` for all `secretEnv` keys.
- Operators should set `secrets.existingSecret` to an ESO/Vault-managed Secret in production.

### Tests
| Type | File | Coverage |
|------|------|----------|
| Unit | `helm template` validation | Run: `helm template pcp helm/parental-control-api \| grep secretKeyRef` |
| Integration | Cluster deploy smoke | Verify Pod env shows `valueFrom.secretKeyRef` not plaintext |
| Regression | Grep CI check (recommended) | Fail build if `JWT_SECRET` appears as `value:` in rendered manifest |

### Residual Risk
**Low–Medium.** Secrets no longer in plain Deployment env. Residual: etcd encryption at rest depends on cluster config; Secret RBAC must be restricted; chart default `secrets.create=true` still allows empty placeholder values in git — operators must inject real secrets via CI/CD. **Estimated reduction: Critical → Low.**

---

## C3 — Signaling child POST bypasses HMAC

### Verification Status
**Confirmed** (pre-remediation). **Remediated.**

### Evidence

`DeviceSecurityInterceptor` listed `/api/v1/monitoring/signaling/` in `HMAC_PATH_PREFIXES` (line 33) but `WebMvcConfig` did not register the interceptor on that path.

| File | Lines | Issue |
|------|-------|-------|
| `backend/.../DeviceSecurityInterceptor.java` | 29–34 | HMAC required for signaling prefix |
| `backend/.../WebMvcConfig.java` | 38–43 (pre-fix) | Signaling path missing from interceptor registration |

**Vulnerable code (before):**
```java
// WebMvcConfig.java (pre-fix)
registry.addInterceptor(deviceSecurityInterceptor)
        .addPathPatterns(
                "/api/v1/device/**",
                "/api/v1/child/**",
                "/api/v1/events/**");
```

**Fixed code (after):**
```java
// WebMvcConfig.java:38-43
registry.addInterceptor(deviceSecurityInterceptor)
        .addPathPatterns(
                "/api/v1/device/**",
                "/api/v1/child/**",
                "/api/v1/events/**",
                "/api/v1/monitoring/signaling/**");
```

### Exploitability
Attacker with stolen device JWT (no HMAC key) could `POST` SDP/ICE payloads to `/api/v1/monitoring/signaling/{roomId}` → disrupt or inject WebRTC negotiation (ES-6 in adversarial review).

### Fix
Registered `deviceSecurityInterceptor` on `/api/v1/monitoring/signaling/**`.

### Tests
| Type | File | Test |
|------|------|------|
| Integration | `SecurityIntegrationTest.java` | `@Order(11)` rejects POST without HMAC |
| Integration | `SecurityIntegrationTest.java` | `@Order(12)` accepts POST with valid HMAC |
| Regression | `SecurityIntegrationTest` | Existing HMAC tests unchanged |

### Residual Risk
**Low.** Child device POST signaling now requires valid HMAC. Parent signaling uses parent JWT (no HMAC by design). Stolen device JWT alone is insufficient for child POST. **Estimated reduction: High → Low.**

---

## C4 — Pairing brute-force: rate limit not wired

### Verification Status
**Confirmed** (pre-remediation). **Remediated.**

### Evidence

`AuthRateLimitInterceptor` implemented `checkPairing` for `POST /api/v1/device/pair` (lines 23–26) but was only registered on `/api/v1/auth/**`.

| File | Lines |
|------|-------|
| `backend/.../AuthRateLimitInterceptor.java` | 23–26 |
| `backend/.../WebMvcConfig.java` | 36–37 (fixed) |

**Vulnerable code (before):**
```java
// WebMvcConfig.java (pre-fix)
registry.addInterceptor(authRateLimitInterceptor)
        .addPathPatterns("/api/v1/auth/**");
```

**Fixed code (after):**
```java
// WebMvcConfig.java:36-37
registry.addInterceptor(authRateLimitInterceptor)
        .addPathPatterns("/api/v1/auth/**", "/api/v1/device/pair");
```

### Exploitability
Distributed botnet could hammer `POST /api/v1/device/pair` with guessed 8-character codes (ES-3). Without rate limiting, attacker has far higher success probability within code TTL.

### Fix
Added `/api/v1/device/pair` to `authRateLimitInterceptor` path patterns.

### Tests
| Type | File | Test |
|------|------|------|
| Integration | `PairingRateLimitIntegrationTest.java` | 3rd request returns 429 with limit=2/min |
| Unit | `AuthRateLimitInterceptor` (implicit) | `checkPairing` invoked when path matches |
| Regression | `SecurityIntegrationTest` | Pairing flow still works under disabled rate limit |

### Residual Risk
**Low–Medium.** Rate limit is per client IP (`X-Forwarded-For` first hop). Residual: distributed attack across many IPs; Redis unavailable silently disables limiting (dev behavior). Recommend dedicated `pairingRequestsPerMinute` property and global pairing code lockout. **Estimated reduction: High → Low–Medium.**

---

## C5 — STOMP subscribe to unlisted `/topic/*` not denied

### Verification Status
**Confirmed** (pre-remediation). **Remediated.**

### Evidence

`StompAuthChannelInterceptor` only validated known prefixes; unknown destinations fell through silently.

| File | Lines |
|------|-------|
| `backend/.../StompAuthChannelInterceptor.java` | 48–60 (fixed) |

**Vulnerable code (before):**
```java
// StompAuthChannelInterceptor.java (pre-fix, lines 48-57)
if (destination != null && destination.startsWith("/topic/device/")) {
    validateDeviceTopic(principal, destination);
} else if (destination != null && destination.startsWith("/topic/signaling/")) {
    ...
} else if (destination != null && destination.startsWith("/user/queue/")) {
    validateUserQueue(principal, destination);
}
// unknown destinations allowed — no else branch
```

**Fixed code (after):**
```java
// StompAuthChannelInterceptor.java:55-60
} else if (destination != null && destination.startsWith("/user/queue/")) {
    validateUserQueue(principal, destination);
} else {
    throw new MessageDeliveryException(
            "Forbidden STOMP destination: " + (destination == null ? "<null>" : destination));
}
```

### Exploitability
Authenticated parent or child JWT could `SUBSCRIBE` or `SEND` to future/admin topics (e.g. `/topic/admin/config`) before ACLs exist (ES-5).

### Fix
Deny-by-default for all SUBSCRIBE/SEND destinations not matching allowlisted prefixes.

### Tests
| Type | File | Test |
|------|------|------|
| Unit | `StompAuthChannelInterceptorTest.java` | Rejects `/topic/admin/secrets` |
| Unit | `StompAuthChannelInterceptorTest.java` | Rejects SEND with null destination |
| Regression | `StompDestinationRegressionIntegrationTest.java` | App context loads after hardening |

### Residual Risk
**Low.** New legitimate topics require explicit interceptor allowlist entry. Risk of breaking new features if destinations added without updating interceptor — mitigated by unit tests. **Estimated reduction: High → Low.**

---

## C6 — TURN public LoadBalancer without TLS

### Verification Status
**Confirmed** (pre-remediation). **Remediated** (TLS enabled by default in chart).

### Evidence

| File | Lines |
|------|-------|
| `helm/.../templates/coturn.yaml` | 67–72 (pre-fix: `--no-tls --no-dtls`) |
| `helm/.../values.yaml` | `coturn.tls.enabled: true` |

**Vulnerable code (before):**
```yaml
# coturn.yaml (pre-fix)
--no-tls \
--no-dtls \
```

**Fixed code (after):**
```yaml
# coturn.yaml (conditional)
{{- if .Values.coturn.tls.enabled }}
--cert=/tls/{{ .Values.coturn.tls.certFile }} \
--pkey=/tls/{{ .Values.coturn.tls.keyFile }} \
{{- else }}
--no-tls \
--no-dtls \
{{- end }}
```

TLS cert volume mount added when `coturn.tls.certSecret` is set.

### Exploitability
TURN credentials and relayed media metadata traverse UDP/TCP 3478 in cleartext on public LoadBalancer → passive network observer can capture long-lived TURN username/password (ES-2 adjunct).

### Fix
- `coturn.tls.enabled: true` by default.
- Mount TLS cert Secret at `/tls` when `coturn.tls.certSecret` configured.
- Port 5349 (`turns-tcp`) exposed for TLS TURN.

### Tests
| Type | File | Coverage |
|------|------|----------|
| Unit | Helm render | Verify `--cert` present when `tls.enabled=true` |
| Integration | TURN client | `turnutils_uclient -S -p 5349` against deployed coturn |
| Regression | `TurnCredentialServiceTest.java` | Credential minting unchanged |

### Residual Risk
**Medium** until operators provision `coturn.tls.certSecret` with valid cert. UDP TURN (3478) may still be used by some clients; WebRTC should prefer `turns:`. DTLS for TURN requires additional coturn flags. **Estimated reduction: High → Medium** (pending cert deployment).

---

## C7 — No Kubernetes NetworkPolicies

### Verification Status
**Confirmed** (pre-remediation). **Remediated.**

### Evidence

Pre-remediation: no `NetworkPolicy` resources under `helm/`.

| File | Lines |
|------|-------|
| `helm/.../templates/networkpolicy.yaml` | 1–68 |
| `helm/.../values.yaml` | `networkPolicy.enabled: true` |

**Fixed resources:**
- `parental-control-api-api` — ingress only from configured namespaces (default: `ingress-nginx`, `default`); egress to Postgres/Redis/Kafka/OTEL/DNS.
- `parental-control-api-turn` — ingress UDP/TCP TURN + relay port range; egress DNS only.

### Exploitability
Compromised pod in cluster could lateral-move to API, Postgres, Redis, or scrape metrics without network segmentation (CWE-668).

### Fix
Added opt-in (default **enabled**) NetworkPolicy templates with namespace-scoped ingress and minimal egress.

### Tests
| Type | File | Coverage |
|------|------|----------|
| Unit | `helm template` | Verify NetworkPolicy objects rendered |
| Integration | Cluster | `kubectl exec` from unrelated namespace → connection refused to :8080 |
| Regression | Deploy smoke | API health from ingress still OK |

### Residual Risk
**Low–Medium.** Policies depend on correct namespace labels (`kubernetes.io/metadata.name`). Egress is permissive to listed ports cluster-wide — tighten to named Services in hardened environments. CNI must support NetworkPolicy (Calico/Cilium). **Estimated reduction: High → Low–Medium.**

---

## C8 — Cleartext + no cert pinning (parent); pins disabled (child)

### Verification Status
**Confirmed** (pre-remediation). **Partially remediated** — infrastructure in place; production pins require operator values.

### Evidence

**Parent (before):**
```xml
<!-- parent/android/app/src/main/AndroidManifest.xml (pre-fix, line 11) -->
android:usesCleartextTraffic="true"
```
No `network_security_config`.

**Child (before):** Pin block commented out in `network_security_config.xml`; `usesCleartextTraffic="true"` in manifest.

| File | Lines | Change |
|------|-------|--------|
| `parent/.../AndroidManifest.xml` | 10–11 | Added `networkSecurityConfig`; removed global cleartext |
| `parent/.../src/debug/res/xml/network_security_config.xml` | 1–16 | Dev cleartext domains only |
| `parent/.../src/release/res/xml/network_security_config.xml` | 1–14 | Cert pin template |
| `child/.../src/release/res/xml/network_security_config.xml` | 1–14 | Release pins enabled (placeholder) |
| `child/.../src/main/res/xml/network_security_config.xml` | 7–11 | `cleartextTrafficPermitted="false"` base |

**Fixed parent manifest:**
```xml
android:networkSecurityConfig="@xml/network_security_config"
```

### Exploitability
LAN MITM on cleartext HTTP during dev or compromised WiFi without pinning → capture pairing response (`deviceToken` + `deviceHmacKey`) or parent API tokens (ES-2).

### Fix
- Parent: flavor-specific network security configs; release builds deny cleartext and enforce pin-set scaffold.
- Child: release overlay with pin-set; debug retains LAN cleartext exceptions.
- Debug builds explicitly opt into cleartext via debug manifest overlay.

### Tests
| Type | File | Coverage |
|------|------|----------|
| Unit | Android lint / manifest merge | Release manifest has no `usesCleartextTraffic=true` |
| Integration | Instrumented | HTTPS API call fails with wrong pin (after pin set) |
| Regression | `authStorage.test.ts` | Unaffected — transport layer separate |

### Residual Risk
**Medium.** Placeholder pin `REPLACE_WITH_BASE64_SPKI_PIN=` must be replaced before production release or TLS will fail pin validation. iOS parent app not covered. Frida/ssl-kill-switch can bypass pinning on rooted devices. **Estimated reduction: High → Medium** (pending pin injection + iOS).

---

## Summary

| ID | Status | Remediated | Residual Risk |
|----|--------|------------|---------------|
| C1 | Confirmed | Yes (Android) | Medium |
| C2 | Confirmed | Yes | Low |
| C3 | Confirmed | Yes | Low |
| C4 | Confirmed | Yes | Low–Medium |
| C5 | Confirmed | Yes | Low |
| C6 | Confirmed | Yes (config) | Medium (cert deploy) |
| C7 | Confirmed | Yes | Low–Medium |
| C8 | Confirmed | Partial | Medium (pins + iOS) |

## Test Execution

```bash
# Backend unit (no infra)
cd backend && mvn test -Dtest=StompAuthChannelInterceptorTest

# Backend integration (requires Postgres + Redis via Podman)
./scripts/dev/compose.sh up -d postgres redis
cd backend && mvn test -Dtest=SecurityIntegrationTest,PairingRateLimitIntegrationTest

# Parent Jest
cd parent && CI=true npm test -- --testPathPattern=authStorage --watchAll=false

# Parent Android compile
cd parent/android && ./gradlew :app:compileDebugKotlin
```

## Operator Checklist (Post-Deploy)

1. Set `secrets.existingSecret` or populate `secretEnv` via sealed-secrets/ESO — never commit real values.
2. Create `coturn.tls.certSecret` with valid TLS cert/key; verify TURNS on port 5349.
3. Replace `REPLACE_WITH_BASE64_SPKI_PIN=` in release `network_security_config.xml` for both apps.
4. Label ingress namespace for NetworkPolicy: `kubernetes.io/metadata.name: ingress-nginx`.
5. Run full `SecurityIntegrationTest` suite in CI against ephemeral Postgres/Redis.

---

*Generated by security remediation implementation pass. Findings C9–C15 are out of scope for this document.*
