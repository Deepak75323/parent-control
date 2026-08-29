package com.childmonitor.capture

import android.content.Context
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlin.math.abs
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

        /** True when [size] is ~16:9 (landscape listing). */
        internal fun isSixteenNine(size: Size): Boolean {
            val ratio = size.width.toDouble() / size.height.toDouble()
            return abs(ratio - 16.0 / 9.0) < 0.05
        }

        /**
         * Stock Camera front preview is 16:9 full-screen (e.g. 1920×1080), not max 4:3 sensor.
         */
        internal fun selectNativeLikeFrontPreviewSize(supportedSizes: List<Size>): Size {
            if (supportedSizes.isEmpty()) {
                return Size(1920, 1080)
            }
            val normalized = supportedSizes.map(::normalizeLandscape)
            val sixteenNine = normalized.filter(::isSixteenNine)
            val pool =
                when {
                    sixteenNine.isNotEmpty() -> sixteenNine
                    normalized.any(::isExactFourThree) -> normalized.filter(::isExactFourThree)
                    else -> normalized
                }
            pool.firstOrNull { it.width == 1920 && it.height == 1080 }?.let { return it }
            pool.firstOrNull { it.width == 1280 && it.height == 720 }?.let { return it }
            val capped = pool.filter { it.width <= 1920 && it.height <= 1080 }
            if (capped.isNotEmpty()) {
                return capped.maxByOrNull { it.width.toLong() * it.height }!!
            }
            return pool.maxByOrNull { it.width.toLong() * it.height }
                ?: Size(1920, 1080)
        }

        /** Native 1× — display crop on the parent handles fill like PreviewView FILL_CENTER. */
        internal fun previewZoomRatio(useFrontCamera: Boolean, minZoom: Float, maxZoom: Float): Float {
            return 1.0f.coerceIn(minZoom, maxZoom)
        }

        /**
         * Map WebRTC constraints to 4:3 dimensions. Forcing 16:9 (e.g. 1280×720) center-crops
         * typical phone sensors and looks zoomed vs stock Camera ~1× preview.
         */
        internal fun widestFovCaptureDimensions(width: Int, height: Int): Pair<Int, Int> {
            val baseWidth = maxOf(width, (height * 4 + 2) / 3).coerceIn(640, 3264)
            return baseWidth to (baseWidth * 3 / 4)
        }

        /** Exact 4:3 (avoids MIUI sizes like 1440×1088 that crop vs stock Camera). */
        internal fun isExactFourThree(size: Size): Boolean =
            size.width * 3 == size.height * 4 || size.height * 3 == size.width * 4

        /** True when [size] is ~4:3 or ~3:4 (portrait listing). */
        internal fun isFourThreeAspect(size: Size): Boolean {
            if (isExactFourThree(size)) return true
            val ratio = size.width.toDouble() / size.height.toDouble()
            return abs(ratio - 4.0 / 3.0) < 0.05 || abs(ratio - 3.0 / 4.0) < 0.05
        }

        internal fun normalizeLandscape(size: Size): Size =
            if (size.width >= size.height) size else Size(size.height, size.width)

        internal fun matchesLandscapeSize(candidate: Size, target: Size): Boolean {
            val normalized = normalizeLandscape(candidate)
            return normalized.width == target.width && normalized.height == target.height
        }

        /**
         * Pick the largest exact 4:3 preview size (widest sensor FOV on most phones).
         * MIUI often exposes 1440×1088 (non-standard) — prefer exact 4:3 like 1920×1440.
         */
        internal fun selectWidestFourThreeSize(supportedSizes: List<Size>): Size {
            if (supportedSizes.isEmpty()) {
                return Size(1920, 1440)
            }
            val normalized = supportedSizes.map(::normalizeLandscape)
            val exactFourThree = normalized.filter(::isExactFourThree)
            if (exactFourThree.isNotEmpty()) {
                return exactFourThree.maxByOrNull { it.width.toLong() * it.height }!!
            }
            val approxFourThree = normalized.filter(::isFourThreeAspect)
            val pool = if (approxFourThree.isNotEmpty()) approxFourThree else normalized
            return pool.maxByOrNull { it.width.toLong() * it.height } ?: normalized.first()
        }

        internal fun buildPreviewResolutionSelector(frontCamera: Boolean): ResolutionSelector {
            val aspect =
                if (frontCamera) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
            return ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        aspect,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO,
                    ),
                )
                .setResolutionFilter { supportedSizes, _ ->
                    val chosen =
                        if (frontCamera) {
                            selectNativeLikeFrontPreviewSize(supportedSizes)
                        } else {
                            selectWidestFourThreeSize(supportedSizes)
                        }
                    Log.i(
                        TAG,
                        "Resolution filter chose ${chosen.width}x${chosen.height} " +
                            "front=$frontCamera from ${supportedSizes.size} supported sizes",
                    )
                    listOf(chosen)
                }
                .build()
        }

        internal fun applyStockCameraCaptureHints(previewBuilder: Preview.Builder) {
            try {
                val extender = Camera2Interop.Extender(previewBuilder)
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_AUTO,
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON,
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO,
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY,
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.EDGE_MODE,
                    CaptureRequest.EDGE_MODE_HIGH_QUALITY,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Camera2 capture hints skipped", e)
            }
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
    private var boundCamera: Camera? = null
    private var orientationListener: OrientationEventListener? = null

    /** Called when CameraX picks the real buffer size — sync WebRTC to avoid center-crop zoom. */
    var onActualOutputSize: ((width: Int, height: Int) -> Unit)? = null

    private var captureWidth = 1920
    private var captureHeight = 1440
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

        if (!CameraAccessManager.ensureCameraReadyForCapture(cameraId)) {
            Log.e(TAG, "Failed to prepare camera access — another app may be using the camera")
            started.set(false)
            observer.onCapturerStarted(false)
            return
        }

        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                lifecycleOwner.markStarted()
                cameraProvider = future.get()
                if (!bindPreviewWithDisplacementFallback(helper, cameraId)) {
                    started.set(false)
                    lifecycleOwner.markStopped()
                    observer.onCapturerStarted(false)
                    return@addListener
                }
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

    private fun bindPreviewWithDisplacementFallback(
        helper: SurfaceTextureHelper,
        cameraId: String,
    ): Boolean {
        try {
            bindPreview(helper)
            return true
        } catch (first: Exception) {
            if (!CameraAccessManager.lastProbeWasSkipped()) {
                Log.e(TAG, "CameraX bind failed", first)
                return false
            }
            Log.w(TAG, "CameraX bind failed after skipped probe — displacing and retrying once", first)
            if (!CameraAccessManager.displaceForCapture(cameraId)) {
                Log.e(TAG, "Camera displacement retry failed")
                return false
            }
            try {
                bindPreview(helper)
                return true
            } catch (second: Exception) {
                Log.e(TAG, "CameraX bind failed after displacement retry", second)
                return false
            }
        }
    }

    private fun bindPreview(helper: SurfaceTextureHelper) {
        val provider = cameraProvider ?: return
        provider.unbindAll()
        // Keep CameraX buffer at ROTATION_0 — hidden/background capture has no reliable
        // display rotation and bind-time setTargetRotation + frame rotation double-applies
        // on MIUI → black parent feed. Upright output uses WebRTC frame rotation only.
        val initialRotation = Surface.ROTATION_0
        val (targetWidth, targetHeight) = widestFovCaptureDimensions(captureWidth, captureHeight)
        val previewBuilder = Preview.Builder()
            .setResolutionSelector(buildPreviewResolutionSelector(useFrontCamera))
            .setTargetRotation(initialRotation)
        applyStockCameraCaptureHints(previewBuilder)
        val preview = previewBuilder.build()
        preview.setSurfaceProvider { request ->
            val w = request.resolution.width
            val h = request.resolution.height
            val texture = helper.surfaceTexture
            texture.setDefaultBufferSize(w, h)
            helper.setTextureSize(w, h)
            captureWidth = w
            captureHeight = h
            onActualOutputSize?.invoke(w, h)
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
        boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview)
        previewUseCase = preview
        resetZoomToNativeOneX(boundCamera)
        appliedSurfaceRotation.set(initialRotation)
        helper.setFrameRotation(
            CameraOrientationHelper.webrtcFrameRotationDegrees(appliedSurfaceRotation.get()),
        )
        Log.i(
            TAG,
            "Preview bound ${targetWidth}x$targetHeight (4:3 FOV) from request ${captureWidth}x$captureHeight",
        )
    }

    /**
     * Match stock Camera "1×" — zoom ratio 1.0, not [ZoomState.minZoomRatio].
     * MIUI/HyperOS front cameras often expose ultra-wide below 1.0; min zoom adds fisheye
     * and a circular vignette haze that native Camera at 1× does not show.
     */
    private fun resetZoomToNativeOneX(camera: Camera?) {
        if (camera == null) return
        try {
            val zoomState = camera.cameraInfo.zoomState.value
            val min = zoomState?.minZoomRatio ?: 1.0f
            val max = zoomState?.maxZoomRatio ?: 1.0f
            val target = previewZoomRatio(useFrontCamera, min, max)
            camera.cameraControl.setZoomRatio(target)
            Log.i(
                TAG,
                "Zoom set to ${target}x (front=$useFrontCamera, range ${min}–${max})",
            )
        } catch (e: Exception) {
            try {
                val fallback = previewZoomRatio(useFrontCamera, 1.0f, 1.0f)
                camera.cameraControl.setZoomRatio(fallback)
                Log.i(TAG, "Zoom reset to ${fallback}x fallback")
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to reset preview zoom", e2)
            }
        }
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
                // Do not call Preview.setTargetRotation here — MIUI rescales into a fixed
                // buffer and the parent feed looks horizontally stretched. WebRTC CVO only.
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
        boundCamera = null
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
