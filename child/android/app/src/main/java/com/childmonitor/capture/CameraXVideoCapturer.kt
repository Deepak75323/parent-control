package com.childmonitor.capture

import android.content.Context
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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

/**
 * CameraX-backed [VideoCapturer] for hidden parental camera streams.
 * Preferred over legacy Camera1/Camera2 on Android 10+ for OEM compatibility (MIUI, Samsung, etc.).
 *
 * Implements [VideoSink] so it can be registered as the SurfaceTextureHelper listener.
 * Without calling [SurfaceTextureHelper.startListening], frames written by CameraX to the
 * surface texture are never forwarded to the WebRTC observer — resulting in a blank stream.
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
    }

    private val lifecycleOwner = CapturerLifecycleOwner()
    private val started = AtomicBoolean(false)
    private val firstFrameLogged = AtomicBoolean(false)
    /** Serialize facing rebinds — overlapping unbind/bind left Flip stuck after one click. */
    private val facingSwitchInFlight = AtomicBoolean(false)

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var captureWidth = 1280
    private var captureHeight = 720
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
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                lifecycleOwner.markStarted()
                cameraProvider = future.get()
                bindPreview(helper)
                // Register as VideoSink so every frame CameraX writes to the surface texture
                // is forwarded to the WebRTC capturerObserver. Without this call, frames are
                // captured by the hardware but never reach the encoder — stream is blank.
                helper.startListening(this@CameraXVideoCapturer)
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
        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(captureWidth, captureHeight))
            .build()
        preview.setSurfaceProvider { request ->
            val w = request.resolution.width
            val h = request.resolution.height
            val texture = helper.surfaceTexture
            texture.setDefaultBufferSize(w, h)
            // SurfaceTextureHelper drops every frame with "Texture size has not been set"
            // unless setTextureSize() is called before the first onTextureFrameAvailable
            // callback. This is distinct from SurfaceTexture.setDefaultBufferSize().
            helper.setTextureSize(w, h)
            Log.i(TAG, "SurfaceTextureHelper texture size set ${w}x$h")
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
    }

    override fun stopCapture() {
        if (!started.compareAndSet(true, false)) return
        // Stop forwarding frames before unbinding the camera so the observer is not
        // called after the capture pipeline is torn down.
        surfaceTextureHelper?.stopListening()
        Log.i(TAG, "CameraX stopCapture — stopListening called")
        // Unbind must finish before the next facing opens. Async unbind raced with the
        // following createCameraStream and left flip-back (back→front) unable to bind.
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

    /**
     * VideoSink callback — receives every frame from [SurfaceTextureHelper] and forwards it to
     * the WebRTC [CapturerObserver]. This is the bridge that makes the camera stream visible on
     * the parent side.
     */
    override fun onFrame(frame: VideoFrame) {
        if (firstFrameLogged.compareAndSet(false, true)) {
            Log.i(TAG, "FIRST_CAMERA_FRAME ${frame.rotatedWidth}x${frame.rotatedHeight} ts=${frame.timestampNs}")
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
    }

    override fun isScreencast(): Boolean = false

    /** True when the capturer is bound (or will bind) to the front / user camera. */
    fun isFrontFacing(): Boolean = useFrontCamera

    /**
     * Switch to an absolute facing. Prefer this over toggle — toggle + controller state
     * desync made Flip work front→back once and then no-op on reverse.
     */
    fun setFacing(wantFront: Boolean, handler: CameraVideoCapturer.CameraSwitchHandler?) {
        if (wantFront == useFrontCamera) {
            Log.i(TAG, "setFacing no-op already front=$useFrontCamera")
            handler?.onCameraSwitchDone(useFrontCamera)
            return
        }
        if (!facingSwitchInFlight.compareAndSet(false, true)) {
            Log.w(TAG, "setFacing skipped — rebind already in flight")
            handler?.onCameraSwitchError("camera switch already in progress")
            return
        }
        val previous = useFrontCamera
        useFrontCamera = wantFront
        firstFrameLogged.set(false)
        Log.i(TAG, "setFacing → front=$useFrontCamera started=${started.get()}")
        if (!started.get()) {
            facingSwitchInFlight.set(false)
            handler?.onCameraSwitchDone(useFrontCamera)
            return
        }
        val helper = surfaceTextureHelper
        if (helper == null) {
            useFrontCamera = previous
            facingSwitchInFlight.set(false)
            handler?.onCameraSwitchError("no SurfaceTextureHelper")
            return
        }
        val mainExecutor = ContextCompat.getMainExecutor(appContext)
        // Safety: never leave Flip permanently locked if a callback is dropped.
        mainExecutor.execute {
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (facingSwitchInFlight.get()) {
                    Log.w(TAG, "setFacing watchdog — clearing stuck rebind lock")
                    facingSwitchInFlight.set(false)
                }
            }, 5_000L)
        }
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                val provider = future.get()
                // Unbind first, then delay before bind — Xiaomi HAL often rejects
                // immediate back→front rebind in the same frame (Flip stuck on 2nd click).
                provider.unbindAll()
                Log.i(TAG, "setFacing unbound — scheduling rebind front=$wantFront")
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        cameraProvider = provider
                        bindPreview(helper)
                        Log.i(TAG, "setFacing rebind done front=$useFrontCamera")
                        handler?.onCameraSwitchDone(useFrontCamera)
                    } catch (e: Exception) {
                        Log.w(TAG, "setFacing delayed bind failed", e)
                        useFrontCamera = previous
                        try {
                            bindPreview(helper)
                        } catch (e2: Exception) {
                            Log.w(TAG, "setFacing restore previous facing failed", e2)
                        }
                        handler?.onCameraSwitchError(e.message ?: "switch failed")
                    } finally {
                        facingSwitchInFlight.set(false)
                    }
                }, FACING_REBIND_DELAY_MS)
            } catch (e: Exception) {
                Log.w(TAG, "setFacing unbind failed", e)
                useFrontCamera = previous
                facingSwitchInFlight.set(false)
                handler?.onCameraSwitchError(e.message ?: "switch failed")
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
