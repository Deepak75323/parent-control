package com.childmonitor.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry

/**
 * Auto-recovery must mark LOST / REQUIRES_RECONSENT and must never claim Cast was launched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaProjectionRecoveryManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MediaProjectionTokenStore.resetEncryptedPrefsForTests()
        MediaProjectionTokenStore.clear(context)
        MediaProjectionRecoveryManager.resetForTests()
        MediaProjectionLossHandler.resetForTests()
        context.getSharedPreferences("media_projection_telemetry", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        MediaProjectionTokenStore.clear(context)
        MediaProjectionRecoveryManager.resetForTests()
        context.getSharedPreferences("media_projection_telemetry", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun attemptRecovery_neverLaunchesConsent_returnsFalse() {
        MediaProjectionTokenStore.markRecoveryFailed(context, "test")
        val launched = MediaProjectionRecoveryManager.attemptRecovery(context, "boot_test")
        assertFalse(launched)
        assertEquals(
            MediaProjectionConsentState.REQUIRES_RECONSENT,
            MediaProjectionTokenStore.getConsentState(context),
        )
    }

    @Test
    fun onRecoveryComplete_failure_marksRequiresReconsent_noRetryLaunch() {
        MediaProjectionTokenStore.markRecoveryPending(context)
        MediaProjectionRecoveryManager.onRecoveryComplete(context, success = false, reason = "denied")
        assertEquals(
            MediaProjectionConsentState.REQUIRES_RECONSENT,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertFalse(MediaProjectionTokenStore.hasValidToken(context))
    }

    @Test
    fun recoveryFailed_statePersists_untilMarkedLost() {
        MediaProjectionTokenStore.markRecoveryFailed(context, "explicit")
        assertEquals(
            MediaProjectionConsentState.RECOVERY_FAILED,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertFalse(MediaProjectionTokenStore.hasValidToken(context))
    }

    @Test
    fun telemetry_recordsLoss() {
        MediaProjectionRecoveryTelemetry.recordLoss(context, "process_death", "LOST_AFTER_PROCESS_DEATH")
        assertEquals(1, MediaProjectionRecoveryTelemetry.lossCount(context))
        assertEquals("process_death", MediaProjectionRecoveryTelemetry.lastLossReason(context))
    }

    @Test
    fun clearRecoveryAttemptBudget_resetsCount() {
        context.getSharedPreferences("media_projection_telemetry", Context.MODE_PRIVATE).edit()
            .putInt("recovery_attempts", MediaProjectionRecoveryManager.MAX_RECOVERY_ATTEMPTS)
            .commit()
        MediaProjectionRecoveryTelemetry.clearRecoveryAttemptBudget(context)
        assertEquals(0, MediaProjectionRecoveryTelemetry.recoveryAttemptCount(context))
    }
}
