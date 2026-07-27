package com.childmonitor.tamper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePinLockoutPolicyTest {

    @Test
    fun locksAfterFiveFailures_withOneMinuteBackoff() {
        var state = OfflinePinLockoutPolicy.State()
        val now = 1_000_000L
        repeat(4) {
            val r = OfflinePinLockoutPolicy.recordFailure(state, now)
            assertEquals(OfflinePinLockoutPolicy.FailureAction.NONE, r.action)
            state = r.state
        }
        val locked = OfflinePinLockoutPolicy.recordFailure(state, now)
        assertEquals(OfflinePinLockoutPolicy.FailureAction.LOCKED, locked.action)
        assertEquals(60_000L, locked.lockDurationMs)
        assertTrue(OfflinePinLockoutPolicy.isLocked(locked.state, now + 30_000L))
        assertFalse(OfflinePinLockoutPolicy.isLocked(locked.state, now + 60_001L))
    }

    @Test
    fun exponentialBackoffIncreasesOnRepeatedLockouts() {
        var state = OfflinePinLockoutPolicy.State()
        val t0 = 0L
        repeat(5) { state = OfflinePinLockoutPolicy.recordFailure(state, t0).state }
        assertEquals(1, state.lockoutLevel)
        // after lock expires, next 5 → 2 minutes
        val t1 = state.lockUntilMs + 1
        state = state.copy(failsSinceLock = 0) // already 0 after lock
        repeat(5) { state = OfflinePinLockoutPolicy.recordFailure(state, t1).state }
        assertEquals(2, state.lockoutLevel)
        assertEquals(2 * 60_000L, OfflinePinLockoutPolicy.lockDurationMs(1))
    }

    @Test
    fun budgetExceededAfterThirtyFailures() {
        var state = OfflinePinLockoutPolicy.State()
        var last = OfflinePinLockoutPolicy.FailureResult(state, OfflinePinLockoutPolicy.FailureAction.NONE)
        var now = 0L
        repeat(30) {
            if (OfflinePinLockoutPolicy.isLocked(state, now)) {
                now = state.lockUntilMs + 1
            }
            last = OfflinePinLockoutPolicy.recordFailure(state, now)
            state = last.state
        }
        assertEquals(OfflinePinLockoutPolicy.FailureAction.BUDGET_EXCEEDED, last.action)
        assertEquals(30, state.lifetimeFails)
    }

    @Test
    fun verifierFreshnessWindow() {
        val now = 10_000_000L
        assertFalse(OfflinePinLockoutPolicy.isVerifierFresh(null, now))
        assertTrue(OfflinePinLockoutPolicy.isVerifierFresh(now - 1000, now))
        assertFalse(
            OfflinePinLockoutPolicy.isVerifierFresh(
                now - OfflinePinLockoutPolicy.VERIFIER_MAX_AGE_MS - 1,
                now,
            ),
        )
    }

    @Test
    fun clearOnSuccessResetsCountersButKeepsPendingReport() {
        val state = OfflinePinLockoutPolicy.State(
            failsSinceLock = 3,
            lockoutLevel = 2,
            lifetimeFails = 10,
            pendingReportFails = 4,
        )
        val cleared = OfflinePinLockoutPolicy.clearOnSuccess(state)
        assertEquals(0, cleared.failsSinceLock)
        assertEquals(0, cleared.lockoutLevel)
        assertEquals(0, cleared.lifetimeFails)
        assertEquals(4, cleared.pendingReportFails)
    }
}
