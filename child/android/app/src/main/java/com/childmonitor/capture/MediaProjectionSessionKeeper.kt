package com.childmonitor.capture

import android.content.Context
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger

/**
 * Keeps a stored MediaProjection consent reusable after pairing.
 * Screen FGS is started on-demand when parent requests screen share — not at app startup.
 */
object MediaProjectionSessionKeeper {

    private const val TAG = "MediaProjSessionKeeper"

    @JvmStatic
    fun warmSessionAsync(context: Context) {
        if (!MediaProjectionTokenStore.hasValidToken(context)) return
        if (!ScreenFgsStartupGate.canStartScreenFgs()) return
        Thread { warmSession(context.applicationContext) }.start()
    }

    /**
     * Start screen FGS and obtain projection — call only when a screen stream is starting.
     */
    @JvmStatic
    @Synchronized
    fun warmSession(context: Context): Boolean {
        val app = context.applicationContext
        if (MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(app)) {
            MediaProjectionTokenStore.logProjectionLostAfterProcessRestart(app)
            return false
        }
        if (!MediaProjectionTokenStore.hasValidToken(app)) {
            PcpFlowLogger.warn(PcpFlowLogger.Category.MEDIA_PROJECTION, "warmSession skipped — no stored consent")
            Log.i(TAG, "warmSession skipped — no stored consent")
            return false
        }
        if (!ScreenFgsStartupGate.canStartScreenFgs()) {
            PcpFlowLogger.warn(PcpFlowLogger.Category.MEDIA_PROJECTION, "warmSession skipped — UI not ready")
            Log.i(TAG, "warmSession skipped — UI not ready")
            return false
        }
        // Re-warm typed FGS when projection is already cached (avoids getMediaProjection on API 34+).
        if (ScreenCaptureController.activeProjection() != null) {
            PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "warmSession — reusing cached projection")
            return ScreenCaptureForegroundService.startAndWait(app)
        }
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "warmSession — starting screen FGS")
        if (!ScreenCaptureForegroundService.startAndWait(app)) {
            PcpFlowLogger.warn(PcpFlowLogger.Category.MEDIA_PROJECTION, "warmSession — screen FGS PREPARE failed")
            Log.w(TAG, "warmSession — screen FGS PREPARE failed")
            return false
        }
        val projection = ScreenCaptureController.obtainProjection(app)
        if (projection == null) {
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "warmSession — obtainProjection failed (consent invalidated for Cast re-grant)",
                MediaProjectionTokenStore.debugState(app).toString(),
            )
            Log.w(TAG, "warmSession — obtainProjection failed — Cast re-grant required")
            return false
        }
        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "warmSession OK — projection ready")
        Log.i(TAG, "warmSession OK — projection session ready for silent capture")
        return true
    }

    @JvmStatic
    fun ensureReady(context: Context): Boolean {
        val app = context.applicationContext
        if (MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(app)) {
            MediaProjectionTokenStore.logProjectionLostAfterProcessRestart(app)
            return false
        }
        if (!MediaProjectionTokenStore.hasValidToken(app)) return false
        if (
            ScreenCaptureForegroundService.isForegroundActive() &&
            ScreenCaptureForegroundService.isMediaProjectionTypeActive() &&
            ScreenCaptureController.activeProjection() != null
        ) {
            return true
        }
        if (ScreenCaptureController.activeProjection() != null) {
            return warmSession(app)
        }
        if (!ScreenFgsStartupGate.canStartScreenFgs()) {
            return false
        }
        return warmSession(app)
    }

    @JvmStatic
    fun releaseSession(context: Context) {
        ScreenCaptureForegroundService.stopStream(context.applicationContext)
        ScreenCaptureController.releaseProjection()
    }
}
