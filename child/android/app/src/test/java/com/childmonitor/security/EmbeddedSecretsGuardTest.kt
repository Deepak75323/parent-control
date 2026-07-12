package com.childmonitor.security

import com.childmonitor.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * P1-02 guard tests — ensures no master PIN or tamper bypass secrets are embedded
 * in the APK via BuildConfig or legacy bypass classes.
 *
 * Tamper verification is server-side only:
 *   POST /api/v1/device/tamper/verify  (parent PIN, dev master PIN on backend, or code)
 */
@RunWith(JUnit4::class)
class EmbeddedSecretsGuardTest {

    @Test
    fun buildConfig_hasNoTamperMasterPinField() {
        val fieldNames = BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse(
            "BuildConfig must not expose TAMPER_MASTER_PIN — secrets belong on the server only",
            fieldNames.contains("TAMPER_MASTER_PIN"),
        )
    }

    @Test
    fun tamperDevBypass_classMustNotExist() {
        // Reverse-engineering guard: local offline bypass was removed in P1-02.
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.childmonitor.tamper.TamperDevBypass")
        }
    }

    @Test
    fun buildConfig_debugBuild_hasEmptyCertPins() {
        // Debug builds must not ship production cert pins (those are release-only via CI).
        if (BuildConfig.DEBUG) {
            assertFalse(
                "Debug BuildConfig.CERT_PIN_PRIMARY must be empty",
                BuildConfig.CERT_PIN_PRIMARY.isNotBlank(),
            )
            assertFalse(
                "Debug BuildConfig.CERT_PIN_BACKUP must be empty",
                BuildConfig.CERT_PIN_BACKUP.isNotBlank(),
            )
        }
    }
}
