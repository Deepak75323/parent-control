package com.childmonitor.tamper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperBlockPolicyTest {

    @Test
    fun blocksWhenAdminActiveAndLocked() {
        assertTrue(
            TamperBlockPolicy.shouldBlockSettingsInteraction(
                deviceAdminActive = true,
                paired = true,
                tamperProtectionEnabled = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
                deviceAdminSetupGraceActive = false,
            ),
        )
    }

    @Test
    fun doesNotBlockWhenUnpaired() {
        assertFalse(
            TamperBlockPolicy.shouldBlockSettingsInteraction(
                deviceAdminActive = true,
                paired = false,
                tamperProtectionEnabled = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
                deviceAdminSetupGraceActive = false,
            ),
        )
    }

    @Test
    fun doesNotBlockDuringPermissionWizard() {
        assertFalse(
            TamperBlockPolicy.shouldBlockSettingsInteraction(
                deviceAdminActive = true,
                paired = true,
                tamperProtectionEnabled = true,
                tamperUnlocked = false,
                permissionSetupInProgress = true,
                deviceAdminSetupGraceActive = false,
            ),
        )
    }

    @Test
    fun doesNotBlockDuringDeviceAdminSetupGrace() {
        assertFalse(
            TamperBlockPolicy.shouldBlockSettingsInteraction(
                deviceAdminActive = true,
                paired = true,
                tamperProtectionEnabled = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
                deviceAdminSetupGraceActive = true,
            ),
        )
    }

    @Test
    fun doesNotBlockWhenTamperUnlocked() {
        assertFalse(
            TamperBlockPolicy.shouldBlockSettingsInteraction(
                deviceAdminActive = true,
                paired = true,
                tamperProtectionEnabled = true,
                tamperUnlocked = true,
                permissionSetupInProgress = false,
                deviceAdminSetupGraceActive = false,
            ),
        )
    }

    @Test
    fun doesNotBlockWhenAdminInactive() {
        assertFalse(
            TamperBlockPolicy.shouldBlockSettingsInteraction(
                deviceAdminActive = false,
                paired = true,
                tamperProtectionEnabled = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
                deviceAdminSetupGraceActive = false,
            ),
        )
    }

    @Test
    fun tamperGateOpensWhenPairedAndLocked() {
        assertTrue(
            TamperBlockPolicy.shouldOpenDeviceAdminTamperGate(
                deviceAdminTamperEnabled = true,
                paired = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
                deviceAdminSetupGraceActive = false,
            ),
        )
    }

    @Test
    fun tamperGateSkippedWhenUnpaired() {
        assertFalse(
            TamperBlockPolicy.shouldOpenDeviceAdminTamperGate(
                deviceAdminTamperEnabled = true,
                paired = false,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
                deviceAdminSetupGraceActive = false,
            ),
        )
    }

    @Test
    fun deviceAdminTamperGateSkippedWhenToggleOff() {
        assertFalse(
            TamperBlockPolicy.shouldOpenDeviceAdminTamperGate(
                deviceAdminTamperEnabled = false,
                paired = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
                deviceAdminSetupGraceActive = false,
            ),
        )
    }

    @Test
    fun blocksAccessibilityWhenToggleOnAndPaired() {
        assertTrue(
            TamperBlockPolicy.shouldBlockAccessibilityDisable(
                accessibilityEnabled = true,
                accessibilityTamperEnabled = true,
                paired = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
            ),
        )
    }

    @Test
    fun doesNotBlockAccessibilityWhenToggleOff() {
        assertFalse(
            TamperBlockPolicy.shouldBlockAccessibilityDisable(
                accessibilityEnabled = true,
                accessibilityTamperEnabled = false,
                paired = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
            ),
        )
    }

    @Test
    fun accessibilityTamperGateSkippedWhenToggleOff() {
        assertFalse(
            TamperBlockPolicy.shouldOpenAccessibilityTamperGate(
                accessibilityTamperEnabled = false,
                paired = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
            ),
        )
    }

    @Test
    fun clearsUnlockWhenLeavingSettingsAfterSeeingIt() {
        assertTrue(
            TamperBlockPolicy.shouldClearUnlockAfterLeavingSettings(
                tamperUnlocked = true,
                protectionStillActive = true,
                sawSettingsWhileUnlocked = true,
                packageIsSettings = false,
                packageIsOurApp = false,
            ),
        )
    }

    @Test
    fun keepsUnlockWhileStillInSettings() {
        assertFalse(
            TamperBlockPolicy.shouldClearUnlockAfterLeavingSettings(
                tamperUnlocked = true,
                protectionStillActive = true,
                sawSettingsWhileUnlocked = true,
                packageIsSettings = true,
                packageIsOurApp = false,
            ),
        )
    }

    @Test
    fun keepsUnlockWhileInOurApp() {
        assertFalse(
            TamperBlockPolicy.shouldClearUnlockAfterLeavingSettings(
                tamperUnlocked = true,
                protectionStillActive = true,
                sawSettingsWhileUnlocked = true,
                packageIsSettings = false,
                packageIsOurApp = true,
            ),
        )
    }
}
