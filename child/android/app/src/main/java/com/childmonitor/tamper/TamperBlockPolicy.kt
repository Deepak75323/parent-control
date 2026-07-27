package com.childmonitor.tamper

/**
 * Pure policy for when Settings/accessibility anti-tamper should intervene.
 * Extracted for unit tests — keep in sync with [TamperGateController] usage.
 */
object TamperBlockPolicy {

    /**
     * @param deviceAdminActive our Device Admin is active
     * @param tamperProtectionEnabled prefs flag set when admin is enabled
     * @param tamperUnlocked temporary parent unlock window
     * @param permissionSetupInProgress JS permission wizard is active
     * @param deviceAdminSetupGraceActive short window after requesting/enabling admin
     *   so post-activate Settings UI is not treated as a deactivate attempt
     */
    fun shouldBlockSettingsInteraction(
        deviceAdminActive: Boolean,
        tamperProtectionEnabled: Boolean,
        tamperUnlocked: Boolean,
        permissionSetupInProgress: Boolean,
        deviceAdminSetupGraceActive: Boolean,
    ): Boolean {
        if (!deviceAdminActive) return false
        if (!tamperProtectionEnabled) return false
        if (tamperUnlocked) return false
        // First-enable / wizard: Settings still shows "Deactivate" — do not open TamperGate.
        if (permissionSetupInProgress) return false
        if (deviceAdminSetupGraceActive) return false
        return true
    }
}
