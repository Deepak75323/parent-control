package com.oney.WebRTCModule;

import android.content.Context;
import android.media.projection.MediaProjection;

import com.childmonitor.capture.ReuseProjectionCapturer;

import org.webrtc.VideoCapturer;

/**
 * Screen capture controller that reuses a stored MediaProjection token so
 * react-native-webrtc does not call createScreenCaptureIntent() every session.
 */
public class StoredProjectionScreenController extends AbstractVideoCaptureController {

    private static final int DEFAULT_FPS = 15;
    private final MediaProjection projection;
    private final int densityDpi;

    public StoredProjectionScreenController(
            Context context,
            MediaProjection projection,
            int width,
            int height,
            int densityDpi) {
        super(width, height, DEFAULT_FPS);
        this.projection = projection;
        this.densityDpi = densityDpi;
    }

    @Override
    public String getDeviceId() {
        return "screen-capture-stored";
    }

    @Override
    protected VideoCapturer createVideoCapturer() {
        com.childmonitor.capture.CaptureFrameLogger.log(
            "CREATE_VIDEO_CAPTURER",
            projection != null ? "projection=ok dpi=" + densityDpi : "projection=null"
        );
        return new ReuseProjectionCapturer(projection, densityDpi, null);
    }

    /**
     * Do not swallow startCapture failures — the stock AbstractVideoCaptureController
     * catches RuntimeException silently, which hid createVirtualDisplay errors.
     */
    @Override
    public void startCapture() {
        com.childmonitor.capture.CaptureFrameLogger.log(
            "CAPTURE_CONTROLLER_START_ENTER",
            targetWidth + "x" + targetHeight + "@" + targetFps
                    + " capturer=" + (videoCapturer != null ? videoCapturer.getClass().getSimpleName() : "null")
        );
        if (videoCapturer == null) {
            com.childmonitor.capture.CaptureFrameLogger.warn(
                "CAPTURE_CONTROLLER_START_ABORT",
                "videoCapturer null"
            );
            return;
        }
        try {
            videoCapturer.startCapture(targetWidth, targetHeight, targetFps);
            com.childmonitor.capture.CaptureFrameLogger.log("CAPTURE_CONTROLLER_START_EXIT", "ok");
        } catch (Throwable e) {
            com.childmonitor.capture.CaptureFrameLogger.error("CAPTURE_CONTROLLER_START_FAILED", e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
