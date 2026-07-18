package com.childmonitor.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Documents consent-binder lifecycle: after intentional [ScreenCaptureController.releaseProjection],
 * [isConsentExhausted] must be false so the next stream can call getMediaProjection again.
 *
 * Full MediaProjection is not mocked here — regression guard for the detachProjection bug where
 * consentBinderUsed stayed true with activeProjection null.
 *
 * Also guards the Android 14+ single-VirtualDisplay rule: detaching a capture surface must keep
 * the warm VirtualDisplay session so the next stream can reattach without calling
 * createVirtualDisplay again (which throws SecurityException).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenCaptureControllerTest {

    @Before
    fun reset() {
        ScreenCaptureController.onFreshConsentSaved()
    }

    @Test
    fun isConsentExhausted_falseWhenBinderNotUsed() {
        assertFalse(ScreenCaptureController.isConsentExhausted())
    }

    @Test
    fun isIntentionalProjectionStop_falseByDefault() {
        assertFalse(ScreenCaptureController.isIntentionalProjectionStop())
    }

    @Test
    fun onFreshConsentSaved_resetsExhaustedState() {
        ScreenCaptureController.onFreshConsentSaved()
        assertFalse(ScreenCaptureController.isConsentExhausted())
    }

    @Test
    fun onConsentInvalidated_clearsExhaustedState() {
        ScreenCaptureController.onConsentInvalidated()
        assertFalse(ScreenCaptureController.isConsentExhausted())
    }

    /**
     * After intentional [ScreenCaptureController.releaseProjection], binder must not read as
     * exhausted — otherwise capture paths invalidate the stored consent token.
     */
    @Test
    fun releaseProjection_clearsConsentExhaustedWhenNoActiveProjection() {
        ScreenCaptureController.onConsentInvalidated()
        ScreenCaptureController.releaseProjection()
        assertFalse(ScreenCaptureController.isConsentExhausted())
    }

    @Test
    fun projectionObtainedWithFgs_falseByDefault() {
        ScreenCaptureController.onFreshConsentSaved()
        assertFalse(ScreenCaptureController.projectionObtainedWithFgs())
    }

    @Test
    fun detachCaptureSurface_keepsWarmVirtualDisplaySession() {
        ScreenCaptureController.seedWarmVirtualDisplaySessionForTests()
        assertTrue(ScreenCaptureController.hasWarmVirtualDisplaySession())
        assertTrue(ScreenCaptureController.virtualDisplayActive())

        ScreenCaptureController.detachCaptureSurface()

        assertFalse(ScreenCaptureController.virtualDisplayActive())
        assertTrue(
            "Warm VD must survive stream stop — Android 14+ forbids a second createVirtualDisplay",
            ScreenCaptureController.hasWarmVirtualDisplaySession(),
        )
    }

    @Test
    fun releaseProjection_endsWarmVirtualDisplaySession() {
        ScreenCaptureController.seedWarmVirtualDisplaySessionForTests()
        ScreenCaptureController.releaseProjection()
        assertFalse(ScreenCaptureController.hasWarmVirtualDisplaySession())
        assertFalse(ScreenCaptureController.virtualDisplayActive())
    }

    @Test
    fun onFreshConsentSaved_endsWarmVirtualDisplaySession() {
        ScreenCaptureController.seedWarmVirtualDisplaySessionForTests()
        ScreenCaptureController.onFreshConsentSaved()
        assertFalse(ScreenCaptureController.hasWarmVirtualDisplaySession())
    }
}
