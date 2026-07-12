package com.childmonitor.capture

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1-07: MediaProjection must not be obtained before mediaProjection-typed FGS
 * on Android 14–16. Covers cached-consent / cold-start gate behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaProjectionFgsOrderingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MediaProjectionTokenStore.resetEncryptedPrefsForTests()
        MediaProjectionTokenStore.clear(context)
        ScreenCaptureController.onFreshConsentSaved()
    }

    @After
    fun tearDown() {
        MediaProjectionTokenStore.clear(context)
        ScreenCaptureController.onFreshConsentSaved()
    }

    @Test
    fun requiredSequence_isConsentThenMpFgsThenObtain() {
        assertEquals(
            listOf("user_consent", "startForeground_mediaProjection", "getMediaProjection"),
            MediaProjectionFgsOrdering.requiredSequence(),
        )
    }

    @Test
    @Config(sdk = [33])
    fun preAndroid14_doesNotRequireMediaProjectionType() {
        assertFalse(MediaProjectionFgsOrdering.requiresMediaProjectionTypeBeforeObtain(33))
        assertTrue(MediaProjectionFgsOrdering.canCallGetMediaProjection(33, mediaProjectionTypeActive = false))
    }

    @Test
    @Config(sdk = [34])
    fun android14_requiresMediaProjectionType() {
        assertTrue(MediaProjectionFgsOrdering.requiresMediaProjectionTypeBeforeObtain(34))
        assertFalse(MediaProjectionFgsOrdering.canCallGetMediaProjection(34, mediaProjectionTypeActive = false))
        assertTrue(MediaProjectionFgsOrdering.canCallGetMediaProjection(34, mediaProjectionTypeActive = true))
    }

    @Test
    @Config(sdk = [35])
    fun android15_requiresMediaProjectionType() {
        assertTrue(MediaProjectionFgsOrdering.requiresMediaProjectionTypeBeforeObtain(35))
        assertFalse(MediaProjectionFgsOrdering.canCallGetMediaProjection(35, mediaProjectionTypeActive = false))
        assertTrue(MediaProjectionFgsOrdering.canCallGetMediaProjection(35, mediaProjectionTypeActive = true))
    }

    @Test
    fun android16_requiresMediaProjectionType() {
        // Robolectric may not ship SDK 36 jars yet — gate is apiLevel >= 34, so assert on 36 directly.
        assertTrue(MediaProjectionFgsOrdering.requiresMediaProjectionTypeBeforeObtain(36))
        assertFalse(MediaProjectionFgsOrdering.canCallGetMediaProjection(36, mediaProjectionTypeActive = false))
        assertTrue(MediaProjectionFgsOrdering.canCallGetMediaProjection(36, mediaProjectionTypeActive = true))
    }

    @Test
    @Config(sdk = [34])
    fun isMediaProjectionTypeActive_falseWhenNoService() {
        assertFalse(ScreenCaptureForegroundService.isForegroundActive())
        assertFalse(ScreenCaptureForegroundService.isMediaProjectionTypeActive())
    }

    @Test
    @Config(sdk = [34])
    fun obtainProjection_blockedWithoutMediaProjectionFgs_android14() {
        MediaProjectionTokenStore.save(context, -1, Intent("test.media.projection.consent"))
        ScreenCaptureController.onFreshConsentSaved()

        assertNull(
            "P1-07: getMediaProjection must not run without mediaProjection FGS",
            ScreenCaptureController.obtainProjection(context),
        )
        assertFalse(ScreenCaptureController.projectionObtainedWithFgs())
    }

    @Test
    @Config(sdk = [35])
    fun obtainProjection_blockedWithoutMediaProjectionFgs_android15() {
        MediaProjectionTokenStore.save(context, -1, Intent("test.media.projection.consent"))
        ScreenCaptureController.onFreshConsentSaved()

        assertNull(ScreenCaptureController.obtainProjection(context))
    }

    @Test
    @Config(sdk = [35])
    fun obtainProjection_blockedWithoutMediaProjectionFgs_android15_sameGateAs16() {
        // Android 16 uses the same UPSIDE_DOWN_CAKE (34+) gate verified for API 35 runtime.
        assertTrue(MediaProjectionFgsOrdering.requiresMediaProjectionTypeBeforeObtain(36))
        MediaProjectionTokenStore.save(context, -1, Intent("test.media.projection.consent"))
        ScreenCaptureController.onFreshConsentSaved()
        assertNull(ScreenCaptureController.obtainProjection(context))
    }

    @Test
    @Config(sdk = [34])
    fun dataSyncAlone_isNotEnoughForObtainGate() {
        // Regression: any FGS active ≠ mediaProjection type (consent prepare uses dataSync).
        assertTrue(MediaProjectionFgsOrdering.requiresMediaProjectionTypeBeforeObtain(34))
        assertFalse(
            MediaProjectionFgsOrdering.canCallGetMediaProjection(
                34,
                mediaProjectionTypeActive = false,
            ),
        )
    }

    @Test
    fun consentStateDocs_matchP107Order() {
        // Warm path after process death still requires typed FGS before obtain once re-granted.
        assertTrue(
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH.blocksScreenShare(),
        )
        assertFalse(MediaProjectionConsentState.GRANTED_ACTIVE.blocksScreenShare())
    }
}
