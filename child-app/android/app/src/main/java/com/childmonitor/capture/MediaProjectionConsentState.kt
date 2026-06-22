package com.childmonitor.capture

/**
 * Persisted MediaProjection consent lifecycle — binder tokens cannot survive process death.
 *
 * Transitions (simplified):
 * ```
 * NEVER_GRANTED ──grant──► GRANTED_ACTIVE
 * GRANTED_ACTIVE ──process death──► LOST_AFTER_PROCESS_DEATH
 * GRANTED_ACTIVE ──onStop / token exhausted──► REQUIRES_RECONSENT
 * LOST_* / REQUIRES_RECONSENT ──recovery start──► RECOVERY_PENDING
 * RECOVERY_PENDING ──success──► GRANTED_ACTIVE
 * RECOVERY_PENDING ──timeout/deny──► RECOVERY_FAILED ──retry──► RECOVERY_PENDING
 * ```
 */
enum class MediaProjectionConsentState {
    NEVER_GRANTED,
    GRANTED_ACTIVE,
    LOST_AFTER_PROCESS_DEATH,
    REQUIRES_RECONSENT,
    RECOVERY_PENDING,
    RECOVERY_FAILED,
    ;

    /** True when screen share cannot start until the child re-approves capture. */
    fun blocksScreenShare(): Boolean = when (this) {
        GRANTED_ACTIVE -> false
        RECOVERY_PENDING -> true
        NEVER_GRANTED,
        LOST_AFTER_PROCESS_DEATH,
        REQUIRES_RECONSENT,
        RECOVERY_FAILED,
        -> true
    }

    /** True when parent should show an explicit re-consent message (not a generic spinner). */
    fun requiresParentReconsentMessage(): Boolean = when (this) {
        LOST_AFTER_PROCESS_DEATH,
        REQUIRES_RECONSENT,
        RECOVERY_FAILED,
        -> true
        NEVER_GRANTED -> true
        GRANTED_ACTIVE,
        RECOVERY_PENDING,
        -> false
    }

    companion object {
        fun fromPersisted(name: String?): MediaProjectionConsentState =
            entries.firstOrNull { it.name == name } ?: NEVER_GRANTED
    }
}
