package com.childmonitor.security

import android.content.Context
import android.os.Build
import android.os.Debug
import android.provider.Settings
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Socket
import java.util.Locale

/**
 * Collects local device trust signals (defense-in-depth alongside Play Integrity).
 * Signals are advisory — server scoring is authoritative.
 */
object DeviceTrustSignalsCollector {

    const val VERSION = "1.0.0"

    data class Signals(
        val rootDetected: Boolean,
        val magiskDetected: Boolean,
        val fridaDetected: Boolean,
        val emulatorDetected: Boolean,
        val debuggerAttached: Boolean,
        val hooksDetected: Boolean,
        val testKeysBuild: Boolean,
        val collectorVersion: String = VERSION,
    )

    fun collect(context: Context): Signals = Signals(
        rootDetected = detectRoot(),
        magiskDetected = detectMagisk(),
        fridaDetected = detectFrida(),
        emulatorDetected = detectEmulator(context),
        debuggerAttached = Debug.isDebuggerConnected(),
        hooksDetected = detectHooks(),
        testKeysBuild = detectTestKeys(),
    )

    fun toJsonMap(signals: Signals): Map<String, Any?> = mapOf(
        "rootDetected" to signals.rootDetected,
        "magiskDetected" to signals.magiskDetected,
        "fridaDetected" to signals.fridaDetected,
        "emulatorDetected" to signals.emulatorDetected,
        "debuggerAttached" to signals.debuggerAttached,
        "hooksDetected" to signals.hooksDetected,
        "testKeysBuild" to signals.testKeysBuild,
        "collectorVersion" to signals.collectorVersion,
    )

    private fun detectRoot(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
        )
        if (paths.any { File(it).exists() }) return true
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            process.inputStream.bufferedReader().readLine()?.isNotBlank() == true
        } catch (_: Exception) {
            false
        }
    }

    private fun detectMagisk(): Boolean {
        val indicators = listOf(
            "/sbin/.magisk",
            "/data/adb/magisk",
            "/data/adb/magisk.db",
            "/cache/magisk.log",
        )
        if (indicators.any { File(it).exists() }) return true
        return Build.TAGS?.contains("test-keys") == true && File("/sbin").list()?.contains("magisk") == true
    }

    private fun detectFrida(): Boolean {
        if (detectFridaPort()) return true
        return try {
            BufferedReader(FileReader("/proc/self/maps")).use { reader ->
                reader.lineSequence().any { line ->
                    line.contains("frida", ignoreCase = true) ||
                        line.contains("gadget", ignoreCase = true) ||
                        line.contains("linjector", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun detectFridaPort(): Boolean = try {
        Socket("127.0.0.1", 27042).use { true }
    } catch (_: Exception) {
        false
    }

    private fun detectEmulator(context: Context): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        val hardware = Build.HARDWARE.lowercase(Locale.US)
        val product = Build.PRODUCT.lowercase(Locale.US)
        if (fingerprint.contains("generic") || fingerprint.contains("emulator") || fingerprint.contains("sdk_gphone")) {
            return true
        }
        if (model.contains("emulator") || model.contains("android sdk built for")) return true
        if (hardware.contains("goldfish") || hardware.contains("ranchu") || hardware.contains("qemu")) return true
        if (product.contains("sdk") || product.contains("google_sdk") || product.contains("sdk_gphone")) return true
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId == "9774d56d682e549c"
    }

    private fun detectHooks(): Boolean {
        return try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (_: ClassNotFoundException) {
            try {
                Class.forName("org.lsposed.lspd.core.Main")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }

    private fun detectTestKeys(): Boolean {
        val tags = Build.TAGS?.lowercase(Locale.US) ?: return false
        return tags.contains("test-keys") || (Build.FINGERPRINT?.contains("test-keys") == true)
    }
}
