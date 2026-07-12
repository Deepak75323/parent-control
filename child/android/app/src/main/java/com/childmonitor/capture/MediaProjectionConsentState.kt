package com.childmonitor.capture

/**
 * Persisted MediaProjection consent lifecycle — binder tokens cannot survive process death.
 *
 * Binder objects inside the system consent Intent are OS-level handles and cannot be
 * serialized to disk or transmitted across process boundaries. When the app process dies
 * (low-memory kill, MIUI aggressive reclaim, battery-optimization stop, reboot), the
 * in-memory token becomes invalid. The only Android-supported recovery is to obtain a
 * fresh consent Intent by re-presenting the system dialog to the user.
 *
 * Transitions:
 * ```
 * NEVER_GRANTED    ──user grants──►  GRANTED_ACTIVE
 * GRANTED_ACTIVE   ──process death──► LOST_AFTER_PROCESS_DEATH
 * GRANTED_ACTIVE   ──onStop / token exhausted──► REQUIRES_RECONSENT
 * LOST_* / REQUIRES_RECONSENT ──recovery start──► RECOVERY_PENDING
 * LOST_* / REQUIRES_RECONSENT ──no accessibility──► RECOVERY_DEFERRED
 * RECOVERY_PENDING  ──success──► GRANTED_ACTIVE
 * RECOVERY_PENDING  ──timeout/deny──► RECOVERY_FAILED ──retry──► RECOVERY_PENDING
 * RECOVERY_DEFERRED ──accessibility ready / WorkManager fires──► RECOVERY_PENDING
 * RECOVERY_FAILED   ──max attempts──► stays RECOVERY_FAILED (parent must guide re-grant)
 * ```
 *
 * Device-specific notes:
 * - **MIUI/HyperOS**: BAL restrictions require accessibility-backed or full-screen-intent launch.
 * - **Samsung One UI**: Dialog titles differ per Android version; auto-approve handles this.
 * - **Android 14 (API 34)**: `getMediaProjection()` must be called inside a running
 *   `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` FGS.  The bootstrap sequence promotes
 *   a `DATA_SYNC` FGS first, then upgrades to `MEDIA_PROJECTION` after obtaining the token.
 * - **Android 15 (API 35)**: FGS timeout enforced; handled by [TypedStreamFgsTimeout].
 * - **Android 16 (API 36)**: One VirtualDisplay per MediaProjection; enforced by
 *   [MediaProjectionCaptureGate].
 */
enum class MediaProjectionConsentState {
    /** User has never granted screen capture on this device. */
    NEVER_GRANTED,

    /** Consent binder is alive in memory and a VirtualDisplay can be created immediately. */
    GRANTED_ACTIVE,

    /**
     * Process was killed (low-memory, MIUI reclaim, reboot) — the in-memory binder is gone.
     * Recovery requires re-presenting the system dialog to the user.
     */
    LOST_AFTER_PROCESS_DEATH,

    /**
     * [MediaProjection.onStop] fired during a non-intentional stop, or the consent token was
     * exhausted (getMediaProjection called twice). A fresh dialog is required.
     */
    REQUIRES_RECONSENT,

    /**
     * Recovery was triggered and the system dialog has been or is being shown.
     * The parent app should display a "waiting for child" spinner.
     */
    RECOVERY_PENDING,

    /**
     * Recovery was attempted but blocked — typically because accessibility is not enabled
     * or the BAL (Background Activity Launch) was denied.  A WorkManager job is scheduled
     * to re-attempt when the conditions improve.  The parent app should display
     * a "re-grant required" message with guidance.
     */
    RECOVERY_DEFERRED,

    /**
     * All recovery attempts exhausted.  Explicit parent guidance is required to ask the
     * child to open the app and re-grant screen capture.
     */
    RECOVERY_FAILED,
    ;

    /** True when screen share cannot start until the child re-approves capture. */
    fun blocksScreenShare(): Boolean = when (this) {
        GRANTED_ACTIVE -> false
        RECOVERY_PENDING -> true
        NEVER_GRANTED,
        LOST_AFTER_PROCESS_DEATH,
        REQUIRES_RECONSENT,
        RECOVERY_DEFERRED,
        RECOVERY_FAILED,
        -> true
    }

    /** True when parent should show an explicit re-consent message (not a generic spinner). */
    fun requiresParentReconsentMessage(): Boolean = when (this) {
        LOST_AFTER_PROCESS_DEATH,
        REQUIRES_RECONSENT,
        RECOVERY_DEFERRED,
        RECOVERY_FAILED,
        -> true
        NEVER_GRANTED -> true
        GRANTED_ACTIVE,
        RECOVERY_PENDING,
        -> false
    }

    /**
     * True when a WorkManager retry is the appropriate next step rather than an immediate
     * in-process attempt. Used to avoid scheduling redundant WorkManager jobs.
     */
    fun isAwaitingDeferredRecovery(): Boolean =
        this == RECOVERY_DEFERRED || this == RECOVERY_FAILED

    companion object {
        fun fromPersisted(name: String?): MediaProjectionConsentState =
            entries.firstOrNull { it.name == name } ?: NEVER_GRANTED
    }
}
