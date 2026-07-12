package com.childmonitor.capture

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1-05: USE_FULL_SCREEN_INTENT must be validated on Android 14+.
 * Covers granted / denied / revoked / not-required (pre-14) and recovery flags.
 */
@RunWith(RobolectricTestRunner::class)
class FullScreenIntentHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FullScreenIntentHelper.resetForTests(context)
    }

    @After
    fun tearDown() {
        FullScreenIntentHelper.resetForTests(context)
    }

    // ── Pre-Android 14 ────────────────────────────────────────────────────────

    @Test
    @Config(sdk = [33])
    fun preAndroid14_notRequired_andGranted() {
        assertFalse(FullScreenIntentHelper.isRequired())
        assertEquals(
            FullScreenIntentHelper.Status.NOT_REQUIRED,
            FullScreenIntentHelper.validate(context),
        )
        assertTrue(FullScreenIntentHelper.isGranted(context))
        assertFalse(FullScreenIntentHelper.needsRecovery(context))
        assertFalse(FullScreenIntentHelper.wasRevoked(context))
    }

    // ── Android 14 / 15 ───────────────────────────────────────────────────────

    @Test
    @Config(sdk = [34])
    fun android14_required() {
        assertTrue(FullScreenIntentHelper.isRequired())
    }

    @Test
    @Config(sdk = [34])
    fun android14_denied_freshInstall_notRevoked() {
        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.DENIED
        assertEquals(
            FullScreenIntentHelper.Status.DENIED,
            FullScreenIntentHelper.validate(context),
        )
        assertFalse(FullScreenIntentHelper.isGranted(context))
        assertFalse(
            "Fresh install deny must not report wasRevoked",
            FullScreenIntentHelper.wasRevoked(context),
        )
    }

    @Test
    @Config(sdk = [34])
    fun android14_grantThenRevoke_marksRecovery() {
        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.GRANTED
        assertEquals(
            FullScreenIntentHelper.Status.GRANTED,
            FullScreenIntentHelper.validate(context),
        )
        assertTrue(FullScreenIntentHelper.isGranted(context))
        assertFalse(FullScreenIntentHelper.needsRecovery(context))

        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.DENIED
        assertEquals(
            FullScreenIntentHelper.Status.DENIED,
            FullScreenIntentHelper.validate(context),
        )
        assertTrue(FullScreenIntentHelper.wasRevoked(context))
        assertTrue(FullScreenIntentHelper.needsRecovery(context))
        assertFalse(FullScreenIntentHelper.isGranted(context))
    }

    @Test
    @Config(sdk = [35])
    fun android15_regrantClearsRecoveryFlag() {
        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.GRANTED
        FullScreenIntentHelper.validate(context)

        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.DENIED
        FullScreenIntentHelper.validate(context)
        assertTrue(FullScreenIntentHelper.needsRecovery(context))

        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.GRANTED
        assertEquals(
            FullScreenIntentHelper.Status.GRANTED,
            FullScreenIntentHelper.validate(context),
        )
        assertFalse(FullScreenIntentHelper.needsRecovery(context))
    }

    @Test
    @Config(sdk = [35])
    fun android15_onDeniedDuringUse_setsRecovery() {
        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.DENIED
        FullScreenIntentHelper.onDeniedDuringUse(context, "bal_fallback_test")
        assertTrue(FullScreenIntentHelper.needsRecovery(context))
    }

    @Test
    @Config(sdk = [34])
    fun android14_validateOnProcessStart_detectsRevoke() {
        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.GRANTED
        FullScreenIntentHelper.validate(context)

        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.DENIED
        val status = FullScreenIntentHelper.validateOnProcessStart(context)
        assertEquals(FullScreenIntentHelper.Status.DENIED, status)
        assertTrue(FullScreenIntentHelper.wasRevoked(context))
    }

    @Test
    @Config(sdk = [34])
    fun debugState_includesApiAndStatus() {
        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.GRANTED
        val debug = FullScreenIntentHelper.debugState(context)
        assertEquals(true, debug["required"])
        assertEquals(FullScreenIntentHelper.Status.GRANTED.name, debug["status"])
        assertEquals(true, debug["granted"])
        assertEquals(Build.VERSION.SDK_INT, debug["api"])
    }

    @Test
    @Config(sdk = [34])
    fun upgradePath_everGrantedPersistsAcrossValidate() {
        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.GRANTED
        FullScreenIntentHelper.validate(context)
        val prefs = context.getSharedPreferences("full_screen_intent_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("ever_granted", false))

        // Simulate process restart — prefs survive, override cleared then re-denied
        FullScreenIntentHelper.testStatusOverride = FullScreenIntentHelper.Status.DENIED
        FullScreenIntentHelper.validate(context)
        assertTrue(prefs.getBoolean("ever_granted", false))
        assertTrue(prefs.getBoolean("needs_recovery", false))
    }
}
