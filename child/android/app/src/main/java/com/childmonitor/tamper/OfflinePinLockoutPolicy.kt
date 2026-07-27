package com.childmonitor.tamper

/**
 * Pure policy for offline parent-password unlock attempts.
 *
 * - After [FAILS_BEFORE_LOCK] wrong guesses → lock with exponential backoff
 * - After [LIFETIME_FAIL_BUDGET] wrong guesses since last success → wipe verifier
 * - Verifier older than [VERIFIER_MAX_AGE_MS] cannot be used offline
 */
object OfflinePinLockoutPolicy {

    const val FAILS_BEFORE_LOCK = 5
    const val LIFETIME_FAIL_BUDGET = 30
    const val VERIFIER_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    data class State(
        val failsSinceLock: Int = 0,
        val lockUntilMs: Long = 0L,
        val lockoutLevel: Int = 0,
        val lifetimeFails: Int = 0,
        val pendingReportFails: Int = 0,
    )

    enum class FailureAction {
        NONE,
        LOCKED,
        BUDGET_EXCEEDED,
    }

    data class FailureResult(
        val state: State,
        val action: FailureAction,
        val lockDurationMs: Long = 0L,
    )

    fun isLocked(state: State, nowMs: Long): Boolean =
        state.lockUntilMs > nowMs

    fun remainingLockMs(state: State, nowMs: Long): Long =
        (state.lockUntilMs - nowMs).coerceAtLeast(0L)

    fun isVerifierFresh(syncedAtMs: Long?, nowMs: Long): Boolean {
        if (syncedAtMs == null || syncedAtMs <= 0L) return false
        return nowMs - syncedAtMs <= VERIFIER_MAX_AGE_MS
    }

    fun lockDurationMs(lockoutLevel: Int): Long {
        // level 0 → 1 min, 1 → 2 min, 2 → 4 … capped at 15 min
        val minutes = (1L shl lockoutLevel.coerceIn(0, 4)).coerceAtMost(15L)
        return minutes * 60_000L
    }

    fun recordFailure(state: State, nowMs: Long): FailureResult {
        if (isLocked(state, nowMs)) {
            return FailureResult(state = state, action = FailureAction.NONE)
        }
        val lifetime = state.lifetimeFails + 1
        val pending = state.pendingReportFails + 1
        if (lifetime >= LIFETIME_FAIL_BUDGET) {
            return FailureResult(
                state = State(
                    failsSinceLock = 0,
                    lockUntilMs = 0L,
                    lockoutLevel = 0,
                    lifetimeFails = lifetime,
                    pendingReportFails = pending,
                ),
                action = FailureAction.BUDGET_EXCEEDED,
            )
        }
        val fails = state.failsSinceLock + 1
        if (fails >= FAILS_BEFORE_LOCK) {
            val duration = lockDurationMs(state.lockoutLevel)
            return FailureResult(
                state = state.copy(
                    failsSinceLock = 0,
                    lockUntilMs = nowMs + duration,
                    lockoutLevel = state.lockoutLevel + 1,
                    lifetimeFails = lifetime,
                    pendingReportFails = pending,
                ),
                action = FailureAction.LOCKED,
                lockDurationMs = duration,
            )
        }
        return FailureResult(
            state = state.copy(
                failsSinceLock = fails,
                lifetimeFails = lifetime,
                pendingReportFails = pending,
            ),
            action = FailureAction.NONE,
        )
    }

    fun clearOnSuccess(state: State): State =
        State(pendingReportFails = state.pendingReportFails)

    fun consumePendingReport(state: State): State =
        state.copy(pendingReportFails = 0)
}
