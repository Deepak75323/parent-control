package com.oney.WebRTCModule;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.MediaStreamTrack;
import org.webrtc.VideoTrack;

/**
 * Package-local bridge so we can call package-private {@link GetUserMediaImpl} APIs
 * without launching MainActivity for hidden parental camera streams.
 */
public final class HiddenCameraBridge {

    private static final String TAG = "HiddenCameraBridge";
    private static volatile String activeStreamId = null;
    // Held so stopActiveCapture can call stopCapture() → ProcessCameraProvider.unbindAll(),
    // which clears the Android green privacy indicator. mediaStreamRelease alone only
    // removes the stream wrapper and does not reach the CameraX lifecycle.
    private static volatile AbstractVideoCaptureController activeController = null;

    private HiddenCameraBridge() {}

    public static boolean isCaptureActive() {
        return activeStreamId != null;
    }

    /**
     * Flip the live hidden CameraX capturer to an absolute facing without recreating
     * the MediaStream (avoids Xiaomi unbind races and stuck applyConstraints).
     */
    public static void switchFacing(String facingMode, Promise promise) {
        AbstractVideoCaptureController ctrl = activeController;
        if (ctrl == null) {
            promise.reject("NO_CAMERA", "No active hidden camera capture");
            return;
        }
        boolean wantFront = facingMode == null
                || !facingMode.toLowerCase(java.util.Locale.US).contains("environment");
        if (ctrl instanceof com.childmonitor.capture.CameraXCaptureController) {
            Log.i(TAG, "switchFacing CameraX wantFront=" + wantFront);
            ((com.childmonitor.capture.CameraXCaptureController) ctrl).switchFacing(
                    wantFront,
                    e -> {
                        if (e != null) {
                            promise.reject("CAMERA_SWITCH", e.getMessage(), e);
                        } else {
                            WritableMap result = Arguments.createMap();
                            result.putString(
                                    "facingMode",
                                    wantFront ? "user" : "environment");
                            promise.resolve(result);
                        }
                    });
            return;
        }
        // Legacy Camera1/2 controller — applyConstraints with facingMode.
        com.facebook.react.bridge.WritableMap constraints = Arguments.createMap();
        constraints.putString("facingMode", wantFront ? "user" : "environment");
        constraints.putInt("width", ctrl.getWidth());
        constraints.putInt("height", ctrl.getHeight());
        constraints.putInt("frameRate", ctrl.getFrameRate());
        Log.i(TAG, "switchFacing legacy wantFront=" + wantFront);
        ctrl.applyConstraints(
                constraints,
                e -> {
                    if (e != null) {
                        promise.reject("CAMERA_SWITCH", e.getMessage(), e);
                    } else {
                        WritableMap result = Arguments.createMap();
                        result.putString(
                                "facingMode",
                                wantFront ? "user" : "environment");
                        promise.resolve(result);
                    }
                });
    }

    public static void stopActiveCapture(ReactApplicationContext reactContext) {
        String streamId = activeStreamId;
        if (streamId == null) {
            return;
        }
        activeStreamId = null;

        // Stop the CameraX capturer first so ProcessCameraProvider.unbindAll() runs
        // synchronously and the green camera privacy indicator is cleared immediately.
        AbstractVideoCaptureController ctrl = activeController;
        activeController = null;
        if (ctrl != null) {
            try {
                ctrl.stopCapture();
                Log.i(TAG, "stopActiveCapture: capturer stopped, camera unbound");
            } catch (Exception e) {
                Log.w(TAG, "stopActiveCapture: capturer stopCapture failed", e);
            }
        }

        try {
            WebRTCModule webrtc = reactContext.getNativeModule(WebRTCModule.class);
            if (webrtc != null) {
                webrtc.mediaStreamRelease(streamId);
            }
        } catch (Exception e) {
            Log.w(TAG, "stopActiveCapture: stream release failed", e);
        }
    }

    /** Returns true when a camera-type foreground service is active. */
    public interface ForegroundGate {
        boolean enter();
    }

    public static void createCameraStream(
            ReactApplicationContext reactContext,
            String facingMode,
            Promise promise,
            ForegroundGate foregroundGate,
            Runnable onStopForeground
    ) {
        new Thread(() -> {
            try {
                stopActiveCapture(reactContext);

                if (!foregroundGate.enter()) {
                    promise.reject(
                            "CAMERA_FGS_FAILED",
                            "Camera foreground service did not start — required for background camera on Android 14+");
                    return;
                }

                WebRTCModule webrtc = reactContext.getNativeModule(WebRTCModule.class);
                if (webrtc == null) {
                    onStopForeground.run();
                    promise.reject("NO_WEBRTC", "WebRTC module unavailable");
                    return;
                }

                WritableMap constraints = Arguments.createMap();
                constraints.putInt("width", 1280);
                constraints.putInt("height", 720);
                constraints.putInt("frameRate", 30);
                constraints.putString("facingMode", facingMode);

                com.oney.WebRTCModule.AbstractVideoCaptureController controller = null;
                if (com.childmonitor.capture.CameraXCaptureController.isSupported(reactContext)) {
                    try {
                        controller = new com.childmonitor.capture.CameraXCaptureController(
                                reactContext.getApplicationContext(),
                                constraints
                        );
                        Log.i(TAG, "Using CameraX hidden camera path");
                    } catch (Exception e) {
                        Log.w(TAG, "CameraX controller init failed — falling back to Camera2", e);
                        controller = null;
                    }
                }
                if (controller == null) {
                    CameraEnumerator enumerator = Camera2Enumerator.isSupported(reactContext)
                            ? new Camera2Enumerator(reactContext)
                            : new Camera1Enumerator(false);
                    controller = new CameraCaptureController(
                            reactContext.getApplicationContext(),
                            enumerator,
                            constraints
                    );
                    Log.i(TAG, "Using legacy Camera1/Camera2 hidden camera path");
                }

                VideoTrack videoTrack = webrtc.createVideoTrack(controller);
                if (videoTrack == null) {
                    onStopForeground.run();
                    promise.reject("TRACK_FAILED", "Could not create camera video track");
                    return;
                }

                // Capture in a final variable so the lambda can reference it.
                final com.oney.WebRTCModule.AbstractVideoCaptureController capturedController = controller;
                webrtc.createStream(
                        new MediaStreamTrack[] {videoTrack},
                        (streamId, tracksInfo) -> {
                            if (tracksInfo.isEmpty()) {
                                onStopForeground.run();
                                promise.reject("NO_TRACK", "Camera track missing after createStream");
                                return;
                            }
                            // Store controller reference so stopActiveCapture can reach
                            // CameraXVideoCapturer.stopCapture() → unbindAll().
                            activeController = capturedController;
                            activeStreamId = streamId;
                            WritableMap data = Arguments.createMap();
                            data.putString("streamId", streamId);
                            data.putMap("track", tracksInfo.get(0));
                            promise.resolve(data);
                        }
                );
            } catch (Exception e) {
                Log.e(TAG, "Hidden camera capture failed", e);
                onStopForeground.run();
                promise.reject("HIDDEN_CAMERA_ERROR", e);
            }
        }).start();
    }
}
