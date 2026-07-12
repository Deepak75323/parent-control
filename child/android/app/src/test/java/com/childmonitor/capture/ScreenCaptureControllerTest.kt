package com.childmonitor.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents consent-binder lifecycle: after intentional [ScreenCaptureController.releaseProjection],
 * [isConsentExhausted] must be false so the next stream can call getMediaProjection again.
 *
 * Full MediaProjection is not mocked here — regression guard for the detachProjection bug where
 * consentBinderUsed stayed true with activeProjection null.
 */
class ScreenCaptureControllerTest {

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
}
