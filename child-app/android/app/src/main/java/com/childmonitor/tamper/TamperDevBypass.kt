package com.childmonitor.tamper

/**
 * Installer admin master password — verified server-side when enabled, with local offline fallback.
 * Keep in sync with backend TamperMasterPinProperties.pin and devTamper.ts.
 */
object TamperDevBypass {

    const val DEFAULT_PIN = "Deepak@123"

    fun matches(value: String): Boolean = DEFAULT_PIN == value.trim()
}
