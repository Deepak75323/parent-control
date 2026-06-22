package com.childmonitor.capture

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger

object ScreenCaptureController {

    private const val TAG = "ScreenCaptureController"

    @Volatile
    private var activeProjection: MediaProjection? = null

    @Volatile
    private var lastContext: Context? = null

    /** True after getMediaProjection succeeded once for the in-memory consent intent. */
    @Volatile
    private var consentBinderUsed: Boolean = false

    /** True once createVirtualDisplay succeeded on the current projection session. */
    @Volatile
    private var virtualDisplayActive: Boolean = false

    /** True when getMediaProjection ran while ScreenCaptureForegroundService was active. */
    @Volatile
    private var projectionObtainedWithFgs: Boolean = false

    /**
     * Set to true before calling activeProjection.stop() during an intentional stream teardown
     * so the onStop callback knows not to invalidate the stored consent token or trigger the
     * re-consent dialog. Only system-initiated projection loss (MIUI kill, user revoke, etc.)
     * should result in a new consent request.
     */
    @Volatile
    private var intentionalStop: Boolean = false

    @JvmStatic
    fun virtualDisplayActive(): Boolean = virtualDisplayActive

    @JvmStatic
    fun markVirtualDisplayActive(active: Boolean) {
        virtualDisplayActive = active
    }

