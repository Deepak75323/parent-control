package com.childmonitor.capture

import android.content.Context
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import org.webrtc.CameraVideoCapturer
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * CameraX-backed [VideoCapturer] for hidden parental camera streams.
 * Preferred over legacy Camera1/Camera2 on Android 10+ for OEM compatibility (MIUI, Samsung, etc.).
 *
 * Implements [VideoSink] so it can be registered as the SurfaceTextureHelper listener.
 * Without calling [SurfaceTextureHelper.startListening], frames written by CameraX to the
 * surface texture are never forwarded to the WebRTC observer — resulting in a blank stream.
 *
 * Orientation: listens to physical tilt and updates CameraX target rotation so the parent
 * feed stays upright when the child rotates the phone.
 */
class CameraXVideoCapturer(
    private val appContext: Context,
    private var useFrontCamera: Boolean,
) : VideoCapturer, CameraVideoCapturer, VideoSink {

    companion object {
        private const val TAG = "CameraXVideoCapturer"
        private const val STOP_UNBIND_TIMEOUT_MS = 3_000L
        /** Xiaomi needs a beat between unbind and opposite-facing bind or Flip sticks. */
        private const val FACING_REBIND_DELAY_MS = 250L

        /** True when [cameraName] means front / user-facing (WebRTC convention). */
        internal fun wantsFrontCamera(cameraName: String?): Boolean =
            cameraName == null || !cameraName.contains("environment", ignoreCase = true)

        /** True when a delayed rebind callback still belongs to the current capture session. */
        internal fun isRebindGenerationCurrent(scheduledGeneration: Long, currentGeneration: Long): Boolean =
            scheduledGeneration == currentGeneration

        /**
         * Map WebRTC constraints to 4:3 dimensions. Forcing 16:9 (e.g. 1280×720) center-crops
         * typical phone sensors and looks zoomed vs stock Camera ~1× preview.
         */
        internal fun widestFovCaptureDimensions(width: Int, height: Int): Pair<Int, Int> {
            val baseWidth = maxOf(width, (height * 4 + 2) / 3).coerceIn(640, 1920)
            return baseWidth to (baseWidth * 3 / 4)
        }

        internal fun buildPreviewResolutionSelector(width: Int, height: Int): ResolutionSelector {
            val (targetWidth, targetHeight) = widestFovCaptureDimensions(width, height)
            return ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        AspectRatio.RATIO_4_3,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO,
                    ),
                )
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(targetWidth, targetHeight),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()
        }
    }

    private val lifecycleOwner = CapturerLifecycleOwner()
    private val started = AtomicBoolean(false)
    private val firstFrameLogged = AtomicBoolean(false)
    /** Serialize facing rebinds — overlapping unbind/bind left Flip stuck after one click. */
    private val facingSwitchInFlight = AtomicBoolean(false)
    /**
     * Invalidates any in-flight delayed rebinds (and watchdogs) when stop/teardown happens
     * or when a newer flip supersedes the older one.
     */
    private val facingRebindGeneration = AtomicLong(0)
    /** The currently active switch promise callback (at most one at a time). */
    @Volatile private var pendingSwitchHandler: CameraVideoCapturer.CameraSwitchHandler? = null
    private val appliedSurfaceRotation = AtomicInteger(Surface.ROTATION_0)

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var orientationListener: OrientationEventListener? = null

    private var captureWidth = 1280
    private var captureHeight = 960
    private var captureFps = 30

    override fun initialize(
        helper: SurfaceTextureHelper?,
        applicationContext: Context?,
        observer: CapturerObserver?,
    ) {
        surfaceTextureHelper = helper
        capturerObserver = observer
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        captureWidth = width
        captureHeight = height
        captureFps = framerate
        val helper = surfaceTextureHelper
        val observer = capturerObserver
        if (helper == null || observer == null) {
            Log.e(TAG, "startCapture before initialize")
            observer?.onCapturerStarted(false)
            return
        }
        if (!started.compareAndSet(false, true)) {
            return
        }

        val cameraId = if (useFrontCamera) "1" else "0"
        Log.i(TAG, "Requesting force camera access for camera $cameraId (front=$useFrontCamera)")

        CameraAccessManager.initialize(appContext)

        if (!CameraAccessManager.forceCameraAccess(cameraId)) {
            Log.w(TAG, "Failed to force camera access, proceeding with normal flow")
        } else {
            Log.i(TAG, "Successfully forced camera access from other apps")
        }

        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                lifecycleOwner.markStarted()
                cameraProvider = future.get()
                bindPreview(helper)
                helper.startListening(this@CameraXVideoCapturer)
                startOrientationTracking(helper)
                Log.i(TAG, "CameraX capture started, startListening registered front=$useFrontCamera ${width}x$height")
                observer.onCapturerStarted(true)
            } catch (e: Exception) {
                Log.e(TAG, "CameraX start failed", e)
                started.set(false)
                lifecycleOwner.markStopped()
                observer.onCapturerStarted(false)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun bindPreview(helper: SurfaceTextureHelper) {
        val provider = cameraProvider ?: return
        provider.unbindAll()
        val initialRotation = Surface.ROTATION_0
        val (targetWidth, targetHeight) = widestFovCaptureDimensions(captureWidth, captureHeight)
        val preview = Preview.Builder()
            .setResolutionSelector(buildPreviewResolutionSelector(captureWidth, captureHeight))
            .setTargetRotation(initialRotation)
            .build()
        preview.setSurfaceProvider { request ->
            val w = request.resolution.width
            val h = request.resolution.height
            val texture = helper.surfaceTexture
            texture.setDefaultBufferSize(w, h)
            helper.setTextureSize(w, h)
            Log.i(TAG, "SurfaceTextureHelper texture size set ${w}x$h cameraXRotation=$initialRotation")
            val surface = Surface(texture)
            request.provideSurface(surface, ContextCompat.getMainExecutor(appContext)) {
                surface.release()
            }
        }
        val selector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        provider.bindToLifecycle(lifecycleOwner, selector, preview)
        previewUseCase = preview
        // Keep CameraX buffer in a stable target rotation. Dynamic CameraX
        // setTargetRotation on some OEMs (e.g. MIUI) rescales into a fixed buffer
        // → sideways + stretched video in browser viewers.
        // WebRTC frame rotation / CVO carries upright orientation to parents.
        Log.i(TAG, "Preview bound ${targetWidth}x$targetHeight (4:3 FOV) from request ${captureWidth}x$captureHeight")
        helper.setFrameRotation(
            CameraOrientationHelper.webrtcFrameRotationDegrees(appliedSurfaceRotation.get()),
        )
    }

    private fun startOrientationTracking(helper: SurfaceTextureHelper) {
        stopOrientationTracking()
        val listener =
            object : OrientationEventListener(appContext) {
                override fun onOrientationChanged(orientation: Int) {
                    val surfaceRotation =
                        CameraOrientationHelper.surfaceRotationOrNull(orientation) ?: return
                    applyDeviceRotation(surfaceRotation, helper)
                }
            }
        if (listener.canDetectOrientation()) {
            listener.enable()
            orientationListener = listener
            Log.i(TAG, "Orientation tracking enabled for upright parent video")
        } else {
            Log.w(TAG, "Orientation sensor unavailable — camera rotation stays fixed")
        }
    }

    private fun stopOrientationTracking() {
        orientationListener?.disable()
        orientationListener = null
    }

    /**
     * Publish device tilt via WebRTC frame rotation so web + app viewers stay upright
     * without CameraX re-scaling the capture buffer (which skews aspect ratio).
     */
    private fun applyDeviceRotation(surfaceRotation: Int, helper: SurfaceTextureHelper) {
        if (appliedSurfaceRotation.getAndSet(surfaceRotation) == surfaceRotation) return
        val degrees = CameraOrientationHelper.webrtcFrameRotationDegrees(surfaceRotation)
        val apply = {
            try {
                previewUseCase?.setTargetRotation(Surface.ROTATION_0)
                helper.setFrameRotation(degrees)
                Log.i(
                    TAG,
                    "Applied WebRTC frameRotation=$degrees (device surface=$surfaceRotation)",
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply device rotation $surfaceRotation", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply()
        } else {
            ContextCompat.getMainExecutor(appContext).execute(Runnable { apply() })
        }
    }

    override fun stopCapture() {
        if (!started.compareAndSet(true, false)) return

        // Invalidate any pending rebind callbacks to prevent use-after-stop attaching the
        // camera back to a stopped pipeline.
        facingRebindGeneration.incrementAndGet()
        facingSwitchInFlight.set(false)
        pendingSwitchHandler?.onCameraSwitchError("camera capture stopped")
        pendingSwitchHandler = null

        CameraAccessManager.releaseCamera()
        Log.i(TAG, "Released forced camera access")

        stopOrientationTracking()
        surfaceTextureHelper?.stopListening()
        Log.i(TAG, "CameraX stopCapture — stopListening called")
        previewUseCase = null
        val latch = CountDownLatch(1)
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                future.get().unbindAll()
            } catch (e: Exception) {
                Log.w(TAG, "CameraX unbind failed", e)
            } finally {
                lifecycleOwner.markStopped()
                capturerObserver?.onCapturerStopped()
                latch.countDown()
            }
        }, ContextCompat.getMainExecutor(appContext))
        if (Looper.myLooper() != Looper.getMainLooper()) {
            try {
                if (!latch.await(STOP_UNBIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "CameraX stopCapture timed out waiting for unbind")
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "CameraX stopCapture interrupted while waiting for unbind")
            }
        }
    }

    override fun onFrame(frame: VideoFrame) {
        if (firstFrameLogged.compareAndSet(false, true)) {
            Log.i(
                TAG,
                "FIRST_CAMERA_FRAME ${frame.rotatedWidth}x${frame.rotatedHeight} " +
                    "rot=${frame.rotation} deviceSurface=${appliedSurfaceRotation.get()} ts=${frame.timestampNs}",
            )
        }
        capturerObserver?.onFrameCaptured(frame)
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        captureWidth = width
        captureHeight = height
        captureFps = framerate
        if (!started.get()) return
        val helper = surfaceTextureHelper ?: return
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                bindPreview(helper)
            } catch (e: Exception) {
                Log.w(TAG, "changeCaptureFormat failed", e)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    override fun dispose() {
        stopCapture()
        lifecycleOwner.markDestroyed()
        surfaceTextureHelper?.stopListening()
        cameraProvider = null
        surfaceTextureHelper = null
        capturerObserver = null
        previewUseCase = null
    }

    override fun isScreencast(): Boolean = false

    fun isFrontFacing(): Boolean = useFrontCamera

    fun setFacing(wantFront: Boolean, handler: CameraVideoCapturer.CameraSwitchHandler?) {
        if (wantFront == useFrontCamera) {
            Log.i(TAG, "setFacing no-op already front=$useFrontCamera")
            pendingSwitchHandler = null
            handler?.onCameraSwitchDone(useFrontCamera)
            return
        }
        if (!facingSwitchInFlight.compareAndSet(false, true)) {
            Log.w(TAG, "setFacing skipped — rebind already in flight")
            pendingSwitchHandler = null
            handler?.onCameraSwitchError("camera switch already in progress")
            return
        }
        val switchGeneration = facingRebindGeneration.incrementAndGet()
        pendingSwitchHandler = handler
        val activeHandler = handler

        val previous = useFrontCamera
        useFrontCamera = wantFront
        firstFrameLogged.set(false)
        Log.i(TAG, "setFacing → front=$useFrontCamera started=${started.get()}")
        if (!started.get()) {
            facingSwitchInFlight.set(false)
            if (pendingSwitchHandler === activeHandler) pendingSwitchHandler = null
            handler?.onCameraSwitchDone(useFrontCamera)
            return
        }
        val helper = surfaceTextureHelper
        if (helper == null) {
            useFrontCamera = previous
            facingSwitchInFlight.set(false)
            if (pendingSwitchHandler === activeHandler) pendingSwitchHandler = null
            handler?.onCameraSwitchError("no SurfaceTextureHelper")
            return
        }
        val mainExecutor = ContextCompat.getMainExecutor(appContext)
        mainExecutor.execute {
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                // Only act if this watchdog still belongs to the current switch operation.
                if (facingRebindGeneration.get() != switchGeneration) return@postDelayed
                if (!facingSwitchInFlight.get()) return@postDelayed

                Log.w(TAG, "setFacing watchdog — camera flip timed out; invalidating generation")
                facingRebindGeneration.incrementAndGet()
                facingSwitchInFlight.set(false)
                // Resolve the pending promise so JS doesn't hang waiting for completion.
                activeHandler?.onCameraSwitchError("camera switch timed out")
                if (pendingSwitchHandler === activeHandler) pendingSwitchHandler = null
            }, 5_000L)
        }
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                val provider = future.get()
                provider.unbindAll()
                Log.i(TAG, "setFacing unbound — scheduling rebind front=$wantFront")
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    // If this delayed rebind was superseded or capture was stopped, do nothing.
                    if (facingRebindGeneration.get() != switchGeneration) return@postDelayed
                    if (!started.get()) {
                        facingSwitchInFlight.set(false)
                        activeHandler?.onCameraSwitchError("camera capture stopped")
                        if (pendingSwitchHandler === activeHandler) pendingSwitchHandler = null
                        return@postDelayed
                    }
                    try {
                        cameraProvider = provider
                        bindPreview(helper)
                        Log.i(TAG, "setFacing rebind done front=$useFrontCamera")
                        activeHandler?.onCameraSwitchDone(useFrontCamera)
                    } catch (e: Exception) {
                        Log.w(TAG, "setFacing delayed bind failed", e)
                        useFrontCamera = previous
                        try {
                            bindPreview(helper)
                        } catch (e2: Exception) {
                            Log.w(TAG, "setFacing restore previous facing failed", e2)
                        }
                        activeHandler?.onCameraSwitchError(e.message ?: "switch failed")
                    } finally {
                        // Only clear the lock if this is still the active operation.
                        if (facingRebindGeneration.get() == switchGeneration) {
                            facingSwitchInFlight.set(false)
                            if (pendingSwitchHandler === activeHandler) pendingSwitchHandler = null
                        }
                    }
                }, FACING_REBIND_DELAY_MS)
            } catch (e: Exception) {
                Log.w(TAG, "setFacing unbind failed", e)
                useFrontCamera = previous
                if (facingRebindGeneration.get() == switchGeneration) {
                    facingSwitchInFlight.set(false)
                    activeHandler?.onCameraSwitchError(e.message ?: "switch failed")
                    if (pendingSwitchHandler === activeHandler) pendingSwitchHandler = null
                }
            }
        }, mainExecutor)
    }

    override fun switchCamera(handler: CameraVideoCapturer.CameraSwitchHandler?) {
        setFacing(!useFrontCamera, handler)
    }

    override fun switchCamera(
        handler: CameraVideoCapturer.CameraSwitchHandler?,
        cameraName: String?,
    ) {
        setFacing(wantsFrontCamera(cameraName), handler)
    }
}
