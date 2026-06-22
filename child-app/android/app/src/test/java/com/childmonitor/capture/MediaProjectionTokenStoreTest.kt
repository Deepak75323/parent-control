package com.childmonitor.capture

import android.content.Context
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaProjectionTokenStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MediaProjectionTokenStore.clear(context)
    }

    @After
    fun tearDown() {
        MediaProjectionTokenStore.clear(context)
    }

    @Test
    fun neverGranted_byDefault() {
        assertEquals(MediaProjectionConsentState.NEVER_GRANTED, MediaProjectionTokenStore.getConsentState(context))
        assertFalse(MediaProjectionTokenStore.hasValidToken(context))
    }

    @Test
    fun lostAfterProcessRestart_whenPrefsFlagWithoutMemory() {
        context.getSharedPreferences("media_projection_secure", Context.MODE_PRIVATE).edit()
            .putBoolean("consent_granted", true)
            .putBoolean("ever_granted", true)
            .putInt("result_code", -1)
            .commit()

        assertEquals(
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertTrue(MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(context))
        assertFalse(MediaProjectionTokenStore.hasValidToken(context))
    }

    @Test
    fun requiresReconsent_afterSessionInvalidated() {
        context.getSharedPreferences("media_projection_secure", Context.MODE_PRIVATE).edit()
            .putBoolean("consent_granted", true)
            .putBoolean("ever_granted", true)
            .commit()

        MediaProjectionTokenStore.invalidateSession(context)

        assertEquals(
            MediaProjectionConsentState.REQUIRES_RECONSENT,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertFalse(MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(context))
        assertTrue(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
    }

    @Test
    fun recoveryPending_persisted() {
        MediaProjectionTokenStore.markRecoveryPending(context)
        assertEquals(
            MediaProjectionConsentState.RECOVERY_PENDING,
            MediaProjectionTokenStore.getConsentState(context),
        )
    }

    @Test
    fun recoveryFailed_clearsCapability() {
        MediaProjectionTokenStore.markRecoveryFailed(context, "test")
        assertEquals(
            MediaProjectionConsentState.RECOVERY_FAILED,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertFalse(MediaProjectionTokenStore.hasValidToken(context))
    }
}