    @JvmStatic
    @JvmOverloads
    fun obtainProjection(
        context: Context,
        resultCode: Int? = null,
        data: Intent? = null,
    ): MediaProjection? {
        activeProjection?.let { cached ->
            CaptureFrameLogger.log(
                "OBTAIN_PROJECTION_CACHED",
                "fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} " +
                    "obtainedWithFgs=$projectionObtainedWithFgs virtualDisplayActive=$virtualDisplayActive",
            )
            return cached
        }

        if (consentBinderUsed) {
            CaptureFrameLogger.warn(
                "OBTAIN_PROJECTION_TOKEN_EXHAUSTED",
                "getMediaProjection already consumed this consent — re-grant required",
            )
            val appContext = context.applicationContext
            MediaProjectionTokenStore.invalidateSession(appContext)
            onConsentInvalidated()
            MediaProjectionLossHandler.onProjectionUnavailable(appContext, "token_exhausted")
            return null
        }

        val code = resultCode ?: MediaProjectionTokenStore.loadResultCode(context) ?: run {
            PcpFlowLogger.warn(PcpFlowLogger.Category.MEDIA_PROJECTION, "obtainProjection — no resultCode")
            return null
        }
        val intent = data ?: MediaProjectionTokenStore.loadIntent(context) ?: run {
            PcpFlowLogger.warn(PcpFlowLogger.Category.MEDIA_PROJECTION, "obtainProjection — no consent intent")
            return null
        }

        PcpFlowLogger.log(
            PcpFlowLogger.Category.MEDIA_PROJECTION,
            "obtainProjection — calling getMediaProjection",
            "fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} freshIntent=${data != null}",
        )
        CaptureFrameLogger.log(
            "OBTAIN_PROJECTION_ENTER",
            "fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} freshIntent=${data != null}",
        )

        return try {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            lastContext = context.applicationContext
            val projection = mgr.getMediaProjection(code, intent)
            if (projection == null) {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.MEDIA_PROJECTION,
                    "getMediaProjection returned null",
                    "fgsActive=${ScreenCaptureForegroundService.isForegroundActive()}",
                )
                Log.w(
                    TAG,
                    "getMediaProjection returned null — keeping stored consent " +
                        "(fgsActive=${ScreenCaptureForegroundService.isForegroundActive()})",
                )
                return null
            }
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    val wasIntentional = intentionalStop
                    intentionalStop = false
                    activeProjection = null
                    projectionObtainedWithFgs = false
                    virtualDisplayActive = false
                    MediaProjectionCaptureGate.release("projection-onStop")
                    if (wasIntentional) {
                        // Normal stream teardown — token is still valid for next session.
                        StreamLifecycleLogger.log("PROJECTION_STOPPED_INTENTIONAL", "ScreenCaptureController.callback")
                        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "MediaProjection released intentionally — consent kept")
                        Log.i(TAG, "MediaProjection released intentionally — stored consent kept for next session")
                        onConsentInvalidated()
                    } else {
                        // System or user killed the projection — invalidate token and recover.
                        StreamLifecycleLogger.warn("PROJECTION_STOPPED", "ScreenCaptureController.callback")
                        PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "MediaProjection session ended unexpectedly")
                        Log.i(TAG, "MediaProjection session ended by system — re-grant required")
                        onConsentInvalidated()
                        MediaProjectionTokenStore.invalidateSession(lastContext)
                        lastContext?.let { ctx ->
                            MediaProjectionLossHandler.onProjectionUnavailable(ctx, "projection_onStop")
                        }
                    }
                }
            }, null)
            activeProjection = projection
            consentBinderUsed = true
            projectionObtainedWithFgs = ScreenCaptureForegroundService.isForegroundActive()
            PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "obtainProjection — SUCCESS")
            CaptureFrameLogger.log(
                "OBTAIN_PROJECTION_SUCCESS",
                "fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} " +
                    "obtainedWithFgs=$projectionObtainedWithFgs",
            )
            if (!projectionObtainedWithFgs) {
                CaptureFrameLogger.warn(
                    "OBTAIN_PROJECTION_BEFORE_FGS",
                    "getMediaProjection succeeded but screen FGS was inactive — " +
                        "createVirtualDisplay may fail on Android 14+",
                )
            }
            projection
        } catch (e: Exception) {
            CaptureFrameLogger.error("OBTAIN_PROJECTION_FAILED", e)
            PcpFlowLogger.error(PcpFlowLogger.Category.MEDIA_PROJECTION, "getMediaProjection failed", e)
            Log.e(TAG, "getMediaProjection failed — keeping stored consent", e)
            null
        }
    }

    fun activeProjection(): MediaProjection? = activeProjection

    fun releaseProjection() {
        try {
            intentionalStop = true
            activeProjection?.stop() ?: run { intentionalStop = false }
        } catch (_: Exception) {
            intentionalStop = false
        }
        activeProjection = null
        projectionObtainedWithFgs = false
        virtualDisplayActive = false
    }

    /** Drop in-memory projection only — stored pairing consent remains for reuse. */
    fun detachProjection() {
        activeProjection = null
        projectionObtainedWithFgs = false
        virtualDisplayActive = false
    }

    @JvmStatic
    fun projectionObtainedWithFgs(): Boolean = projectionObtainedWithFgs

    /** Consent intent was consumed but projection is not cached — re-grant required. */
    @JvmStatic
    fun isConsentExhausted(): Boolean = consentBinderUsed && activeProjection == null

    /** True when a one-time MediaProjection consent was stored during setup. */
    fun canUseSilentCapture(context: Context): Boolean =
        MediaProjectionTokenStore.hasValidToken(context)

    fun onFreshConsentSaved() {
        consentBinderUsed = false
        virtualDisplayActive = false
        activeProjection = null
        projectionObtainedWithFgs = false
        MediaProjectionCaptureGate.release("fresh-consent")
    }

    /** After system consumed or revoked projection — clears binder state for re-grant. */
    fun onConsentInvalidated() {
        consentBinderUsed = false
        virtualDisplayActive = false
        activeProjection = null
        projectionObtainedWithFgs = false
        MediaProjectionCaptureGate.release("consent-invalidated")
    }

    fun buildConsentIntent(context: Context): Intent {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mgr.createScreenCaptureIntent()
    }

    /**
     * Release cached MediaProjection when no publisher or screen FGS is active.
     */
    @JvmStatic
    fun releaseIfIdle(context: Context, reason: String) {
        if (activeProjection == null && !virtualDisplayActive) return
        if (NativeStreamPublisher.isRunning() || ScreenCaptureForegroundService.isForegroundActive()) {
            return
        }
        StreamLifecycleLogger.log("PROJECTION_RELEASE_IDLE", reason)
        releaseProjection()
    }
}
