package com.childmonitor.security

import com.childmonitor.BuildConfig
import okhttp3.CertificatePinner

/**
 * Certificate pinning configuration for OkHttp clients (child app).
 *
 * ### Architecture
 * Android's Network Security Config (NSC) already covers [HttpURLConnection] and the
 * system HTTPS stack in release builds.  OkHttp has its own trust chain that runs
 * independently of NSC, so [NativeStompClient] requires an explicit
 * [CertificatePinner] for defence-in-depth on the STOMP WebSocket path.
 *
 * ### Pin values
 * Pins are base64-encoded SHA-256 fingerprints of a certificate's Subject Public
 * Key Info (SPKI).  They are injected at build time via Gradle [BuildConfig] fields
 * sourced from CI environment variables (see child/android/app/build.gradle).
 *
 * ### Rotation strategy
 * Two pins are always required:
 *  - **Primary** — the current leaf certificate's SPKI.
 *  - **Backup** — the issuing CA's SPKI, or a pre-generated next-leaf SPKI.
 *
 * When the leaf certificate is renewed, deploy the new leaf SPKI as the next
 * primary and keep the CA SPKI (or set the new leaf as backup) so the pinner
 * continues to match both the old and new chains during the transition window.
 * After all clients have updated, remove the old primary.
 *
 * ### Debug builds
 * [isActive] returns `false` when [BuildConfig.DEBUG] is true so that developers
 * can use local servers, Charles Proxy, or mitmproxy without modifying this file.
 */
object CertPinningConfig {

    /**
     * Returns `true` when certificate pinning should be applied.
     *
     * Conditions for active pinning:
     *  1. This is a release build ([BuildConfig.DEBUG] == false).
     *  2. [BuildConfig.CERT_HOST], [BuildConfig.CERT_PIN_PRIMARY], and
     *     [BuildConfig.CERT_PIN_BACKUP] are all non-blank and not placeholder values.
     */
    val isActive: Boolean
        get() = !BuildConfig.DEBUG &&
                isValidHost(BuildConfig.CERT_HOST) &&
                isValidPin(BuildConfig.CERT_PIN_PRIMARY) &&
                isValidPin(BuildConfig.CERT_PIN_BACKUP)

    /**
     * Builds a [CertificatePinner] for [BuildConfig.CERT_HOST] when [isActive],
     * or returns `null` for debug builds and unconfigured release builds.
     *
     * The pinner covers both the bare hostname and one subdomain level (`*.host`)
     * so that a `wss://ws.api.example.com` endpoint is also pinned when the
     * production host is `api.example.com`.
     *
     * OkHttp validates that the server certificate chain contains a public key
     * matching **at least one** of the listed pins.  Listing primary + backup
     * means either pin satisfies the check — primary is used normally, backup
     * takes over transparently after leaf rotation.
     */
    fun buildPinner(): CertificatePinner? {
        if (!isActive) return null
        return buildPinnerFrom(
            host = BuildConfig.CERT_HOST,
            primary = BuildConfig.CERT_PIN_PRIMARY,
            backup = BuildConfig.CERT_PIN_BACKUP,
        )
    }

    // ── internal helpers (package-internal for unit testing) ─────────────────

    /**
     * Builds a pinner from explicit values.  Exposed as `internal` so unit tests
     * can call it directly without touching [BuildConfig].
     */
    internal fun buildPinnerFrom(host: String, primary: String, backup: String): CertificatePinner? {
        if (!isValidHost(host) || !isValidPin(primary) || !isValidPin(backup)) return null
        return CertificatePinner.Builder()
            // Bare host: covers wss://api.example.com/ws-native
            .add(host, "sha256/$primary")
            .add(host, "sha256/$backup")
            // One subdomain wildcard: covers wss://ws.api.example.com if needed
            .add("*.$host", "sha256/$primary")
            .add("*.$host", "sha256/$backup")
            .build()
    }

    /** Returns `true` when [pin] looks like a valid base64-encoded SHA-256 SPKI hash. */
    internal fun isValidPin(pin: String): Boolean =
        pin.isNotBlank() &&
                !pin.startsWith("REPLACE_") &&
                pin.length >= 44 // SHA-256 = 32 bytes → base64 = 44 chars (with padding)

    /** Returns `true` when [host] is a non-empty, non-placeholder hostname. */
    internal fun isValidHost(host: String): Boolean =
        host.isNotBlank() &&
                !host.startsWith("REPLACE_") &&
                !host.endsWith(".local") && // reject dev/staging placeholders
                host.contains('.')
}
