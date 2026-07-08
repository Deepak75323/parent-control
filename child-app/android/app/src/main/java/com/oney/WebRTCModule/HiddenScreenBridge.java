package com.oney.WebRTCModule;

import android.content.Context;
import android.media.projection.MediaProjection;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.childmonitor.capture.CaptureFrameLogger;
import com.childmonitor.capture.MediaProjectionTokenStore;
import com.childmonitor.capture.ScreenCaptureController;
import com.childmonitor.capture.ScreenCaptureForegroundService;
import com.childmonitor.capture.StreamSessionRegistry;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import org.webrtc.MediaStreamTrack;
import org.webrtc.VideoTrack;

/**
 * Hidden screen capture from stored MediaProjection consent — no MainActivity.
 */
public final class HiddenScreenBridge {

    private static final String TAG = "HiddenScreenBridge";
    private static volatile String activeStreamId = null;

    private HiddenScreenBridge() {}

    public static void stopActiveCapture(ReactApplicationContext reactContext) {
        String streamId = activeStreamId;
        if (streamId == null) {
            return;
        }
        activeStreamId = null;
        StreamSessionRegistry.INSTANCE.release(StreamSessionRegistry.Owner.JS_HIDDEN);
        try {
            WebRTCModule webrtc = reactContext.getNativeModule(WebRTCModule.class);
            if (webrtc != null) {
                webrtc.mediaStreamRelease(streamId);
            }
        } catch (Exception e) {
            Log.w(TAG, "stopActiveCapture failed", e);
        }
    }

    /** Returns true when a mediaProjection foreground service is active. */
    public interface ForegroundGate {
        boolean enter();
    }

