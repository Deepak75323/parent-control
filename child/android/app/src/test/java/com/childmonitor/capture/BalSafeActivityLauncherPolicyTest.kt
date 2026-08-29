package com.childmonitor.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalSafeActivityLauncherPolicyTest {

    @Test
    fun grantedOverlayIsTrustedEvenOnRestrictedXiaomi() {
        assertTrue(
            BalSafeActivityLauncher.shouldTrustDirectLaunch(
                overlayGranted = true,
                oemDropsBackgroundLaunches = true,
                normalDirectLaunchEligible = false,
            ),
        )
    }

    @Test
    fun missingOverlayDoesNotTrustDirectLaunchOnXiaomi() {
        assertFalse(
            BalSafeActivityLauncher.shouldTrustDirectLaunch(
                overlayGranted = false,
                oemDropsBackgroundLaunches = true,
                normalDirectLaunchEligible = true,
            ),
        )
    }

    @Test
    fun stockForegroundEligibleLaunchRemainsTrustedWithoutOverlay() {
        assertTrue(
            BalSafeActivityLauncher.shouldTrustDirectLaunch(
                overlayGranted = false,
                oemDropsBackgroundLaunches = false,
                normalDirectLaunchEligible = true,
            ),
        )
    }
}
