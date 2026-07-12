package com.childmonitor.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [CertPinningConfig].
 *
 * Security contract being tested:
 *  1. [isValidPin] rejects blank, placeholder, and short values — only correctly-formatted
 *     base64 SHA-256 SPKI hashes (≥ 44 chars, no REPLACE_ prefix) are accepted.
 *  2. [isValidHost] rejects blank, placeholder, `.local`, and no-dot hosts — only real
 *     production FQDNs get through.
 *  3. [buildPinnerFrom] returns null for any invalid input combination and non-null only
 *     when both host and both pins are valid.  Non-null means OkHttp's CertificatePinner
 *     is built and will be attached to the OkHttp client — any MITM whose cert chain
 *     doesn't match one of the two pins will cause OkHttp to throw SSLPeerUnverifiedException.
 *  4. [isActive] / [buildPinner] return null/false in debug/test builds so developers
 *     can use local servers and intercept proxies freely.
 *
 * Note: [BuildConfig.DEBUG] is `true` in the test runner, so [isActive] is always
 * `false` here — which IS the correct observable behaviour for non-release builds.
 */
@RunWith(JUnit4::class)
class CertPinningConfigTest {

    // Synthetic SPKI pins — 44 base64 chars (= 32-byte SHA-256 → 43 chars + 1 padding '=')
    private val validPin1 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val validPin2 = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="

    // =========================================================================
    // isValidPin
    // =========================================================================

    @Test
    fun isValidPin_blank_returnsFalse() {
        assertFalse(CertPinningConfig.isValidPin(""))
        assertFalse(CertPinningConfig.isValidPin("   "))
    }

    @Test
    fun isValidPin_placeholderPrefix_returnsFalse() {
        assertFalse(CertPinningConfig.isValidPin("REPLACE_WITH_BASE64_SPKI_PIN="))
        assertFalse(CertPinningConfig.isValidPin("REPLACE_WITH_BASE64_SPKI_PIN_PRIMARY="))
        assertFalse(CertPinningConfig.isValidPin("REPLACE_WITH_BASE64_SPKI_PIN_BACKUP="))
        assertFalse(CertPinningConfig.isValidPin("REPLACE_"))
    }

    @Test
    fun isValidPin_tooShort_returnsFalse() {
        // Fewer than 44 chars cannot be a valid SHA-256 SPKI base64 hash
        assertFalse(CertPinningConfig.isValidPin("abc"))
        assertFalse(CertPinningConfig.isValidPin("YWJj"))  // "abc" base64
        assertFalse(CertPinningConfig.isValidPin("A".repeat(43)))  // one short
    }

    @Test
    fun isValidPin_exactlyMinLength_returnsTrue() {
        // 44 chars = 32 bytes in base64 (with = padding)
        assertTrue(CertPinningConfig.isValidPin("A".repeat(43) + "="))
    }

    @Test
    fun isValidPin_validRealWorldLengthPins_returnTrue() {
        assertTrue(CertPinningConfig.isValidPin(validPin1))
        assertTrue(CertPinningConfig.isValidPin(validPin2))
    }

    @Test
    fun isValidPin_longerThanMin_returnsTrue() {
        // Some SHA-256 SPKI hashes may be represented without padding (43 chars) or with
        // extra chars — any length ≥ 44 that isn't a placeholder is valid
        assertTrue(CertPinningConfig.isValidPin("A".repeat(44) + "="))
    }

    // =========================================================================
    // isValidHost
    // =========================================================================

    @Test
    fun isValidHost_blank_returnsFalse() {
        assertFalse(CertPinningConfig.isValidHost(""))
        assertFalse(CertPinningConfig.isValidHost("   "))
    }

    @Test
    fun isValidHost_placeholder_returnsFalse() {
        assertFalse(CertPinningConfig.isValidHost("REPLACE_WITH_CERT_HOST"))
    }

    @Test
    fun isValidHost_dotLocalDomain_returnsFalse() {
        // .local names are dev/staging placeholders — must never be pinned in release
        assertFalse(CertPinningConfig.isValidHost("api.parentalcontrol.local"))
        assertFalse(CertPinningConfig.isValidHost("api.example.local"))
        assertFalse(CertPinningConfig.isValidHost("myapi.local"))
    }

    @Test
    fun isValidHost_noDot_returnsFalse() {
        // Bare labels are never valid production FQDNs
        assertFalse(CertPinningConfig.isValidHost("localhost"))
        assertFalse(CertPinningConfig.isValidHost("myapi"))
    }

    @Test
    fun isValidHost_validProductionFqdns_returnTrue() {
        assertTrue(CertPinningConfig.isValidHost("api.example.com"))
        assertTrue(CertPinningConfig.isValidHost("api.childmonitor.io"))
        assertTrue(CertPinningConfig.isValidHost("backend.app.co.uk"))
        assertTrue(CertPinningConfig.isValidHost("svc.prod.mycompany.net"))
    }

    // =========================================================================
    // buildPinnerFrom — null gate (security boundary)
    // =========================================================================

    @Test
    fun buildPinnerFrom_invalidHost_returnsNull() {
        // .local host — must be rejected
        assertNull(CertPinningConfig.buildPinnerFrom("api.example.local", validPin1, validPin2))
    }

