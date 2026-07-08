package com.childmonitor.capture

import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import java.util.concurrent.atomic.AtomicLong

/**
 * Frame-level capture pipeline logger — filter logcat with tag PCP_FRAME.
 */
object CaptureFrameLogger {

    const val TAG = "PCP_FRAME"

    private val frameCount = AtomicLong(0)

    fun resetFrameCount() {
        frameCount.set(0)
    }

    @JvmStatic
    fun hasReceivedFrame(): Boolean = frameCount.get() > 0

    @JvmStatic
    fun capturedFrameCount(): Long = frameCount.get()

    @JvmStatic
    @JvmOverloads
    fun log(stage: String, detail: String? = null) {
        val line = if (detail.isNullOrBlank()) stage else "$stage $detail"
        Log.i(TAG, line)
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, stage, detail)
    }

    @JvmStatic
    fun warn(stage: String, detail: String? = null) {
        val line = if (detail.isNullOrBlank()) stage else "$stage $detail"
        Log.w(TAG, line)
        PcpFlowLogger.warn(PcpFlowLogger.Category.MEDIA_PROJECTION, stage, detail)
    }

    @JvmStatic
    @JvmOverloads
    fun error(stage: String, throwable: Throwable, detail: String? = null) {
        val line = if (detail.isNullOrBlank()) stage else "$stage $detail"
        Log.e(TAG, line, throwable)
        PcpFlowLogger.error(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "$stage ${throwable.javaClass.simpleName}: ${throwable.message}",
            throwable,
        )
    }

    fun onFrameCaptured(frame: VideoFrameDimensions) {
        val count = frameCount.incrementAndGet()
        if (count == 1L) {
            log("FIRST_CAPTURE_FRAME", "${frame.width}x${frame.height}")
        } else if (count == 100L) {
            log("FRAME_COUNT_100", "${frame.width}x${frame.height}")
        } else if (count == 500L) {
            log("FRAME_COUNT_500", "${frame.width}x${frame.height}")
        }
    }

    data class VideoFrameDimensions(val width: Int, val height: Int)
}
