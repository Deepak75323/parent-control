package com.childmonitor.capture

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.childmonitor.debug.PcpFlowLogger

object ScreenCaptureController {

    private const val TAG = "ScreenCaptureController"
    private const val VIRTUAL_DISPLAY_NAME = "ChildMonitorScreen"

    @Volatile
    private var activeProjection: MediaProjection? = null

    @Volatile
    private var lastContext: Context? = null

    /** True after getMediaProjection succeeded once for the in-memory consent intent. */
    @Volatile
    private var consentBinderUsed: Boolean = false

    /**
     * True while a capturer has an active Surface attached to the warm VirtualDisplay.
     * False after [detachCaptureSurface] between streams — the VirtualDisplay itself stays warm.
     */
    @Volatile
    private var virtualDisplayActive: Boolean = false

    /**
     * Android 14+: one [MediaProjection.createVirtualDisplay] per projection instance.
     * After the first success we keep the [VirtualDisplay] and reattach Surfaces instead of
     * calling createVirtualDisplay again (which throws SecurityException).
     */
    @Volatile
    private var warmVirtualDisplay: VirtualDisplay? = null

    @Volatile
    private var warmVirtualDisplaySession: Boolean = false

    /**
     * True after [createVirtualDisplay] succeeded on [activeProjection]. If the warm display
     * is later lost while the projection binder is still cached, we must NOT call
     * createVirtualDisplay again — mark reconsent instead (Android 14+ SecurityException).
     */
    @Volatile
    private var virtualDisplayCreatedOnProjection: Boolean = false

    /** True when getMediaProjection ran while mediaProjection-typed screen FGS was active. */
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
    fun hasWarmVirtualDisplaySession(): Boolean = warmVirtualDisplaySession

    /**
     * False when this projection already created a VirtualDisplay that is no longer warm —
     * a second createVirtualDisplay would throw SecurityException on API 34+.
     */
    @JvmStatic
    fun canCreateVirtualDisplayOnCurrentProjection(): Boolean = !virtualDisplayCreatedOnProjection

    /**
     * Cached binder exists but the one-shot VirtualDisplay was lost — next capture needs fresh consent.
     */
    @JvmStatic
    fun isSpentProjectionWithoutWarmDisplay(): Boolean =
        activeProjection != null && virtualDisplayCreatedOnProjection && warmVirtualDisplay == null

    /**
     * @deprecated Prefer [attachCaptureSurface] / [detachCaptureSurface]. Kept for callers that
     * only flip the busy flag after managing their own display (should no longer create displays).
     */
    @JvmStatic
    fun markVirtualDisplayActive(active: Boolean) {
        virtualDisplayActive = active
    }

