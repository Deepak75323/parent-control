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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX-backed [VideoCapturer] for hidden parental camera streams.
 * Preferred over legacy Camera1/Camera2 on Android 10+ for OEM compatibility (MIUI, Samsung, etc.).
 */
class CameraXVideoCapturer(
    private val appContext: Context,
    private var useFrontCamera: Boolean,
) : VideoCapturer, CameraVideoCapturer {

    companion object {
        private const val TAG = "CameraXVideoCapturer"
    }

    private val lifecycleOwner = CapturerLifecycleOwner()
    private val started = AtomicBoolean(false)

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
                observer.onCapturerStarted(true)
                Log.i(TAG, "CameraX capture started front=$useFrontCamera ${width}x$height")
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
            val texture = helper.surfaceTexture
            texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
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
        cameraProvider = null
        surfaceTextureHelper = null
        capturerObserver = null
    }

    override fun isScreencast(): Boolean = false

    override fun switchCamera(handler: CameraVideoCapturer.CameraSwitchHandler?) {
        useFrontCamera = !useFrontCamera
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
