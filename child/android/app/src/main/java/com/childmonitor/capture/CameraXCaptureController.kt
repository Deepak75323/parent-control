package com.childmonitor.capture

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.facebook.react.bridge.ReadableMap
import com.oney.WebRTCModule.AbstractVideoCaptureController
import com.oney.WebRTCModule.ReactBridgeUtil
import org.webrtc.CameraVideoCapturer
import org.webrtc.VideoCapturer

/**
 * CameraX capture controller for [com.oney.WebRTCModule.WebRTCModule.createVideoTrack].
 * Falls back to legacy [com.oney.WebRTCModule.CameraCaptureController] from [HiddenCameraBridge] on failure.
 */
class CameraXCaptureController(
    context: Context,
    private var constraints: ReadableMap,
) : AbstractVideoCaptureController(
    constraints.getInt("width"),
    constraints.getInt("height"),
    constraints.getInt("frameRate"),
) {

    companion object {
        private const val TAG = "CameraXCaptureController"

        @JvmStatic
        fun isSupported(context: Context): Boolean {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return try {
                ProcessCameraProvider.getInstance(context.applicationContext)
                true
            } catch (e: Exception) {
                Log.w(TAG, "CameraX not available", e)
                false
            }
        }
    }

    private val appContext = context.applicationContext
    private var useFrontCamera: Boolean =
        ReactBridgeUtil.getMapStrValue(constraints, "facingMode") != "environment"

    override fun getDeviceId(): String? = if (useFrontCamera) "0" else "1"

    override fun createVideoCapturer(): VideoCapturer {
        Log.i(TAG, "Creating CameraX capturer front=$useFrontCamera")
        return CameraXVideoCapturer(appContext, useFrontCamera)
    }

    override fun applyConstraints(constraints: ReadableMap, onFinishedCallback: Consumer<Exception>?) {
        val facing = ReactBridgeUtil.getMapStrValue(constraints, "facingMode")
        val wantFront = facing == null || facing == "user"
        val capturer = videoCapturer as? CameraXVideoCapturer
        if (capturer == null) {
            onFinishedCallback?.accept(
                UnsupportedOperationException("CameraX capturer not initialized"),
            )
            return
        }
        this.constraints = constraints
        targetWidth = constraints.getInt("width")
        targetHeight = constraints.getInt("height")
        targetFps = constraints.getInt("frameRate")

        if (wantFront == useFrontCamera) {
            capturer.changeCaptureFormat(targetWidth, targetHeight, targetFps)
            onFinishedCallback?.accept(null)
            return
        }
        useFrontCamera = wantFront
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                useFrontCamera = isFrontCamera
                capturer.changeCaptureFormat(targetWidth, targetHeight, targetFps)
                onFinishedCallback?.accept(null)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                onFinishedCallback?.accept(Exception(errorDescription ?: "Camera switch failed"))
            }
        })
    }
}
