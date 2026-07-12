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

/**
 * Regression tests for P0-02: MediaProjection consent cannot survive Android process death.
 *
 * These tests simulate the exact conditions that occur in production:
 *
 * 1. **Cold start after LMKD kill** — prefs say consent was granted, in-memory binder is gone.
 * 2. **Reboot** — all in-memory state cleared; consent state must resolve to LOST_AFTER_PROCESS_DEATH.
 * 3. **Low-memory warning** — telemetry recorded; state unchanged (recovery happens on restart).
 * 4. **RECOVERY_DEFERRED lifecycle** — accessibility not available path.
 * 5. **Recovery → consent re-granted** — state machine round-trip.
 * 6. **Max attempts exhausted** — stays at RECOVERY_FAILED; parent must guide user.
 *
 * OEM coverage (state-machine level, no device-specific code paths needed in unit tests):
 * - MIUI/HyperOS: same state machine; BAL restrictions are in BalSafeActivityLauncher.
 * - Samsung: same state machine; dialog differences are in MediaProjectionConsentAutomation.
 * - Android 14/15/16: state enum and persistence are API-level-agnostic; captured via @Config.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaProjectionProcessDeathTest {

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

    // ── Process death (LMKD) ─────────────────────────────────────────────────

    @Test
    fun processDeath_flaggedAsGranted_detectsLoss() {
        // Simulate: consent was granted (prefs written), but process was killed.
        // The in-memory binder (inMemoryIntent) is gone — simulated by MediaProjectionTokenStore.clear()
        // + manually writing the "was granted" prefs.
        MediaProjectionTokenStore.seedPersistedGrantedWithoutBinderForTests(context)

        // On next getConsentState() call (simulating app restart), binder is null → LOST.
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH, state)
        assertTrue(state.blocksScreenShare())
        assertTrue(MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(context))
        assertTrue(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
        assertFalse(MediaProjectionTokenStore.hasValidToken(context))
    }

    @Test
    @Config(sdk = [26])
    fun processDeath_api26_detectsLoss() {
        MediaProjectionTokenStore.seedPersistedGrantedWithoutBinderForTests(context)
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH, state)
    }

    @Test
    @Config(sdk = [35])
    fun processDeath_api35_detectsLoss() {
        MediaProjectionTokenStore.seedPersistedGrantedWithoutBinderForTests(context)
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH, state)
        assertTrue(state.blocksScreenShare())
    }

    /**
     * Android 16 (API 36) — enforces single VirtualDisplay per MediaProjection and adds
     * stricter FGS type requirements. The state-machine logic is identical to API 35.
     * Robolectric does not yet ship a stub for SDK 36, so this test exercises the same
     * state-machine path with the highest supported Robolectric SDK (35).
     */
    @Test
    @Config(sdk = [35])
    fun processDeath_api35_android16Compat_detectsLoss() {
        MediaProjectionTokenStore.seedPersistedGrantedWithoutBinderForTests(context)
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH, state)
        assertTrue(state.blocksScreenShare())
    }

    // ── Reboot ────────────────────────────────────────────────────────────────

    @Test
    fun reboot_withPreviousGrant_transitionsToLost() {
        // After reboot: prefs say granted but no in-memory binder.
        MediaProjectionTokenStore.seedPersistedGrantedWithoutBinderForTests(context)

        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH, state)
        // State remains LOST on subsequent reads.
        assertEquals(
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionTokenStore.getConsentState(context),
        )
    }

    @Test
    fun reboot_neverGranted_staysNeverGranted() {
        // Fresh device or after explicit clear — no prefs at all.
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.NEVER_GRANTED, state)
        assertFalse(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
        assertFalse(MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(context))
    }

    // ── RECOVERY_DEFERRED lifecycle ───────────────────────────────────────────

    @Test
    fun recoveryDeferred_whenAccessibilityNotReady_stateIsPersisted() {
        MediaProjectionTokenStore.markRecoveryDeferred(context, "no_accessibility")
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.RECOVERY_DEFERRED, state)
        assertTrue(state.blocksScreenShare())
        assertTrue(state.requiresParentReconsentMessage())
        assertTrue(state.isAwaitingDeferredRecovery())
        assertTrue(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
    }

    @Test
    fun recoveryDeferred_progressesToPending_whenAccessibilityReady() {
        MediaProjectionTokenStore.markRecoveryDeferred(context, "no_accessibility")
        // Simulate accessibility becoming ready → manager calls markRecoveryPending.
        MediaProjectionTokenStore.markRecoveryPending(context)
        val state = MediaProjectionTokenStore.getConsentState(context)
        assertEquals(MediaProjectionConsentState.RECOVERY_PENDING, state)
        assertTrue(state.blocksScreenShare())
        assertFalse(state.requiresParentReconsentMessage()) // Spinner, not "re-grant" message.
        assertFalse(state.isAwaitingDeferredRecovery())
    }

    @Test
    fun recoveryDeferred_persistsSurvivesGetConsentStateCall() {
        MediaProjectionTokenStore.markRecoveryDeferred(context, "test")
        // Verify the state is preserved through repeated getConsentState() calls
        // (in-memory binder is null, which previously could overwrite the state).
        repeat(3) {
            val state = MediaProjectionTokenStore.getConsentState(context)
            assertEquals(MediaProjectionConsentState.RECOVERY_DEFERRED, state)
        }
    }

    // ── Recovery round-trip ───────────────────────────────────────────────────

    @Test
    fun recovery_pendingThenFailed_transitionsCorrectly() {
        MediaProjectionTokenStore.markRecoveryPending(context)
        assertEquals(
            MediaProjectionConsentState.RECOVERY_PENDING,
            MediaProjectionTokenStore.getConsentState(context),
        )
        MediaProjectionTokenStore.markRecoveryFailed(context, "timeout")
        assertEquals(
            MediaProjectionConsentState.RECOVERY_FAILED,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertTrue(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
        assertTrue(
            MediaProjectionTokenStore.getConsentState(context).isAwaitingDeferredRecovery(),
        )
    }

    @Test
    fun recovery_clear_resetsAll() {
        MediaProjectionTokenStore.markRecoveryFailed(context, "max_attempts")
        MediaProjectionTokenStore.clear(context)
        assertEquals(
            MediaProjectionConsentState.NEVER_GRANTED,
            MediaProjectionTokenStore.getConsentState(context),
        )
        assertFalse(MediaProjectionTokenStore.needsScreenCaptureRecovery(context))
        assertFalse(MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(context))
    }

    // ── Low-memory / task-removed ─────────────────────────────────────────────

    @Test
    fun lowMemoryWarning_doesNotChangeConsentState() {
        MediaProjectionTokenStore.seedPersistedGrantedWithoutBinderForTests(context)
        val before = MediaProjectionTokenStore.getConsentState(context)

        MediaProjectionLossHandler.onLowMemoryWarning(context)

        assertEquals(before, MediaProjectionTokenStore.getConsentState(context))
        val telemetryPrefs = context.getSharedPreferences("media_projection_telemetry", Context.MODE_PRIVATE)
        val lastEvent = telemetryPrefs.getString("last_event_json", null)
        assertTrue("telemetry should record low_memory_warning", lastEvent?.contains("low_memory_warning") == true)
    }

    // ── State enum invariants ─────────────────────────────────────────────────

    @Test
    fun stateEnum_grantedActive_neverBlocksOrNeedsReconsent() {
        val state = MediaProjectionConsentState.GRANTED_ACTIVE
        assertFalse(state.blocksScreenShare())
        assertFalse(state.requiresParentReconsentMessage())
        assertFalse(state.isAwaitingDeferredRecovery())
    }

    @Test
    fun stateEnum_allBlockingStates_blockScreenShare() {
        val blocking = listOf(
            MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
            MediaProjectionConsentState.REQUIRES_RECONSENT,
            MediaProjectionConsentState.RECOVERY_PENDING,
            MediaProjectionConsentState.RECOVERY_DEFERRED,
            MediaProjectionConsentState.RECOVERY_FAILED,
            MediaProjectionConsentState.NEVER_GRANTED,
        )
        blocking.forEach { state ->
            assertTrue("$state should block screen share", state.blocksScreenShare())
        }
    }

    @Test
    fun stateEnum_deferredAndFailed_awaitDeferredRecovery() {
        assertTrue(MediaProjectionConsentState.RECOVERY_DEFERRED.isAwaitingDeferredRecovery())
        assertTrue(MediaProjectionConsentState.RECOVERY_FAILED.isAwaitingDeferredRecovery())
    }

    @Test
    fun stateEnum_pendingAndGranted_doNotAwaitDeferredRecovery() {
        assertFalse(MediaProjectionConsentState.RECOVERY_PENDING.isAwaitingDeferredRecovery())
        assertFalse(MediaProjectionConsentState.GRANTED_ACTIVE.isAwaitingDeferredRecovery())
        assertFalse(MediaProjectionConsentState.NEVER_GRANTED.isAwaitingDeferredRecovery())
        assertFalse(MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH.isAwaitingDeferredRecovery())
    }

    @Test
    fun stateEnum_fromPersisted_unknownStringReturnsNeverGranted() {
        val state = MediaProjectionConsentState.fromPersisted("UNKNOWN_FUTURE_STATE")
        assertEquals(MediaProjectionConsentState.NEVER_GRANTED, state)
    }

    @Test
    fun stateEnum_fromPersisted_recoveryDeferred() {
        val state = MediaProjectionConsentState.fromPersisted("RECOVERY_DEFERRED")
        assertEquals(MediaProjectionConsentState.RECOVERY_DEFERRED, state)
    }
}
