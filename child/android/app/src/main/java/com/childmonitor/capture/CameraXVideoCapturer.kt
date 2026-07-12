package com.childmonitor.capture

import android.content.Context
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
    }

    private val lifecycleOwner = CapturerLifecycleOwner()
    private val started = AtomicBoolean(false)
    private val firstFrameLogged = AtomicBoolean(false)

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
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                future.get().unbindAll()
            } catch (e: Exception) {
                Log.w(TAG, "CameraX unbind failed", e)
            }
            lifecycleOwner.markStopped()
            capturerObserver?.onCapturerStopped()
        }, ContextCompat.getMainExecutor(appContext))
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

    override fun switchCamera(handler: CameraVideoCapturer.CameraSwitchHandler?) {
        useFrontCamera = !useFrontCamera
        firstFrameLogged.set(false)
        handler?.onCameraSwitchDone(useFrontCamera)
        if (started.get()) {
            val helper = surfaceTextureHelper ?: return
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener({
                try {
                    bindPreview(helper)
                } catch (e: Exception) {
                    handler?.onCameraSwitchError(e.message ?: "switch failed")
                }
            }, ContextCompat.getMainExecutor(appContext))
        }
    }

    override fun switchCamera(
        handler: CameraVideoCapturer.CameraSwitchHandler?,
        cameraName: String?,
    ) {
        useFrontCamera = cameraName == null || !cameraName.contains("environment")
        switchCamera(handler)
    }
}
