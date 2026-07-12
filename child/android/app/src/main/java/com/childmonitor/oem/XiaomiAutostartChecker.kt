package com.childmonitor.oem

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.util.Log

/**
 * Best-effort read of MIUI/HyperOS autostart via hidden AppOps (opcode 10008).
 * Returns [State.UNKNOWN] when the OEM API is unavailable — caller must fall back to manual confirm.
 */
object XiaomiAutostartChecker {

    private const val TAG = "XiaomiAutostartChecker"
    private const val OP_AUTO_START = 10008

    enum class State {
        ENABLED,
        DISABLED,
        UNKNOWN,
    }

    fun query(context: Context): State {
        if (OemVendor.detect() != OemVendor.XIAOMI) {
            return State.UNKNOWN
        }
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            val result = method.invoke(
                appOps,
                OP_AUTO_START,
                Process.myUid(),
                context.packageName,
            ) as Int
            when (result) {
                AppOpsManager.MODE_ALLOWED -> State.ENABLED
                else -> State.DISABLED
            }
        } catch (e: Exception) {
            Log.w(TAG, "MIUI autostart query unavailable", e)
            State.UNKNOWN
        }
    }
}