    /**
     * First stream: create VirtualDisplay once. Later streams: resize + setSurface on the warm
     * instance. Never call createVirtualDisplay twice on the same [MediaProjection] (API 34+).
     */
    @JvmStatic
    @Synchronized
    fun attachCaptureSurface(
        surface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int,
        handler: Handler?,
    ): VirtualDisplay? {
        val projection = activeProjection ?: run {
            CaptureFrameLogger.warn("VIRTUAL_DISPLAY_ATTACH_ABORT", "no active projection")
            return null
        }
        warmVirtualDisplay?.let { existing ->
            return try {
                existing.resize(width, height, densityDpi)
                existing.surface = surface
                virtualDisplayActive = true
                warmVirtualDisplaySession = true
                CaptureFrameLogger.log(
                    "VIRTUAL_DISPLAY_REATTACHED",
                    "${width}x${height} dpi=$densityDpi",
                )
                existing
            } catch (t: Throwable) {
                CaptureFrameLogger.error("VIRTUAL_DISPLAY_REATTACH_FAILED", t)
                releaseWarmVirtualDisplayLocked()
                // Token was already used for createVirtualDisplay — fresh consent required.
                lastContext?.let { ctx ->
                    MediaProjectionTokenStore.markRequiresReconsent(ctx, "virtual_display_reattach_failed")
                }
                invalidateSpentProjectionLocked("virtual_display_reattach_failed")
                null
            }
        }
        if (virtualDisplayCreatedOnProjection) {
            CaptureFrameLogger.warn(
                "CREATE_VIRTUAL_DISPLAY_BLOCKED",
                "projection already consumed createVirtualDisplay — requiring reconsent",
            )
            lastContext?.let { ctx ->
                MediaProjectionTokenStore.markRequiresReconsent(ctx, "second_create_virtual_display_blocked")
            }
            invalidateSpentProjectionLocked("second_create_virtual_display_blocked")
            return null
        }
        return try {
            // PUBLIC | AUTO_MIRROR matches WebRTC ScreenCapturerAndroid. AUTO_MIRROR helps
            // MIUI/HyperOS mirror HW-composed layers (e.g. video players) into the VirtualDisplay.
            val created = projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                handler,
            )
            if (created == null) {
                CaptureFrameLogger.warn(
                    "CREATE_VIRTUAL_DISPLAY_FAILED",
                    "createVirtualDisplay returned null projection=$projection",
                )
                return null
            }
            warmVirtualDisplay = created
            warmVirtualDisplaySession = true
            virtualDisplayCreatedOnProjection = true
            virtualDisplayActive = true
            CaptureFrameLogger.log(
                "CREATE_VIRTUAL_DISPLAY_SUCCESS",
                "displayId=${created.display?.displayId} ${width}x${height}",
            )
            created
        } catch (t: Throwable) {
            CaptureFrameLogger.error("CREATE_VIRTUAL_DISPLAY_FAILED", t)
            if (t is SecurityException) {
                lastContext?.let { ctx ->
                    MediaProjectionTokenStore.markRequiresReconsent(ctx, "create_virtual_display_security")
                }
                invalidateSpentProjectionLocked("create_virtual_display_security")
            }
            throw t
        }
    }

    /**
     * End a stream without ending the MediaProjection session. Clears the Surface so the
     * SurfaceTextureHelper can be disposed, but keeps the VirtualDisplay for the next attach.
     */
    @JvmStatic
    @Synchronized
    fun detachCaptureSurface() {
        try {
            warmVirtualDisplay?.surface = null
        } catch (t: Throwable) {
            CaptureFrameLogger.error("VIRTUAL_DISPLAY_DETACH_FAILED", t)
        }
        virtualDisplayActive = false
        CaptureFrameLogger.log(
            "VIRTUAL_DISPLAY_DETACHED",
            "warmSession=$warmVirtualDisplaySession hasDisplay=${warmVirtualDisplay != null}",
        )
    }

    @Synchronized
    private fun releaseWarmVirtualDisplayLocked() {
        try {
            warmVirtualDisplay?.surface = null
        } catch (_: Throwable) {
        }
        try {
            warmVirtualDisplay?.release()
        } catch (_: Throwable) {
        }
        warmVirtualDisplay = null
        warmVirtualDisplaySession = false
        virtualDisplayActive = false
    }

    /** Drop a spent projection binder so callers request fresh consent instead of crashing. */
    @Synchronized
    private fun invalidateSpentProjectionLocked(reason: String) {
        CaptureFrameLogger.warn("PROJECTION_SPENT_INVALIDATED", reason)
        releaseWarmVirtualDisplayLocked()
        try {
            intentionalStop = true
            activeProjection?.stop()
        } catch (_: Exception) {
        } finally {
            intentionalStop = false
        }
        activeProjection = null
        projectionObtainedWithFgs = false
        virtualDisplayCreatedOnProjection = false
        consentBinderUsed = false
        MediaProjectionCaptureGate.release("spent-projection:$reason")
        lastContext?.let { ctx ->
            MediaProjectionTokenStore.invalidateSession(ctx)
            MediaProjectionLossHandler.onProjectionUnavailable(ctx, reason)
        }
    }

    /** @visibleForTesting */
    @JvmStatic
    fun seedWarmVirtualDisplaySessionForTests() {
        warmVirtualDisplaySession = true
        virtualDisplayActive = true
        virtualDisplayCreatedOnProjection = true
    }

    /** @visibleForTesting — VD already consumed, warm instance lost (FGS destroy race). */
    @JvmStatic
    fun seedSpentProjectionWithoutWarmDisplayForTests() {
        warmVirtualDisplay = null
        warmVirtualDisplaySession = false
        virtualDisplayActive = false
        virtualDisplayCreatedOnProjection = true
    }

    @JvmStatic
    @JvmOverloads
    fun obtainProjection(
        context: Context,
        resultCode: Int? = null,
        data: Intent? = null,
    ): MediaProjection? {
        activeProjection?.let { cached ->
            if (isSpentProjectionWithoutWarmDisplay()) {
                CaptureFrameLogger.warn(
                    "OBTAIN_PROJECTION_SPENT_NO_WARM_VD",
                    "refusing cached spent projection — reconsent required",
                )
                synchronized(this) {
                    invalidateSpentProjectionLocked("obtain_cached_spent_no_warm_vd")
                }
                return null
            }
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

        // P1-07: Android 14+ requires FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION before
        // MediaProjectionManager.getMediaProjection (SecurityException otherwise).
        if (!MediaProjectionFgsOrdering.canCallGetMediaProjection(
                Build.VERSION.SDK_INT,
                ScreenCaptureForegroundService.isMediaProjectionTypeActive(),
            )
        ) {
            CaptureFrameLogger.warn(
                "OBTAIN_PROJECTION_BLOCKED_NO_MP_FGS",
                "api=${Build.VERSION.SDK_INT} fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} " +
                    "mpTypeActive=false — promote mediaProjection FGS first",
            )
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.MEDIA_PROJECTION,
                "obtainProjection blocked — mediaProjection FGS not active (P1-07)",
                "api=${Build.VERSION.SDK_INT}",
            )
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
            "fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} " +
                "mpTypeActive=${ScreenCaptureForegroundService.isMediaProjectionTypeActive()} " +
                "freshIntent=${data != null}",
        )
        CaptureFrameLogger.log(
            "OBTAIN_PROJECTION_ENTER",
            "fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} " +
                "mpTypeActive=${ScreenCaptureForegroundService.isMediaProjectionTypeActive()} " +
                "freshIntent=${data != null}",
        )

        return try {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            lastContext = context.applicationContext
            val projection = mgr.getMediaProjection(code, intent)
            if (projection == null) {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.MEDIA_PROJECTION,
                    "getMediaProjection returned null — invalidating stale consent",
                    "fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} " +
                        "mpTypeActive=${ScreenCaptureForegroundService.isMediaProjectionTypeActive()}",
                )
                Log.w(
                    TAG,
                    "getMediaProjection returned null — invalidating stale consent for Cast re-grant",
                )
                onConsentInvalidated()
                MediaProjectionTokenStore.invalidateSession(context.applicationContext)
                return null
            }
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    val wasIntentional = intentionalStop
                    intentionalStop = false
                    synchronized(this@ScreenCaptureController) {
                        releaseWarmVirtualDisplayLocked()
                        activeProjection = null
                        projectionObtainedWithFgs = false
                        virtualDisplayCreatedOnProjection = false
                    }
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
            virtualDisplayCreatedOnProjection = false
            projectionObtainedWithFgs = ScreenCaptureForegroundService.isMediaProjectionTypeActive()
            PcpFlowLogger.log(PcpFlowLogger.Category.MEDIA_PROJECTION, "obtainProjection — SUCCESS")
            CaptureFrameLogger.log(
                "OBTAIN_PROJECTION_SUCCESS",
                "fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} " +
                    "mpTypeActive=${ScreenCaptureForegroundService.isMediaProjectionTypeActive()} " +
                    "obtainedWithFgs=$projectionObtainedWithFgs",
            )
            if (!projectionObtainedWithFgs) {
                CaptureFrameLogger.warn(
                    "OBTAIN_PROJECTION_BEFORE_FGS",
                    "getMediaProjection succeeded but mediaProjection FGS type was inactive — " +
                        "P1-07 gate should have blocked this on API 34+",
                )
            }
            projection
        } catch (e: Exception) {
            CaptureFrameLogger.error("OBTAIN_PROJECTION_FAILED", e)
            PcpFlowLogger.error(PcpFlowLogger.Category.MEDIA_PROJECTION, "getMediaProjection failed — invalidating", e)
            Log.e(TAG, "getMediaProjection failed — invalidating stale consent for Cast re-grant", e)
            onConsentInvalidated()
            MediaProjectionTokenStore.invalidateSession(context.applicationContext)
            null
        }
    }

    fun activeProjection(): MediaProjection? = activeProjection

    /**
     * Returns a [MediaProjection] ready for capture.
     *
     * Always reuses a warm cached instance when present. Never releases and re-calls
     * [MediaProjectionManager.getMediaProjection] with the same consent Intent — Android 14+
     * throws SecurityException for both token reuse and a second
     * [MediaProjection.createVirtualDisplay] on the same instance. VirtualDisplay reuse is
     * handled by [attachCaptureSurface] / [detachCaptureSurface].
     */
    @JvmStatic
    fun acquireProjectionForCapture(context: Context): MediaProjection? {
        val appContext = context.applicationContext
        activeProjection?.let { cached ->
            if (isSpentProjectionWithoutWarmDisplay()) {
                CaptureFrameLogger.warn(
                    "OBTAIN_PROJECTION_SPENT_NO_WARM_VD",
                    "refuse for capture — requiring reconsent",
                )
                synchronized(this) {
                    invalidateSpentProjectionLocked("acquire_spent_no_warm_vd")
                }
                return null
            }
            CaptureFrameLogger.log(
                "OBTAIN_PROJECTION_CACHED",
                "reuse for capture fgsActive=${ScreenCaptureForegroundService.isForegroundActive()} " +
                    "obtainedWithFgs=$projectionObtainedWithFgs " +
                    "virtualDisplayActive=$virtualDisplayActive warmVdSession=$warmVirtualDisplaySession",
            )
            return cached
        }
        if (isConsentExhausted()) {
            MediaProjectionTokenStore.invalidateSession(appContext)
            onConsentInvalidated()
            MediaProjectionLossHandler.onProjectionUnavailable(appContext, "token_exhausted")
            return null
        }
        return obtainProjection(appContext)
    }

    fun releaseProjection() {
        val hadProjection = activeProjection != null
        synchronized(this) {
            releaseWarmVirtualDisplayLocked()
        }
        try {
            intentionalStop = true
            activeProjection?.stop() ?: run { intentionalStop = false }
        } catch (_: Exception) {
            intentionalStop = false
        }
        activeProjection = null
        projectionObtainedWithFgs = false
        virtualDisplayCreatedOnProjection = false
        if (hadProjection) {
            consentBinderUsed = false
        }
    }

    /** Drop in-memory projection only — stored pairing consent remains for reuse. */
    fun detachProjection() {
        synchronized(this) {
            releaseWarmVirtualDisplayLocked()
        }
        activeProjection = null
        projectionObtainedWithFgs = false
        // Keep virtualDisplayCreatedOnProjection if consent binder still "used" via stored
        // intent — but without activeProjection, obtainProjection must not reuse a binder.
        // Clear the create flag so a fresh getMediaProjection (new consent) can create again.
        virtualDisplayCreatedOnProjection = false
    }

    @JvmStatic
    fun projectionObtainedWithFgs(): Boolean = projectionObtainedWithFgs

    /** True while [releaseProjection] is tearing down — secondary callbacks must not invalidate consent. */
    @JvmStatic
    fun isIntentionalProjectionStop(): Boolean = intentionalStop

    /** Consent intent was consumed but projection is not cached — re-grant required. */
    @JvmStatic
    fun isConsentExhausted(): Boolean = consentBinderUsed && activeProjection == null

    /** True when a live MediaProjection binder exists (capture can proceed without Cast). */
    fun canUseSilentCapture(context: Context): Boolean =
        MediaProjectionTokenStore.hasValidToken(context) &&
            (activeProjection != null || !isConsentExhausted())

    /**
     * True only when a warm [MediaProjection] is already held. In-memory Intent alone is not
     * enough on API 34+ (same Intent cannot be getMediaProjection twice after binder death).
     */
    fun hasLiveProjection(): Boolean = activeProjection != null

    fun onFreshConsentSaved() {
        synchronized(this) {
            releaseWarmVirtualDisplayLocked()
        }
        consentBinderUsed = false
        activeProjection = null
        projectionObtainedWithFgs = false
        virtualDisplayCreatedOnProjection = false
        MediaProjectionCaptureGate.release("fresh-consent")
    }

    /** After system consumed or revoked projection — clears binder state for re-grant. */
    fun onConsentInvalidated() {
        synchronized(this) {
            releaseWarmVirtualDisplayLocked()
        }
        consentBinderUsed = false
        activeProjection = null
        projectionObtainedWithFgs = false
        virtualDisplayCreatedOnProjection = false
        MediaProjectionCaptureGate.release("consent-invalidated")
    }

    /**
     * System consent intent. On API 34+ uses [MediaProjectionConfig.createConfigForDefaultDisplay]
     * so Android skips the app-picker step — user still sees the consent dialog once.
     */
    fun buildConsentIntent(context: Context): Intent {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mgr.createScreenCaptureIntent()
        }
    }

    /**
     * Release cached MediaProjection only when explicitly revoking consent — not after each stream.
     */
    @JvmStatic
    fun releaseIfIdle(context: Context, reason: String) {
        if (activeProjection == null && !virtualDisplayActive && !warmVirtualDisplaySession) return
        if (NativeStreamPublisher.isRunning() || ScreenCaptureForegroundService.isForegroundActive()) {
            return
        }
        StreamLifecycleLogger.log("PROJECTION_RELEASE_IDLE_SKIPPED", reason)
    }
}
