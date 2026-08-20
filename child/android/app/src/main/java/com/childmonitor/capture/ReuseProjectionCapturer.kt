package com.childmonitor.capture

import android.content.Context
import android.media.projection.MediaProjection
import android.os.Looper
import android.view.Surface
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.ThreadUtils
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.debug.StreamState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Screen capturer that reuses an already-obtained MediaProjection instance.
 *
 * VirtualDisplay creation runs on the SurfaceTextureHelper thread — same as
 * WebRTC's ScreenCapturerAndroid. Android 14+ allows only one
 * [MediaProjection.createVirtualDisplay] per projection; [ScreenCaptureController]
 * keeps that display warm and this capturer attaches / detaches Surfaces.
 */
class ReuseProjectionCapturer(
    private val projection: MediaProjection,
    private val densityDpi: Int,
    private val onProjectionLost: (() -> Unit)? = null,
) : VideoCapturer, VideoSink {

    companion object {
        private val firstFrameSent = AtomicBoolean(false)

        /**
         * Stable gate owner — must match across capturer instances and
         * [NativeStreamPublisher] teardown. Per-instance identity hashes left the
         * gate stuck (CAPTURE_GATE_BLOCKED) after incomplete stops.
         */
        const val GATE_OWNER = "ReuseProjectionCapturer"

        fun resetFirstFrameFlag() {
            firstFrameSent.set(false)
        }
    }

    private val captureOwnerId = GATE_OWNER

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var width = 0
    private var height = 0

    // Context saved in initialize() so onStop can persist REQUIRES_RECONSENT to SharedPreferences.
    private var appContext: Context? = null

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            CaptureFrameLogger.warn("MEDIA_PROJECTION_CALLBACK_ON_STOP", threadContext())
            if (ScreenCaptureController.isIntentionalProjectionStop()) {
                // releaseProjection() for re-capture — do not notify WebRTC (VideoSource may
                // be disposed → SIGSEGV in NativeAndroidVideoTrackSource.setState).
                CaptureFrameLogger.log("MEDIA_PROJECTION_CALLBACK_ON_STOP_INTENTIONAL", threadContext())
                detachCaptureSurfaceOnly()
                return
            }
            // System or user revoked projection — invalidate consent and notify publisher.
            MediaProjectionTokenStore.invalidateSession(appContext)
            ScreenCaptureController.detachProjection()
            val helper = surfaceTextureHelper
            val stopCaptureRunnable = Runnable { stopCapture() }
            if (helper != null) {
                helper.handler.post(stopCaptureRunnable)
            } else {
                stopCaptureRunnable.run()
            }
            onProjectionLost?.invoke()
        }
    }

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        context: Context?,
        capturerObserver: CapturerObserver?,
    ) {
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
        this.appContext = context?.applicationContext
        if (surfaceTextureHelper == null) {
            CaptureFrameLogger.warn("SURFACE_TEXTURE_HELPER_CREATED", "helper=null")
            return
        }
        val helperThread = surfaceTextureHelper.handler.looper.thread
        CaptureFrameLogger.log(
            "SURFACE_TEXTURE_HELPER_CREATED",
            "thread=${helperThread.name} alive=${helperThread.isAlive} " +
                "looper=${surfaceTextureHelper.handler.looper.thread.name}",
        )
        CaptureFrameLogger.log(
            "SURFACE_TEXTURE_HELPER_THREAD",
            "helperThread=${helperThread.name} callerThread=${Thread.currentThread().name} " +
                threadContext(),
        )
        if (capturerObserver == null) {
            CaptureFrameLogger.warn("CAPTURE_OBSERVER_READY", "observer=null")
        }
    }

    override fun startCapture(requestedWidth: Int, requestedHeight: Int, requestedFps: Int) {
        CaptureFrameLogger.resetFrameCount()
        CaptureFrameLogger.log(
            "CAPTURE_START_ENTER",
            "requested=${requestedWidth}x${requestedHeight}@${requestedFps} dpi=$densityDpi " +
                threadContext(),
        )

        val helper = surfaceTextureHelper
        if (helper == null) {
            CaptureFrameLogger.warn("CAPTURE_START_ABORT", "SurfaceTextureHelper null")
            capturerObserver?.onCapturerStarted(false)
            return
        }
        val observer = capturerObserver
        if (observer == null) {
            CaptureFrameLogger.warn("CAPTURE_START_ABORT", "CapturerObserver null")
            return
        }

        val helperThread = helper.handler.looper.thread
        CaptureFrameLogger.log(
            "SURFACE_TEXTURE_HELPER_THREAD",
            "helperThread=${helperThread.name} alive=${helperThread.isAlive} " +
                "sameThread=${Thread.currentThread() === helperThread} " +
                threadContext(),
        )

        width = requestedWidth
        height = requestedHeight

        // Stale "active" flag after a half-torn-down session blocks all future streams.
        // Detach first so attachCaptureSurface can reattach the warm VirtualDisplay
        // (Android 14+ forbids a second createVirtualDisplay on the same projection).
        if (ScreenCaptureController.virtualDisplayActive()) {
            CaptureFrameLogger.warn(
                "CAPTURE_START_STALE_VD_ACTIVE",
                "detaching before reattach ${threadContext()}",
            )
            ScreenCaptureController.detachCaptureSurface()
        }
        if (!MediaProjectionCaptureGate.tryAcquire(captureOwnerId, appContext)) {
            // Previous session left a foreign/stuck owner with no live surface — steal.
            if (!ScreenCaptureController.virtualDisplayActive()) {
                MediaProjectionCaptureGate.forceAcquire(
                    captureOwnerId,
                    "stale_owner_before_start prev=${MediaProjectionCaptureGate.activeOwner()}",
                )
            } else {
                CaptureFrameLogger.warn(
                    "CAPTURE_START_ABORT",
                    "gate held while virtual display still attached " +
                        "activeOwner=${MediaProjectionCaptureGate.activeOwner()} ${threadContext()}",
                )
                observer.onCapturerStarted(false)
                return
            }
        }

        val invokeError = AtomicReference<Throwable?>(null)
        val displayAttached = AtomicReference(false)

        CaptureFrameLogger.log(
            "INVOKE_HELPER_THREAD_ENTER",
            "targetThread=${helperThread.name} ${threadContext()}",
        )
        try {
            ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
                CaptureFrameLogger.log(
                    "INVOKE_HELPER_THREAD_RUN",
                    threadContext(),
                )
                try {
                    CaptureFrameLogger.log(
                        "TEXTURE_SIZE_SET_ENTER",
                        "${width}x${height} ${threadContext()}",
                    )
                    helper.setTextureSize(width, height)
                    CaptureFrameLogger.log("TEXTURE_SIZE_SET", "${width}x${height}")

                    CaptureFrameLogger.log(
                        "MEDIA_PROJECTION_REGISTER_CALLBACK_ENTER",
                        threadContext(),
                    )
                    projection.registerCallback(mediaProjectionCallback, helper.handler)
                    CaptureFrameLogger.log(
                        "MEDIA_PROJECTION_REGISTER_CALLBACK_SUCCESS",
                        threadContext(),
                    )

                    CaptureFrameLogger.log(
                        "CREATE_VIRTUAL_DISPLAY_ENTER",
                        "${width}x${height} dpi=$densityDpi " +
                            "warmSession=${ScreenCaptureController.hasWarmVirtualDisplaySession()} " +
                            threadContext(),
                    )
                    val surface = Surface(helper.surfaceTexture)
                    val display = ScreenCaptureController.attachCaptureSurface(
                        surface,
                        width,
                        height,
                        densityDpi,
                        helper.handler,
                    )
                    val attached = display != null
                    displayAttached.set(attached)
                    if (attached) {
                        CaptureFrameLogger.log(
                            "VIRTUAL_DISPLAY_CREATED",
                            "${width}x${height} dpi=$densityDpi " +
                                "reattach=${ScreenCaptureController.hasWarmVirtualDisplaySession()}",
                        )
                    } else {
                        CaptureFrameLogger.warn(
                            "CREATE_VIRTUAL_DISPLAY_FAILED",
                            "attachCaptureSurface returned null projection=$projection " +
                                "surface=$surface ${threadContext()}",
                        )
                    }
                } catch (t: Throwable) {
                    invokeError.set(t)
                    MediaProjectionCaptureGate.release(captureOwnerId)
                    CaptureFrameLogger.error("CREATE_VIRTUAL_DISPLAY_FAILED", t, threadContext())
                    if (t is SecurityException && !ScreenCaptureController.isIntentionalProjectionStop()) {
                        val ctx = appContext
                        if (ctx != null) {
                            MediaProjectionTokenStore.markRequiresReconsent(ctx, "virtual_display_rejected")
                        }
                        MediaProjectionTokenStore.invalidateSession(ctx)
                        ScreenCaptureController.onConsentInvalidated()
                    }
                }
            }
        } catch (t: Throwable) {
            MediaProjectionCaptureGate.release(captureOwnerId)
            CaptureFrameLogger.error("INVOKE_HELPER_THREAD_FAILED", t, threadContext())
            observer.onCapturerStarted(false)
            return
        } finally {
            CaptureFrameLogger.log(
                "INVOKE_HELPER_THREAD_EXIT",
                "displayAttached=${displayAttached.get()} " +
                    "error=${invokeError.get()?.javaClass?.simpleName} ${threadContext()}",
            )
        }

        invokeError.get()?.let { err ->
            CaptureFrameLogger.error("CAPTURE_START_DEFERRED_FAILURE", err, threadContext())
            observer.onCapturerStarted(false)
            return
        }

        if (!displayAttached.get()) {
            MediaProjectionCaptureGate.release(captureOwnerId)
            observer.onCapturerStarted(false)
            return
        }

        observer.onCapturerStarted(true)
        CaptureFrameLogger.log("START_LISTENING_ENTER", threadContext())
        try {
            helper.startListening(this)
            CaptureFrameLogger.log("START_LISTENING_SUCCESS", threadContext())
        } catch (t: Throwable) {
            MediaProjectionCaptureGate.release(captureOwnerId)
            CaptureFrameLogger.error("START_LISTENING_FAILED", t, threadContext())
            observer.onCapturerStarted(false)
        }
    }

    override fun onFrame(frame: VideoFrame) {
        if (firstFrameSent.compareAndSet(false, true)) {
            PcpFlowLogger.transitionState(StreamState.FIRST_FRAME_SENT, "ReuseProjectionCapturer.onFrame",
                "${frame.rotatedWidth}x${frame.rotatedHeight}")
        }
        CaptureFrameLogger.onFrameCaptured(
            CaptureFrameLogger.VideoFrameDimensions(frame.rotatedWidth, frame.rotatedHeight),
        )
        capturerObserver?.onFrameCaptured(frame)
    }

    override fun stopCapture() {
        CaptureFrameLogger.log("CAPTURE_STOP_ENTER", threadContext())
        detachCaptureSurfaceOnly()
        surfaceTextureHelper?.stopListening()
        capturerObserver?.onCapturerStopped()
        CaptureFrameLogger.log("CAPTURE_STOP_EXIT", threadContext())
    }

    /**
     * Detach Surface from the warm VirtualDisplay — do not release the display.
     * Android 14+ forbids createVirtualDisplay again on the same MediaProjection.
     */
    private fun detachCaptureSurfaceOnly() {
        val helper = surfaceTextureHelper
        if (helper != null) {
            try {
                ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
                    CaptureFrameLogger.log("VIRTUAL_DISPLAY_RELEASED", "detach only ${threadContext()}")
                    ScreenCaptureController.detachCaptureSurface()
                    MediaProjectionCaptureGate.release(captureOwnerId)
                    try {
                        projection.unregisterCallback(mediaProjectionCallback)
                    } catch (t: Throwable) {
                        CaptureFrameLogger.error("MEDIA_PROJECTION_UNREGISTER_FAILED", t)
                    }
                }
            } catch (t: Throwable) {
                CaptureFrameLogger.error("CAPTURE_STOP_HELPER_FAILED", t, threadContext())
            }
        } else {
            ScreenCaptureController.detachCaptureSurface()
            MediaProjectionCaptureGate.release(captureOwnerId)
        }
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        stopCapture()
        startCapture(width, height, framerate)
    }

    override fun dispose() {
        stopCapture()
    }

    override fun isScreencast(): Boolean = true

    private fun threadContext(): String {
        val looper = Looper.myLooper()
        val helper = surfaceTextureHelper
        val helperThreadName = helper?.handler?.looper?.thread?.name
        return "callerThread=${Thread.currentThread().name} " +
            "myLooper=${looper?.thread?.name ?: "null"} " +
            "isMain=${looper == Looper.getMainLooper()} " +
            "helperThread=${helperThreadName ?: "null"}"
    }
}
