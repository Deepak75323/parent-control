package com.childmonitor.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Looper
import android.view.Surface
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.ThreadUtils
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.atomic.AtomicReference

/**
 * Screen capturer that reuses an already-obtained MediaProjection instance.
 * VirtualDisplay creation runs on the SurfaceTextureHelper thread — same as
 * WebRTC's ScreenCapturerAndroid — so frames reach VideoSource reliably.
 */
class ReuseProjectionCapturer(
    private val projection: MediaProjection,
    private val densityDpi: Int,
    private val onProjectionLost: (() -> Unit)? = null,
) : VideoCapturer, VideoSink {

    private val captureOwnerId = "ReuseProjectionCapturer@${System.identityHashCode(this)}"

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var width = 0
    private var height = 0

    // Context saved in initialize() so onStop can persist REQUIRES_RECONSENT to SharedPreferences.
    private var appContext: Context? = null

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            CaptureFrameLogger.warn("MEDIA_PROJECTION_CALLBACK_ON_STOP", threadContext())
            // Pass context so REQUIRES_RECONSENT is written to SharedPreferences; without it
            // the state stays GRANTED_ACTIVE in prefs while in-memory binders are gone.
            MediaProjectionTokenStore.invalidateSession(appContext)
            ScreenCaptureController.detachProjection()
            val helper = surfaceTextureHelper
            if (helper != null) {
                helper.handler.post { stopCapture() }
            } else {
                stopCapture()
            }
            // Notify the stream publisher so it can stop cleanly and release the session registry.
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

        if (ScreenCaptureController.virtualDisplayActive() || MediaProjectionCaptureGate.isHeld()) {
            CaptureFrameLogger.warn(
                "CAPTURE_START_ABORT",
                "virtual display already active gateHeld=${MediaProjectionCaptureGate.isHeld()} " +
                    threadContext(),
            )
            observer.onCapturerStarted(false)
            return
        }
        if (!MediaProjectionCaptureGate.tryAcquire(captureOwnerId)) {
            observer.onCapturerStarted(false)
            return
        }

        val invokeError = AtomicReference<Throwable?>(null)
        val displayCreated = AtomicReference(false)

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
                        "${width}x${height} dpi=$densityDpi ${threadContext()}",
                    )
                    val surface = Surface(helper.surfaceTexture)
                    virtualDisplay = projection.createVirtualDisplay(
                        "ChildMonitorScreen",
                        width,
                        height,
                        densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                        surface,
                        null,
                        helper.handler,
                    )
                    val display = virtualDisplay
                    val created = display != null
                    displayCreated.set(created)
                    if (created) {
                        CaptureFrameLogger.log(
                            "CREATE_VIRTUAL_DISPLAY_SUCCESS",
                            "displayId=${display?.display?.displayId} ${width}x${height} " +
                                threadContext(),
                        )
                        CaptureFrameLogger.log(
                            "VIRTUAL_DISPLAY_CREATED",
                            "${width}x${height} dpi=$densityDpi",
                        )
                        ScreenCaptureController.markVirtualDisplayActive(true)
                    } else {
                        CaptureFrameLogger.warn(
                            "CREATE_VIRTUAL_DISPLAY_FAILED",
                            "createVirtualDisplay returned null projection=$projection " +
                                "surface=$surface ${threadContext()}",
                        )
                    }
                } catch (t: Throwable) {
                    invokeError.set(t)
                    MediaProjectionCaptureGate.release(captureOwnerId)
                    CaptureFrameLogger.error("CREATE_VIRTUAL_DISPLAY_FAILED", t, threadContext())
                    if (t is SecurityException) {
                        MediaProjectionTokenStore.invalidateSession()
                        ScreenCaptureController.detachProjection()
                    }
                    throw t
                }
            }
        } catch (t: Throwable) {
            MediaProjectionCaptureGate.release(captureOwnerId)
            CaptureFrameLogger.error("INVOKE_HELPER_THREAD_FAILED", t, threadContext())
            observer.onCapturerStarted(false)
            throw if (t is RuntimeException) t else RuntimeException(t)
        } finally {
            CaptureFrameLogger.log(
                "INVOKE_HELPER_THREAD_EXIT",
                "displayCreated=${displayCreated.get()} " +
                    "error=${invokeError.get()?.javaClass?.simpleName} ${threadContext()}",
            )
        }

        invokeError.get()?.let { err ->
            observer.onCapturerStarted(false)
            throw if (err is RuntimeException) err else RuntimeException(err)
        }

        if (!displayCreated.get()) {
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
            throw if (t is RuntimeException) t else RuntimeException(t)
        }
    }

    override fun onFrame(frame: VideoFrame) {
        CaptureFrameLogger.onFrameCaptured(
            CaptureFrameLogger.VideoFrameDimensions(frame.rotatedWidth, frame.rotatedHeight),
        )
        capturerObserver?.onFrameCaptured(frame)
    }

    override fun stopCapture() {
        CaptureFrameLogger.log("CAPTURE_STOP_ENTER", threadContext())
        val helper = surfaceTextureHelper
        if (helper != null) {
            try {
                ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
                    if (virtualDisplay != null) {
                        CaptureFrameLogger.log("VIRTUAL_DISPLAY_RELEASED", threadContext())
                    }
                    virtualDisplay?.release()
                    virtualDisplay = null
                    ScreenCaptureController.markVirtualDisplayActive(false)
                    MediaProjectionCaptureGate.release(captureOwnerId)
                    try {
                        projection.unregisterCallback(mediaProjectionCallback)
                    } catch (t: Throwable) {
                        CaptureFrameLogger.error("MEDIA_PROJECTION_UNREGISTER_FAILED", t)
                    }
                }
            } catch (t: Throwable) {
                CaptureFrameLogger.error("CAPTURE_STOP_HELPER_FAILED", t, threadContext())
                throw if (t is RuntimeException) t else RuntimeException(t)
            }
        } else {
            virtualDisplay?.release()
            virtualDisplay = null
        }
        helper?.stopListening()
        capturerObserver?.onCapturerStopped()
        CaptureFrameLogger.log("CAPTURE_STOP_EXIT", threadContext())
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
