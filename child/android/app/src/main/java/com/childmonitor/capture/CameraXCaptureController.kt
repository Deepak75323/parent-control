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

    /**
     * Include facingMode so JS [MediaStreamTrack._switchCamera] / applyConstraints
     * can tell front from back. The base class omits it, which made Flip always
     * request "user" and no-op on the default front camera.
     */
    override fun getSettings(): com.facebook.react.bridge.WritableMap {
        val settings = super.getSettings()
        settings.putString("facingMode", if (useFrontCamera) "user" else "environment")
        return settings
    }

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
        if (constraints.hasKey("width")) {
            targetWidth = constraints.getInt("width")
        }
        if (constraints.hasKey("height")) {
            targetHeight = constraints.getInt("height")
        }
        if (constraints.hasKey("frameRate")) {
            targetFps = constraints.getInt("frameRate")
        }

        switchFacing(wantFront, onFinishedCallback)
    }

    /**
     * Switch to an absolute facing without a second rebind. Calling [changeCaptureFormat]
     * after setFacing was rebinding again and left Flip stuck after the first click.
     */
    fun switchFacing(wantFront: Boolean, onFinishedCallback: Consumer<Exception>?) {
        val capturer = videoCapturer as? CameraXVideoCapturer
        if (capturer == null) {
            onFinishedCallback?.accept(
                UnsupportedOperationException("CameraX capturer not initialized"),
            )
            return
        }
        if (wantFront == capturer.isFrontFacing()) {
            useFrontCamera = wantFront
            Log.i(TAG, "switchFacing no-op already front=$wantFront")
            onFinishedCallback?.accept(null)
            return
        }
        capturer.setFacing(
            wantFront,
            object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    useFrontCamera = isFrontCamera
                    Log.i(TAG, "switchFacing done front=$isFrontCamera")
                    onFinishedCallback?.accept(null)
                }

                override fun onCameraSwitchError(errorDescription: String?) {
                    useFrontCamera = capturer.isFrontFacing()
                    Log.w(TAG, "switchFacing error: $errorDescription")
                    onFinishedCallback?.accept(Exception(errorDescription ?: "Camera switch failed"))
                }
            },
        )
    }
}
