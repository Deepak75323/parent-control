package com.childmonitor

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceTelemetryReaderTest {

    @Test
    fun percentFromBatteryIntent_computesFromLevelAndScale() {
        val intent =
            Intent(Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(BatteryManager.EXTRA_LEVEL, 47)
                putExtra(BatteryManager.EXTRA_SCALE, 100)
            }

        assertEquals(47, DeviceTelemetryReader.percentFromBatteryIntent(intent))
    }

    @Test
    fun percentFromBatteryIntent_handlesNonHundredScale() {
        val intent =
            Intent(Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(BatteryManager.EXTRA_LEVEL, 50)
                putExtra(BatteryManager.EXTRA_SCALE, 200)
            }

        assertEquals(25, DeviceTelemetryReader.percentFromBatteryIntent(intent))
    }

    @Test
    fun percentFromBatteryIntent_returnsNullWhenLevelMissing() {
        assertNull(DeviceTelemetryReader.percentFromBatteryIntent(null))
        assertNull(
            DeviceTelemetryReader.percentFromBatteryIntent(
                Intent(Intent.ACTION_BATTERY_CHANGED).apply {
                    putExtra(BatteryManager.EXTRA_SCALE, 100)
                },
            ),
        )
    }

    @Test
    fun resolveBatteryPercent_prefersStickyOverStuckCapacityProperty() {
        // Xiaomi/HyperOS can report a stale BATTERY_PROPERTY_CAPACITY (e.g. 93)
        // while the sticky ACTION_BATTERY_CHANGED intent is correct.
        val resolved =
            DeviceTelemetryReader.resolveBatteryPercent(
                stickyPercent = 61,
                capacityProperty = 93,
                lastKnown = 93,
            )

        assertEquals(61, resolved)
    }

    @Test
    fun resolveBatteryPercent_fallsBackToCapacityWhenStickyUnavailable() {
        assertEquals(
            72,
            DeviceTelemetryReader.resolveBatteryPercent(
                stickyPercent = null,
                capacityProperty = 72,
                lastKnown = 40,
            ),
        )
    }

    @Test
    fun resolveBatteryPercent_fallsBackToLastKnownWhenBothSourcesInvalid() {
        assertEquals(
            55,
            DeviceTelemetryReader.resolveBatteryPercent(
                stickyPercent = null,
                capacityProperty = -1,
                lastKnown = 55,
            ),
        )
        assertNull(
            DeviceTelemetryReader.resolveBatteryPercent(
                stickyPercent = null,
                capacityProperty = Int.MIN_VALUE,
                lastKnown = null,
            ),
        )
    }
}
