package com.childmonitor.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Parent Screen Share must not open Cast when Accessibility is off.
 * Setup / USER_GRANT may still launch (manual Start during guided grant).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaProjectionConsentHelperGateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MediaProjectionTokenStore.resetEncryptedPrefsForTests()
        MediaProjectionTokenStore.clear(context)
        MediaProjectionRecoveryManager.resetForTests()
    }

    @After
    fun tearDown() {
        MediaProjectionTokenStore.clear(context)
    }

    @Test
    fun parentScreenShare_withoutAccessibility_doesNotGrant_andDoesNotHangForever() {
        // Accessibility is not enabled under Robolectric → PARENT path must refuse to launch.
        val granted = MediaProjectionConsentHelper.ensureConsent(
            context,
            timeoutMs = 500L,
            source = ConsentLaunchSource.PARENT_SCREEN_SHARE,
        )
        assertFalse(granted)
        assertFalse(MediaProjectionTokenStore.hasValidToken(context))
    }

    @Test
    fun hasConsent_false_whenNeverGranted() {
        assertFalse(MediaProjectionConsentHelper.hasConsent(context))
        assertTrue(
            MediaProjectionTokenStore.getConsentState(context) == MediaProjectionConsentState.NEVER_GRANTED ||
                !MediaProjectionTokenStore.hasValidToken(context),
        )
    }
}