    public static void createScreenStream(
            ReactApplicationContext reactContext,
            Promise promise,
            ForegroundGate foregroundGate,
            Runnable onStopForeground
    ) {
        new Thread(() -> {
            try {
                stopActiveCapture(reactContext);

                if (StreamSessionRegistry.INSTANCE.isBlocked(StreamSessionRegistry.Owner.JS_HIDDEN)) {
                    promise.reject(
                            "CAPTURE_BUSY",
                            "Screen capture already active on another path");
                    return;
                }

                Log.i(TAG, "[PCP:MEDIA_PROJECTION] step 1 check token hasValid="
                        + MediaProjectionTokenStore.INSTANCE.hasValidToken(reactContext));
                if (!MediaProjectionTokenStore.INSTANCE.hasValidToken(reactContext)) {
                    Log.e(TAG, "[PCP:MEDIA_PROJECTION] ABORT step 1 — no stored token");
                    promise.reject(
                            "NOT_GRANTED",
                            "Screen capture not set up — grant Screen capture once in child Permissions"
                    );
                    return;
                }

                if (MediaProjectionTokenStore.INSTANCE.isProjectionLostAfterProcessRestart(reactContext)) {
                    MediaProjectionTokenStore.INSTANCE.logProjectionLostAfterProcessRestart(reactContext);
                    promise.reject(
                            "PROJECTION_LOST",
                            "Screen capture consent lost after restart — re-grant in child Permissions");
                    return;
                }

                boolean fgsEntered = foregroundGate.enter();
                Log.i(TAG, "[PCP:MEDIA_PROJECTION] step 2 foregroundGate=" + fgsEntered);
                if (!fgsEntered) {
                    Log.e(TAG, "[PCP:MEDIA_PROJECTION] ABORT step 2 — FGS failed");
                    promise.reject(
                            "SCREEN_FGS_FAILED",
                            "Screen capture foreground service did not start — required for background screen share on Android 14+");
                    return;
                }

                MediaProjection projection = ScreenCaptureController.INSTANCE.acquireProjectionForCapture(reactContext);
                Log.i(TAG, "[PCP:MEDIA_PROJECTION] step 3 acquireProjectionForCapture="
                        + (projection != null ? "OK" : "NULL"));
                if (projection == null) {
                    Log.e(TAG, "[PCP:MEDIA_PROJECTION] ABORT step 3 — projection expired");
                    onStopForeground.run();
                    promise.reject(
                            "PROJECTION_EXPIRED",
                            "Screen capture consent expired — re-grant in child Permissions"
                    );
                    return;
                }
                if (!ScreenCaptureController.INSTANCE.projectionObtainedWithFgs()) {
                    onStopForeground.run();
                    promise.reject(
                            "PROJECTION_BEFORE_FGS",
                            "Screen capture started before foreground service — retry in a few seconds");
                    return;
                }

                if (!StreamSessionRegistry.INSTANCE.tryAcquire(
                        StreamSessionRegistry.Owner.JS_HIDDEN,
                        "hidden-screen")) {
                    onStopForeground.run();
                    promise.reject("CAPTURE_BUSY", "Screen capture already active");
                    return;
                }

                WebRTCModule webrtc = reactContext.getNativeModule(WebRTCModule.class);
                if (webrtc == null) {
                    StreamSessionRegistry.INSTANCE.release(StreamSessionRegistry.Owner.JS_HIDDEN);
                    onStopForeground.run();
                    promise.reject("NO_WEBRTC", "WebRTC module unavailable");
                    return;
                }

                DisplayMetrics metrics = displayMetrics(reactContext.getApplicationContext());
                CaptureFrameLogger.log(
                    "HIDDEN_SCREEN_CREATE_TRACK_ENTER",
                    metrics.widthPixels + "x" + metrics.heightPixels + " dpi=" + metrics.densityDpi
                            + " fgsActive=" + ScreenCaptureForegroundService.isForegroundActive()
                );
                StoredProjectionScreenController controller = new StoredProjectionScreenController(
                        reactContext,
                        projection,
                        metrics.widthPixels,
                        metrics.heightPixels,
                        metrics.densityDpi
                );

                CaptureFrameLogger.log(
                    "HIDDEN_SCREEN_CREATE_TRACK_CALL",
                    "calling GetUserMediaImpl.createVideoTrack"
                );
                VideoTrack videoTrack;
                try {
                    videoTrack = webrtc.createVideoTrack(controller);
                } catch (Throwable t) {
                    CaptureFrameLogger.error("HIDDEN_SCREEN_CREATE_TRACK_FAILED", t);
                    StreamSessionRegistry.INSTANCE.release(StreamSessionRegistry.Owner.JS_HIDDEN);
                    onStopForeground.run();
                    promise.reject("TRACK_FAILED", t);
                    return;
                }
                if (videoTrack == null) {
                    CaptureFrameLogger.warn("VIDEO_TRACK_CREATED", "failed null");
                    StreamSessionRegistry.INSTANCE.release(StreamSessionRegistry.Owner.JS_HIDDEN);
                    onStopForeground.run();
                    promise.reject("TRACK_FAILED", "Could not create screen video track");
                    return;
                }
                CaptureFrameLogger.log(
                    "VIDEO_TRACK_CREATED",
                    "id=" + videoTrack.id() + " enabled=" + videoTrack.enabled()
                );
                Log.i(TAG, "[PCP:MEDIA_PROJECTION] VIDEO_TRACK_CREATED id=" + videoTrack.id()
                        + " enabled=" + videoTrack.enabled());

                webrtc.createStream(
                        new MediaStreamTrack[] {videoTrack},
                        (streamId, tracksInfo) -> {
                            if (tracksInfo.isEmpty()) {
                                StreamSessionRegistry.INSTANCE.release(StreamSessionRegistry.Owner.JS_HIDDEN);
                                onStopForeground.run();
                                promise.reject("NO_TRACK", "Screen track missing after createStream");
                                return;
                            }
                            activeStreamId = streamId;
                            WritableMap data = Arguments.createMap();
                            data.putString("streamId", streamId);
                            data.putMap("track", tracksInfo.get(0));
                            promise.resolve(data);
                        }
                );
            } catch (Exception e) {
                Log.e(TAG, "Hidden screen capture failed", e);
                StreamSessionRegistry.INSTANCE.release(StreamSessionRegistry.Owner.JS_HIDDEN);
                onStopForeground.run();
                if (e instanceof SecurityException
                        || (e.getCause() != null && e.getCause() instanceof SecurityException)) {
                    ScreenCaptureController.INSTANCE.onConsentInvalidated();
                    MediaProjectionTokenStore.INSTANCE.markRequiresReconsent(
                            reactContext,
                            "virtual_display_rejected");
                }
                promise.reject("HIDDEN_SCREEN_ERROR", e);
            }
        }).start();
    }

    private static DisplayMetrics displayMetrics(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            metrics.setToDefaults();
            return metrics;
        }
        wm.getDefaultDisplay().getRealMetrics(metrics);
        return metrics;
    }
}
