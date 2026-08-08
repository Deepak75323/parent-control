package com.childmonitor.oem

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HyperOS remaps REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to PowerDetailActivity
 * ("Battery details") with no Unrestricted control — Xiaomi must open app details instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OemBatteryOptimizationIntentTest {

    @Test
    fun xiaomiHyperOs_opensBatteryOptimisationListNotRequestIgnore() {
        val intent =
            OemCompatibilityManager.buildBatteryOptimizationIntentForOem(
                OemCompatibilityManager.OemType.XIAOMI_HYPEROS,
                "com.childmonitor",
            )
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intent.action)
    }

    @Test
    fun xiaomiMiui_opensBatteryOptimisationListNotRequestIgnore() {
        val intent =
            OemCompatibilityManager.buildBatteryOptimizationIntentForOem(
                OemCompatibilityManager.OemType.XIAOMI_MIUI,
                "com.childmonitor",
            )
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intent.action)
    }

    @Test
    fun genericAndroid_usesRequestIgnoreDialog() {
        val intent =
            OemCompatibilityManager.buildBatteryOptimizationIntentForOem(
                OemCompatibilityManager.OemType.GENERIC_ANDROID,
                "com.childmonitor",
            )
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertTrue(intent.data?.toString()?.contains("com.childmonitor") == true)
    }
}