    @Test
    fun buildPinnerFrom_blankHost_returnsNull() {
        assertNull(CertPinningConfig.buildPinnerFrom("", validPin1, validPin2))
    }

    @Test
    fun buildPinnerFrom_blankPrimaryPin_returnsNull() {
        assertNull(CertPinningConfig.buildPinnerFrom("api.example.com", "", validPin2))
    }

    @Test
    fun buildPinnerFrom_placeholderPrimaryPin_returnsNull() {
        assertNull(CertPinningConfig.buildPinnerFrom("api.example.com", "REPLACE_WITH_BASE64_SPKI_PIN_PRIMARY=", validPin2))
    }

    @Test
    fun buildPinnerFrom_shortPrimaryPin_returnsNull() {
        assertNull(CertPinningConfig.buildPinnerFrom("api.example.com", "shortpin", validPin2))
    }

    @Test
    fun buildPinnerFrom_blankBackupPin_returnsNull() {
        assertNull(CertPinningConfig.buildPinnerFrom("api.example.com", validPin1, ""))
    }

    @Test
    fun buildPinnerFrom_placeholderBackupPin_returnsNull() {
        assertNull(CertPinningConfig.buildPinnerFrom("api.example.com", validPin1, "REPLACE_WITH_BASE64_SPKI_PIN_BACKUP="))
    }

    @Test
    fun buildPinnerFrom_allValid_returnsNonNullPinner() {
        val pinner = CertPinningConfig.buildPinnerFrom("api.example.com", validPin1, validPin2)
        assertNotNull("buildPinnerFrom must return a pinner for valid inputs", pinner)
    }

    @Test
    fun buildPinnerFrom_samePinForBothSlots_doesNotCrash() {
        // Degenerate but valid during initial deploy before a second cert is ready
        val pinner = CertPinningConfig.buildPinnerFrom("api.example.com", validPin1, validPin1)
        assertNotNull(pinner)
    }

    // =========================================================================
    // isActive + buildPinner — debug/test builds must never pin
    // =========================================================================

    @Test
    fun isActive_inDebugTestBuild_isFalse() {
        // BuildConfig.DEBUG == true in the test runner → pinning must be off
        // so developers can freely use local servers, Charles, mitmproxy, etc.
        assertFalse(
            "Pinning must be disabled in debug/test builds",
            CertPinningConfig.isActive,
        )
    }

    @Test
    fun buildPinner_inDebugTestBuild_returnsNull() {
        // When isActive is false, no CertificatePinner must be attached to OkHttp
        assertNull(
            "buildPinner() must return null in debug/test builds",
            CertPinningConfig.buildPinner(),
        )
    }

    // =========================================================================
    // MITM rejection — structural gate (the key security invariant)
    // =========================================================================

    @Test
    fun mitm_emptyPins_returnNullPinner_noConnectionAttempted() {
        // If an attacker tampers with the CI env to supply empty pins, buildPinnerFrom
        // returns null — the build itself then fails at the Gradle validation gate
        // before any APK is produced.
        assertNull(CertPinningConfig.buildPinnerFrom("api.example.com", "", ""))
    }

    @Test
    fun mitm_placeholderPins_returnNullPinner_attackerCannotBypassPinning() {
        // The REPLACE_ placeholder values used in the committed template XML must never
        // produce a pinner that would allow any certificate through.
        assertNull(
            CertPinningConfig.buildPinnerFrom(
                "api.example.com",
                "REPLACE_WITH_BASE64_SPKI_PIN=",
                "REPLACE_WITH_BASE64_SPKI_PIN=",
            ),
        )
    }

    @Test
    fun mitm_devLocalhost_returnNullPinner_localDevServerNotPinned() {
        // Local dev server or mitmproxy — must never get a pinner
        assertNull(CertPinningConfig.buildPinnerFrom("localhost", validPin1, validPin2))
        assertNull(CertPinningConfig.buildPinnerFrom("api.example.local", validPin1, validPin2))
    }

    // =========================================================================
    // Certificate rotation safety
    // =========================================================================

    @Test
    fun rotation_newLeafPinAndCaBackup_buildsPinnerSuccessfully() {
        // After leaf rotation: new leaf becomes primary, CA/next-leaf stays as backup.
        // buildPinnerFrom must succeed so the app connects to the new cert chain.
        val newLeafPin = "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD="
        val caBackupPin = validPin2
        val pinner = CertPinningConfig.buildPinnerFrom("api.example.com", newLeafPin, caBackupPin)
        assertNotNull("Pinner must be built after leaf rotation with valid pins", pinner)
    }

    @Test
    fun rotation_placeholderBackupPin_returnsNull_blocksIncompleteDeploy() {
        // A deploy that forgot to update the backup pin must fail at build time
        // (Gradle validation) AND at runtime (buildPinnerFrom returns null, causing
        // the pinner-less OkHttp to rely solely on the Android NSC XML).
        assertNull(
            CertPinningConfig.buildPinnerFrom("api.example.com", validPin1, "REPLACE_WITH_BACKUP"),
        )
    }
}
