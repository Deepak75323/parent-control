package com.childmonitor.tamper

/**
 * Pure policy for when Settings/accessibility anti-tamper should intervene.
 * Extracted for unit tests — keep in sync with [TamperGateController] usage.
 */
object TamperBlockPolicy {

    /**
     * @param deviceAdminActive our Device Admin is active
     * @param paired device linked to a parent account (pairing code completed)
     * @param tamperProtectionEnabled prefs flag set when admin is enabled
     * @param tamperUnlocked temporary parent unlock window
     * @param permissionSetupInProgress JS permission wizard is active
     * @param deviceAdminSetupGraceActive short window after requesting/enabling admin
     *   so post-activate Settings UI is not treated as a deactivate attempt
     */
    fun shouldBlockSettingsInteraction(
        deviceAdminActive: Boolean,
        paired: Boolean,
        tamperProtectionEnabled: Boolean,
        tamperUnlocked: Boolean,
        permissionSetupInProgress: Boolean,
        deviceAdminSetupGraceActive: Boolean,
    ): Boolean {
        if (!deviceAdminActive) return false
        // Incomplete setup: permissions may be granted before pairing — allow undo without parent PIN.
        if (!paired) return false
        if (!tamperProtectionEnabled) return false
        if (tamperUnlocked) return false
        // First-enable / wizard: Settings still shows "Deactivate" — do not open TamperGate.
        if (permissionSetupInProgress) return false
        if (deviceAdminSetupGraceActive) return false
        return true
    }

    /** Whether TamperGate should open for device-admin disable / settings bypass. */
    fun shouldOpenDeviceAdminTamperGate(
        deviceAdminTamperEnabled: Boolean,
        paired: Boolean,
        tamperUnlocked: Boolean,
        permissionSetupInProgress: Boolean,
        deviceAdminSetupGraceActive: Boolean,
    ): Boolean {
        if (!deviceAdminTamperEnabled) return false
        if (!paired) return false
        if (tamperUnlocked) return false
        if (permissionSetupInProgress) return false
        if (deviceAdminSetupGraceActive) return false
        return true
    }

    /**
     * Whether accessibility disable should be blocked in Settings.
     * Independent of device-admin tamper — controlled by the in-app toggle.
     */
    fun shouldBlockAccessibilityDisable(
        accessibilityEnabled: Boolean,
        accessibilityTamperEnabled: Boolean,
        paired: Boolean,
        tamperUnlocked: Boolean,
        permissionSetupInProgress: Boolean,
    ): Boolean {
        if (!accessibilityEnabled) return false
        if (!accessibilityTamperEnabled) return false
        if (!paired) return false
        if (tamperUnlocked) return false
        if (permissionSetupInProgress) return false
        return true
    }

    fun shouldOpenAccessibilityTamperGate(
        accessibilityTamperEnabled: Boolean,
        paired: Boolean,
        tamperUnlocked: Boolean,
        permissionSetupInProgress: Boolean,
    ): Boolean {
        if (!accessibilityTamperEnabled) return false
        if (!paired) return false
        if (tamperUnlocked) return false
        if (permissionSetupInProgress) return false
        return true
    }

    /**
     * After parent PIN unlock, Settings can be opened once. Leaving Settings (or any
     * non-Settings / non-app package) while protection is still active must clear the
     * unlock so the next visit requires the password again.
     */
    fun shouldClearUnlockAfterLeavingSettings(
        tamperUnlocked: Boolean,
        protectionStillActive: Boolean,
        sawSettingsWhileUnlocked: Boolean,
        packageIsSettings: Boolean,
        packageIsOurApp: Boolean,
    ): Boolean {
        if (!tamperUnlocked) return false
        if (!protectionStillActive) return false
        if (!sawSettingsWhileUnlocked) return false
        if (packageIsSettings || packageIsOurApp) return false
        return true
    }
}
