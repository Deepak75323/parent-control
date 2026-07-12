package com.childmonitor.deviceowner

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Lock Task (kiosk) shell — child stays in this activity until parent disables kiosk.
 */
class KioskActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        layout.addView(
            TextView(this).apply {
                text = "Device locked by parent"
                textSize = 20f
            },
        )
        layout.addView(
            TextView(this).apply {
                text = "Only approved apps are available in kiosk mode."
                textSize = 14f
            },
        )
        setContentView(layout)

        if (ACTION_DISABLE == intent?.action) {
            stopLockTaskAndFinish()
            return
        }

        enterLockTaskIfPermitted()
    }

    override fun onResume() {
        super.onResume()
        if (intent?.action != ACTION_DISABLE) {
            enterLockTaskIfPermitted()
        }
    }

    @Suppress("DEPRECATION")
    private fun enterLockTaskIfPermitted() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isLockTaskPermitted(packageName)) {
            try {
                startLockTask()
            } catch (e: Exception) {
                // Not in lock task allowlist yet
            }
        }
    }

    private fun stopLockTaskAndFinish() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                stopLockTask()
            }
        } catch (_: Exception) {
        }
        finish()
    }

    companion object {
        const val ACTION_DISABLE = "com.childmonitor.action.DISABLE_KIOSK"
    }
}
