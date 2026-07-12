package com.childmonitor

import android.content.Context
import android.net.NetworkCapabilities
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.childmonitor.constants.PreferenceConstants
import com.childmonitor.monitoring.BootRecoveryTelemetry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * P1-03 regression tests — CONNECTIVITY_ACTION manifest receivers are dead on
 * Android 10+ (targetSdk ≥ 29). Recovery must use NetworkCallback semantics:
 * INTERNET + VALIDATED, transport labeling, and post-boot window gating.
 */
@RunWith(RobolectricTestRunner::class)
class NetworkRecoveryMonitorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NetworkRecoveryMonitor.resetForTests()
        context.getSharedPreferences("boot_recovery_telemetry", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(PreferenceConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        NetworkRecoveryMonitor.resetForTests()
        context.getSharedPreferences("boot_recovery_telemetry", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(PreferenceConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── Capability checks ─────────────────────────────────────────────────────

    @Test
    @Config(sdk = [29])
    fun isUsableInternet_requiresValidated_onAndroid10() {
        assertValidatedRequired()
    }

    @Test
    @Config(sdk = [30])
    fun isUsableInternet_android11() {
        assertValidatedRequired()
    }

    @Test
    @Config(sdk = [31])
    fun isUsableInternet_android12() {
        assertValidatedRequired()
    }

    @Test
    @Config(sdk = [33])
    fun isUsableInternet_android13() {
        assertValidatedRequired()
    }

    @Test
    @Config(sdk = [34])
    fun isUsableInternet_android14() {
        assertValidatedRequired()
    }

    @Test
    @Config(sdk = [35])
    fun isUsableInternet_android15() {
        assertValidatedRequired()
    }

    @Test
    @Config(sdk = [34])
    fun rejectsInternetCapabilityMissing() {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        assertFalse(NetworkRecoveryMonitor.isUsableInternet(caps))
    }

    // ── Transport labeling (Wi‑Fi / cellular / airplane restore diagnostics) ──

    @Test
    @Config(sdk = [34])
    fun describeTransport_wifi() {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        assertEquals("wifi", NetworkRecoveryMonitor.describeTransport(caps))
    }

    @Test
    @Config(sdk = [34])
    fun describeTransport_cellular() {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        assertEquals("cellular", NetworkRecoveryMonitor.describeTransport(caps))
    }

    @Test
    @Config(sdk = [34])
    fun describeTransport_ethernet() {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        assertEquals("ethernet", NetworkRecoveryMonitor.describeTransport(caps))
    }

    // ── Lifecycle / gating ────────────────────────────────────────────────────

    @Test
    @Config(sdk = [34])
    fun start_skippedWhenNotPaired() {
        setPaired(false)
        NetworkRecoveryMonitor.start(context, reason = "boot")
        assertFalse(NetworkRecoveryMonitor.isRegistered())
    }

    @Test
    @Config(sdk = [34])
    fun start_registersAfterBootTelemetry() {
        setPaired(true)
        BootRecoveryTelemetry.recordBootReceived(context, "BOOT")
        NetworkRecoveryMonitor.start(context, reason = "boot")
        assertTrue(
            "NetworkCallback must register after BOOT on Android 10+",
            NetworkRecoveryMonitor.isRegistered(),
        )
        NetworkRecoveryMonitor.stop(context)
        assertFalse(NetworkRecoveryMonitor.isRegistered())
    }

    @Test
    @Config(sdk = [29])
    fun start_registersOnAndroid10() {
        setPaired(true)
        BootRecoveryTelemetry.recordBootReceived(context, "BOOT")
        NetworkRecoveryMonitor.start(context, reason = "boot")
        assertTrue(NetworkRecoveryMonitor.isRegistered())
        NetworkRecoveryMonitor.stop(context)
    }

    @Test
    @Config(sdk = [35])
    fun start_registersOnAndroid15() {
        setPaired(true)
        BootRecoveryTelemetry.recordBootReceived(context, "BOOT")
        NetworkRecoveryMonitor.start(context, reason = "boot")
        assertTrue(NetworkRecoveryMonitor.isRegistered())
        NetworkRecoveryMonitor.stop(context)
    }

    @Test
    @Config(sdk = [34])
    fun start_skippedOutsidePostBootWindow() {
        setPaired(true)
        context.getSharedPreferences("boot_recovery_telemetry", Context.MODE_PRIVATE).edit()
            .putLong("boot_received_at_ms", System.currentTimeMillis() - 20 * 60_000L)
            .commit()
        NetworkRecoveryMonitor.start(context, reason = "app_start_post_boot")
        assertFalse(
            "Must not register outside the 10-minute post-boot window",
            NetworkRecoveryMonitor.isRegistered(),
        )
    }

    @Test
    @Config(sdk = [34])
    fun start_idempotent() {
        setPaired(true)
        BootRecoveryTelemetry.recordBootReceived(context, "BOOT")
        NetworkRecoveryMonitor.start(context, reason = "boot")
        NetworkRecoveryMonitor.start(context, reason = "boot_again")
        assertTrue(NetworkRecoveryMonitor.isRegistered())
        NetworkRecoveryMonitor.stop(context)
    }

    @Test
    fun withinPostBootWindow_trueAfterBoot() {
        BootRecoveryTelemetry.recordBootReceived(context, "BOOT")
        assertTrue(BootRecoveryTelemetry.isWithinPostBootWindow(context))
    }

    @Test
    @Config(sdk = [34])
    fun apiLevelAtLeastAndroid10() {
        assertTrue(Build.VERSION.SDK_INT >= 29)
    }

    private fun setPaired(paired: Boolean) {
        context.getSharedPreferences(PreferenceConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PreferenceConstants.KEY_PAIRED, paired)
            .commit()
    }

    private fun assertValidatedRequired() {
        val noValidated = ShadowNetworkCapabilities.newInstance()
        shadowOf(noValidated).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        assertFalse(NetworkRecoveryMonitor.isUsableInternet(noValidated))

        val validated = ShadowNetworkCapabilities.newInstance()
        val shadow = shadowOf(validated)
        shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        assertTrue(NetworkRecoveryMonitor.isUsableInternet(validated))
    }
}
