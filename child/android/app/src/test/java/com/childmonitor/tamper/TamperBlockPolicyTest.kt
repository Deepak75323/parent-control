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
                tamperProtectionEnabled = true,
                tamperUnlocked = false,
                permissionSetupInProgress = false,
                deviceAdminSetupGraceActive = false,
            ),
        )
    }
}
