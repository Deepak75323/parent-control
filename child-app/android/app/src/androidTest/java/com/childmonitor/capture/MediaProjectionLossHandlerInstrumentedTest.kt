package com.childmonitor.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.childmonitor.MonitoringPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(org.junit.runners.JUnit4::class)
class MediaProjectionLossHandlerInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MediaProjectionLossHandler.resetForTests()
        MediaProjectionRecoveryManager.resetForTests()
        MediaProjectionTokenStore.clear(context)
        MonitoringPrefs.clear(context)
    }

    @After
    fun tearDown() {
        MediaProjectionTokenStore.clear(context)
        MonitoringPrefs.clear(context)
        MediaProjectionLossHandler.resetForTests()
        MediaProjectionRecoveryManager.resetForTests()
    }

    @Test
    fun isProjectionCapabilityLost_falseWhenNeverGranted() {
        assertFalse(MediaProjectionLossHandler.isProjectionCapabilityLost(context))
        assertFalse(MediaProjectionLossHandler.isScreenShareAvailable(context))
        assertEquals(MediaProjectionConsentState.NEVER_GRANTED, MediaProjectionLossHandler.currentConsentState(context))
    }

    @Test
    fun isProjectionCapabilityLost_trueWhenConsentFlagWithoutMemory() {
        context.getSharedPreferences("media_projection_secure", Context.MODE_PRIVATE).edit()
            .putBoolean("consent_granted", true)
            .putBoolean("ever_granted", true)
            .putInt("result_code", -1)
            .commit()

        assertTrue(MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(context))
        assertTrue(MediaProjectionLossHandler.isProjectionCapabilityLost(context))
        assertFalse(MediaProjectionLossHandler.isScreenShareAvailable(context))
        assertEquals(
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionLossHandler.currentConsentState(context),
        )
    }

    @Test
    fun requiresReconsent_afterInvalidateSession() {
        context.getSharedPreferences("media_projection_secure", Context.MODE_PRIVATE).edit()
            .putBoolean("ever_granted", true)
            .putBoolean("consent_granted", true)
            .commit()
        MediaProjectionTokenStore.invalidateSession(context)

        assertTrue(MediaProjectionLossHandler.isProjectionCapabilityLost(context))
        assertEquals(
            MediaProjectionConsentState.REQUIRES_RECONSENT,
            MediaProjectionLossHandler.currentConsentState(context),
        )
    }
}
