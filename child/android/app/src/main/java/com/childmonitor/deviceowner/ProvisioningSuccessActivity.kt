package com.childmonitor.deviceowner

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Entry after QR / ADB Device Owner provisioning (Option B).
 */
class ProvisioningSuccessActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceOwnerManager.onDeviceOwnerEstablished(this)
        setResult(RESULT_OK)
        finish()
    }
}
