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
     * Get the current camera facing mode for state synchronization.
     */
    public static String getCurrentFacing() {
        AbstractVideoCaptureController ctrl = activeController;
        if (ctrl == null) {
            return null;
        }
        if (ctrl instanceof com.childmonitor.capture.CameraXCaptureController) {
            boolean isFront = ((com.childmonitor.capture.CameraXCaptureController) ctrl).isCurrentlyFrontFacing();
            return isFront ? "user" : "environment";
        }
        // For legacy controllers, try to infer from device ID or settings
        try {
            String deviceId = ctrl.getDeviceId();
            // Convention: "0" = front, "1" = back
            if ("0".equals(deviceId)) {
                return "user";
            } else if ("1".equals(deviceId)) {
                return "environment";
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not determine facing from device ID", e);
        }
        return null;
    }

    /**
     * Flip live camera facing without a RN Promise — used when STOMP SWITCH_CAMERA arrives
     * and the JS control listener may be missing.
     */
    public static void switchFacingToggle(java.util.function.BiConsumer<String, Exception> result) {
        String current = getCurrentFacing();
        boolean wantFront = current == null || "environment".equalsIgnoreCase(current);
        String nextFacing = wantFront ? "user" : "environment";
        Log.i(TAG, "switchFacingToggle current=" + current + " next=" + nextFacing);
        switchFacingInternal(nextFacing, result);
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

        Log.i(TAG, "switchFacing request: " + facingMode + " (wantFront=" + wantFront + ")");

        if (ctrl instanceof com.childmonitor.capture.CameraXCaptureController) {
            Log.i(TAG, "switchFacing CameraX wantFront=" + wantFront);
            boolean alreadyFront = ((com.childmonitor.capture.CameraXCaptureController) ctrl)
                    .isCurrentlyFrontFacing();
            final boolean wasNoOp = (wantFront == alreadyFront);
            ((com.childmonitor.capture.CameraXCaptureController) ctrl).switchFacing(
                    wantFront,
                    e -> {
                        if (e != null) {
                            Log.w(TAG, "CameraX switchFacing failed: " + e.getMessage(), e);
                            promise.reject("CAMERA_SWITCH", e.getMessage(), e);
                        } else {
                            boolean actualFront = ((com.childmonitor.capture.CameraXCaptureController) ctrl)
                                    .isCurrentlyFrontFacing();
                            String actualFacing = actualFront ? "user" : "environment";
                            Log.i(TAG, "CameraX switchFacing success: requested=" + facingMode
                                    + ", actual=" + actualFacing + ", noop=" + wasNoOp);
                            WritableMap result = Arguments.createMap();
                            result.putString("facingMode", actualFacing);
                            result.putBoolean("switchSuccessful", !wasNoOp && actualFront == wantFront);
                            result.putBoolean("noop", wasNoOp);
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

    private static void switchFacingInternal(
            String facingMode,
            java.util.function.BiConsumer<String, Exception> result) {
        AbstractVideoCaptureController ctrl = activeController;
        if (ctrl == null) {
            if (result != null) {
                result.accept(null, new IllegalStateException("No active hidden camera capture"));
            }
            return;
        }
        boolean wantFront = facingMode == null
                || !facingMode.toLowerCase(java.util.Locale.US).contains("environment");

        Log.i(TAG, "switchFacingInternal request: " + facingMode + " (wantFront=" + wantFront + ")");

        if (ctrl instanceof com.childmonitor.capture.CameraXCaptureController) {
            ((com.childmonitor.capture.CameraXCaptureController) ctrl).switchFacing(
                    wantFront,
                    e -> {
                        if (e != null) {
                            Log.w(TAG, "CameraX switchFacing failed: " + e.getMessage(), e);
                            if (result != null) {
                                result.accept(
                                        null,
                                        e instanceof Exception ? (Exception) e : new Exception(e));
                            }
                        } else {
                            boolean actualFront = ((com.childmonitor.capture.CameraXCaptureController) ctrl)
                                    .isCurrentlyFrontFacing();
                            String actualFacing = actualFront ? "user" : "environment";
                            Log.i(TAG, "CameraX switchFacingInternal success actual=" + actualFacing);
                            if (result != null) {
                                result.accept(actualFacing, null);
                            }
                        }
                    });
            return;
        }
        com.facebook.react.bridge.WritableMap constraints = Arguments.createMap();
        constraints.putString("facingMode", wantFront ? "user" : "environment");
        constraints.putInt("width", ctrl.getWidth());
        constraints.putInt("height", ctrl.getHeight());
        constraints.putInt("frameRate", ctrl.getFrameRate());
        ctrl.applyConstraints(
                constraints,
                e -> {
                    if (e != null) {
                        if (result != null) {
                            result.accept(
                                    null,
                                    e instanceof Exception ? (Exception) e : new Exception(e));
                        }
                    } else if (result != null) {
                        result.accept(wantFront ? "user" : "environment", null);
                    }
                });
    }

    public static void stopActiveCapture(ReactApplicationContext reactContext) {
        String streamId = activeStreamId;
        activeStreamId = null;

        // Stop the CameraX capturer first so ProcessCameraProvider.unbindAll() runs
        // synchronously and the green camera privacy indicator is cleared immediately.
        // Do this even when streamId is already null — PC may still hold the track while
        // the controller reference remains (network-loss teardown race).
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

        if (streamId == null) {
            return;
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
                constraints.putInt("height", 960);
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
