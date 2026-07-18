package com.childmonitor.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperSettingsHeuristicsTest {

    @Test
    fun deviceAdmin_miuiShortDeactivateButton_matchesWhenOurAppPresent() {
        val text =
            "clock time device admin apps deactivate this admin app can no longer control this device deactivate"
        assertTrue(
            TamperSettingsHeuristics.looksLikeOurDeviceAdminDeactivatePage(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
            ),
        )
    }

    @Test
    fun deviceAdmin_otherAdminApp_doesNotMatch() {
        val text = "google find my device device administrator deactivate"
        assertFalse(
            TamperSettingsHeuristics.looksLikeOurDeviceAdminDeactivatePage(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
            ),
        )
    }

    @Test
    fun accessibility_downloadedAppsList_matchesWithoutToggle() {
        val text =
            "downloaded apps clock time on interconnectivity off link to windows off screenshot off"
        assertTrue(
            TamperSettingsHeuristics.looksLikeOurAccessibilityServicesList(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
            ),
        )
        assertTrue(
            TamperSettingsHeuristics.looksLikeOurAccessibilityDisablePage(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
                hasToggle = false,
            ),
        )
    }

    @Test
    fun accessibility_downloadedAppsList_matchesWithSwitchesOnlyNoOnOffWords() {
        // MIUI list rows often use Switch widgets with no "On"/"Off" text nodes.
        val text = "downloaded apps clock time interconnectivity link to windows screenshot"
        assertTrue(
            TamperSettingsHeuristics.looksLikeOurAccessibilityServicesList(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
                hasToggle = true,
            ),
        )
    }

    @Test
    fun accessibility_downloadedAppsList_matchesByWindowTitleWhenBodyOmitsTitle() {
        val text = "clock time interconnectivity screenshot"
        assertTrue(
            TamperSettingsHeuristics.looksLikeOurAccessibilityServicesList(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
                hasToggle = true,
                windowTitleLower = "downloaded apps",
            ),
        )
    }

    @Test
    fun accessibility_downloadedAppsSurface_matchesTruncatedMiuiWindowTitleWithoutRows() {
        // MIUI a11y tree often only exposes Back + title; service rows are invisible.
        assertTrue(
            TamperSettingsHeuristics.looksLikeAccessibilityDownloadedAppsSurface("downloaded"),
        )
        assertTrue(
            TamperSettingsHeuristics.looksLikeOurAccessibilityServicesList(
                combinedLowerText = "back downloaded",
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
                windowTitleLower = "downloaded",
            ),
        )
    }

    @Test
    fun accessibility_parentAccessibilityScreen_doesNotMatchAsDownloadedSurface() {
        assertFalse(
            TamperSettingsHeuristics.looksLikeAccessibilityDownloadedAppsSurface("accessibility"),
        )
    }

    @Test
    fun accessibility_detailPageWithoutWordAccessibility_matchesWithToggle() {
        // MIUI service detail: app name + description + switch, often no "accessibility" string.
        val text = "clock time required for clock time features. turn on once during setup."
        assertTrue(
            TamperSettingsHeuristics.looksLikeOurAccessibilityDisablePage(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
                hasToggle = true,
            ),
        )
    }

    @Test
    fun accessibility_unrelatedSettingsList_doesNotMatch() {
        val text = "bluetooth devices headphones connected"
        assertFalse(
            TamperSettingsHeuristics.looksLikeOurAccessibilityServicesList(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
            ),
        )
    }

    @Test
    fun accessibility_deviceAdminPage_doesNotMatch() {
        val text = "clock time device administrator deactivate"
        assertFalse(
            TamperSettingsHeuristics.looksLikeOurAccessibilityDisablePage(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
                hasToggle = true,
            ),
        )
    }

    @Test
    fun adminDisableWarningDialog_matchesSystemCopy() {
        val text =
            "parent verification is required before device protection can be turned off. cancel ok"
        assertTrue(
            TamperSettingsHeuristics.looksLikeAdminDisableWarningDialog(
                text,
                "parent verification is required before device protection can be turned off.",
            ),
        )
        assertTrue(TamperSettingsHeuristics.isCancelLabel("Cancel"))
        assertFalse(TamperSettingsHeuristics.isCancelLabel("OK"))
    }

    @Test
    fun adminDisableWarningDialog_matchesPartialCopyWithoutFullWarningString() {
        val text = "parent verification is required cancel ok"
        assertTrue(
            TamperSettingsHeuristics.looksLikeAdminDisableWarningDialog(text, ""),
        )
    }

    @Test
    fun deviceAdmin_deactivateThisDeviceAdminApp_matches() {
        val text =
            "device admin app clock time this admin app is active deactivate this device admin app cancel"
        assertTrue(
            TamperSettingsHeuristics.looksLikeOurDeviceAdminDeactivatePage(
                combinedLowerText = text,
                ourAppLabelLower = "clock time",
                ourPackageLower = "com.childmonitor",
            ),
        )
    }
}
