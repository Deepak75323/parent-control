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
    fun loadIntent_triggersRecoveryWhenBinderLostAfterProcessDeath() {
        context.getSharedPreferences("media_projection_secure", Context.MODE_PRIVATE).edit()
            .putBoolean("consent_granted", true)
            .putBoolean("ever_granted", true)
            .putInt("result_code", -1)
            .commit()

        assertEquals(null, MediaProjectionTokenStore.loadIntent(context))
        assertEquals(
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertFalse(MediaProjectionTokenStore.hasValidToken(context))
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

    // ── RECOVERY_DEFERRED ─────────────────────────────────────────────────────

    @Test
    fun recoveryDeferred_persistedAndRecognised() {
        MediaProjectionTokenStore.markRecoveryDeferred(context, "accessibility_unavailable")
        assertEquals(
            MediaProjectionConsentState.RECOVERY_DEFERRED,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertFalse(MediaProjectionTokenStore.hasValidToken(context))
        assertTrue(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
    }

    @Test
    fun recoveryDeferred_blocksScreenShare() {
        MediaProjectionTokenStore.markRecoveryDeferred(context, "test")
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertTrue(state.blocksScreenShare())
        assertTrue(state.requiresParentReconsentMessage())
        assertTrue(state.isAwaitingDeferredRecovery())
    }

    @Test
    fun recoveryDeferred_transitionsToRecoveryPending() {
        MediaProjectionTokenStore.markRecoveryDeferred(context, "test")
        // Simulating accessibility becoming ready → manager calls markRecoveryPending
        MediaProjectionTokenStore.markRecoveryPending(context)
        assertEquals(
            MediaProjectionConsentState.RECOVERY_PENDING,
            MediaProjectionTokenStore.getConsentState(context),
        )
        // RECOVERY_PENDING: blocks screen share but does NOT require parent re-consent message
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertTrue(state.blocksScreenShare())
        assertFalse(state.requiresParentReconsentMessage())
    }

    // ── State machine invariants ──────────────────────────────────────────────

    @Test
    fun neverGranted_doesNotNeedRecovery() {
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.NEVER_GRANTED, state)
        assertFalse(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
        assertFalse(state.isAwaitingDeferredRecovery())
    }

    @Test
    fun lostAfterProcessDeath_needsRecovery() {
        context.getSharedPreferences("media_projection_secure", Context.MODE_PRIVATE).edit()
            .putBoolean("consent_granted", true)
            .putBoolean("ever_granted", true)
            .putInt("result_code", -1)
            .commit()
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH, state)
        assertTrue(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
        assertFalse(state.isAwaitingDeferredRecovery())
    }

    @Test
    fun clear_resetsToNeverGranted() {
        MediaProjectionTokenStore.markRecoveryDeferred(context, "test")
        MediaProjectionTokenStore.clear(context)
        assertEquals(
            MediaProjectionConsentState.NEVER_GRANTED,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertFalse(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
    }

    @Test
    fun debugState_includesNewFields() {
        MediaProjectionTokenStore.markRecoveryDeferred(context, "test")
        val debug = MediaProjectionTokenStore.debugState(context)
        assertTrue(debug.containsKey("blocksScreenShare"))
        assertTrue(debug.containsKey("requiresParentMessage"))
        assertTrue(debug.containsKey("isAwaitingDeferredRecovery"))
        assertEquals(true, debug["blocksScreenShare"])
        assertEquals(true, debug["requiresParentMessage"])
        assertEquals(true, debug["isAwaitingDeferredRecovery"])
    }

    // ── Android versions ─────────────────────────────────────────────────────

    @Test
    @Config(sdk = [26])
    fun lostAfterProcessDeath_api26() {
        context.getSharedPreferences("media_projection_secure", Context.MODE_PRIVATE).edit()
            .putBoolean("consent_granted", true)
            .putInt("result_code", -1)
            .commit()
        assertEquals(
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionTokenStore.getConsentState(context),
        )
    }

    @Test
    @Config(sdk = [35])
    fun recoveryDeferred_api35() {
        MediaProjectionTokenStore.markRecoveryDeferred(context, "api35_test")
        assertEquals(
            MediaProjectionConsentState.RECOVERY_DEFERRED,
            MediaProjectionTokenStore.getConsentState(context),
        )
    }
}
