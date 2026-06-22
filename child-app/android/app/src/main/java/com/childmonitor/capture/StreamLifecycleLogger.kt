package com.childmonitor.capture

import android.util.Log
import com.childmonitor.debug.PcpFlowLogger

/** Stream/capture lifecycle — filter logcat: PCP_LIFECYCLE */
object StreamLifecycleLogger {

    const val TAG = "PCP_LIFECYCLE"

    @JvmStatic
    @JvmOverloads
    fun log(stage: String, detail: String? = null) {
        val line = if (detail.isNullOrBlank()) stage else "$stage $detail"
        Log.i(TAG, line)
        PcpFlowLogger.log(PcpFlowLogger.Category.SCREEN_SHARE_FLOW, stage, detail)
    }

    @JvmStatic
    fun warn(stage: String, detail: String? = null) {
        val line = if (detail.isNullOrBlank()) stage else "$stage $detail"
        Log.w(TAG, line)
        PcpFlowLogger.warn(PcpFlowLogger.Category.SCREEN_SHARE_FLOW, stage, detail)
    }
}
