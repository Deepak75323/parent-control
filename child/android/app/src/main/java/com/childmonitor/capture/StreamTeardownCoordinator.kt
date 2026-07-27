package com.childmonitor.capture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.childmonitor.StreamingHeadlessTaskService
import com.childmonitor.native.ChildMonitorNativeModule
import com.facebook.react.bridge.ReactApplicationContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Ordered stream teardown: request JS cleanup first, then release capturers, then stop typed FGS.
 *
 * Prevents Android from revoking camera/microphone/mediaProjection while WebRTC tracks are
 * still active (P0-3).
 */
object StreamTeardownCoordinator {

    private const val TAG = "StreamTeardownCoord"
    private const val WATCHDOG_MS = 30_000L

    enum class State {
        IDLE,
        JS_PENDING,
        NATIVE_FORCED,
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val state = AtomicReference(State.IDLE)
    private val pendingReason = AtomicReference<String?>(null)
    private val watchdogRunnable = AtomicReference<Runnable?>(null)

    @JvmStatic
    fun currentState(): State = state.get()

    @JvmStatic
    fun isInProgress(): Boolean = state.get() != State.IDLE

    @JvmStatic
    fun requestStopAll(context: Context, reason: String) {
        val appContext = context.applicationContext
        if (state.compareAndSet(State.IDLE, State.JS_PENDING)) {
            pendingReason.set(reason)
            StreamLifecycleLogger.warn("TEARDOWN_REQUESTED", reason)
            Log.i(TAG, "requestStopAll reason=$reason")
        } else if (state.get() == State.JS_PENDING) {
            StreamLifecycleLogger.log("TEARDOWN_COALESCED", reason)
            Log.i(TAG, "requestStopAll coalesced — JS teardown already pending reason=$reason")
            return
        } else {
            StreamLifecycleLogger.log("TEARDOWN_IGNORED", "state=${state.get()} reason=$reason")
            return
        }

        val jsWillTeardown = emitJsStop()
        if (jsWillTeardown) {
            scheduleWatchdog(appContext)
            return
        }

        state.set(State.IDLE)
        pendingReason.set(null)
        finishNativeTeardown(appContext, reason, releaseCaptures = true, label = "native-only")
    }

    /**
     * Immediate native teardown without waiting for JS — use when replacing an existing
     * publisher. Prefer this over [requestStopAll] so a 30s JS watchdog cannot kill the
     * replacement stream. Does not stop [ScreenCaptureForegroundService] unless a publisher
     * or registry owner is active (warm FGS from ensureConsent must stay up).
     */
    @JvmStatic
    fun requestImmediateStopAll(context: Context, reason: String) {
        val appContext = context.applicationContext
        abortPendingJsTeardown("immediate:$reason")
        StreamLifecycleLogger.warn("TEARDOWN_IMMEDIATE", reason)
        Log.i(TAG, "requestImmediateStopAll reason=$reason")
        // Tell JS to close the PeerConnection / WebRTC tracks first. Native-only
        // release leaves the RN ADM AudioRecord running → green mic privacy indicator.
        emitJsStop()
        finishNativeTeardown(appContext, reason, releaseCaptures = true, label = "immediate")
        state.set(State.IDLE)
        pendingReason.set(null)
    }

    /**
     * Like [finishNativeTeardown] but for handoff: stop publisher / hidden audio+camera without
     * tearing down a warm screen FGS that still holds a live MediaProjection binder.
     *
     * Must NOT call [HiddenScreenCapture.stop] — that path invokes
     * [ScreenCaptureForegroundService.stopStream] and revokes MediaProjection on API 34+,
     * which surfaces on the parent as a false "ICE/TURN reconnect failed" dialog.
     */
    @JvmStatic
    fun stopPublisherPreservingWarmScreenFgs(context: Context, reason: String) {
        abortPendingJsTeardown("preserve-fgs:$reason")
        val appContext = context.applicationContext
        StreamLifecycleLogger.warn("TEARDOWN_PRESERVE_FGS", reason)
        Log.i(TAG, "stopPublisherPreservingWarmScreenFgs reason=$reason")
        val reactContext = ChildMonitorNativeModule.getSharedReactApplicationContext()
        if (reactContext != null) {
            try {
                HiddenAudioCapture.stopCaptureOnly(reactContext)
                HiddenCameraCapture.stopCaptureOnly(reactContext)
            } catch (e: Exception) {
                Log.w(TAG, "Hidden audio/camera stop failed during preserve-fgs", e)
            }
        }
        NativeStreamPublisher.stop(appContext)
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.JS_HIDDEN)
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)
        StreamingHeadlessTaskService.stop(appContext)
        state.set(State.IDLE)
        pendingReason.set(null)
        StreamLifecycleLogger.log("TEARDOWN_COMPLETE", "preserve-fgs reason=$reason")
    }

    /**
     * Cancel a pending JS teardown watchdog without releasing captures. Call after a native
     * (re)start so a stale STOP from the handoff cannot destroy the newly started pipeline.
     */
    @JvmStatic
    fun abortPendingJsTeardown(reason: String): Boolean {
        if (state.get() != State.JS_PENDING) {
            return false
        }
        cancelWatchdog()
        state.set(State.IDLE)
        pendingReason.set(null)
        StreamLifecycleLogger.log("TEARDOWN_ABORTED", reason)
        Log.i(TAG, "abortPendingJsTeardown reason=$reason")
        return true
    }

  @JvmStatic
    fun requestStopKind(context: Context, kind: StreamKind, reason: String) {
        val appContext = context.applicationContext
        Log.i(TAG, "requestStopKind kind=$kind reason=$reason")

        if (ChildMonitorNativeModule.canDeliverStreamToJs()) {
            if (state.compareAndSet(State.IDLE, State.JS_PENDING)) {
                pendingReason.set("$reason:$kind")
                emitJsStop()
                scheduleWatchdog(appContext)
                return
            }
            if (state.get() == State.JS_PENDING) {
                StreamLifecycleLogger.log("TEARDOWN_KIND_DEFERRED", "kind=$kind reason=$reason")
                return
            }
        }

        finishKindTeardown(appContext, kind, reason, releaseCaptures = true)
    }

  @JvmStatic
    fun onJsTeardownComplete(context: Context, detail: String?) {
        if (state.get() != State.JS_PENDING) {
            StreamLifecycleLogger.log(
                "TEARDOWN_JS_COMPLETE_IGNORED",
                "state=${state.get()} detail=$detail",
            )
            return
        }
        // After "new native screen stream" handoff, React may ACK STOP late — do not kill
        // the publisher that replaced the previous session.
        if (isScreenPipelineActive() && isHandoffTeardownReason(pendingReason.get())) {
            abortPendingJsTeardown("js-complete-during-handoff:${pendingReason.get()}")
            return
        }
        cancelWatchdog()
        val reason = pendingReason.getAndSet(null) ?: detail ?: "js-complete"
        state.set(State.IDLE)
        StreamLifecycleLogger.log("TEARDOWN_JS_COMPLETE", reason)
        Log.i(TAG, "onJsTeardownComplete reason=$reason detail=$detail")
        finishNativeTeardown(
            context.applicationContext,
            reason,
            releaseCaptures = false,
            label = "js-complete",
        )
    }

    private fun scheduleWatchdog(context: Context) {
        cancelWatchdog()
        val runnable = Runnable {
            if (state.get() != State.JS_PENDING) return@Runnable
            // Handoff race: "new native screen stream" armed this watchdog, then the new
            // publisher connected. Do not destroy the live pipeline.
            if (isScreenPipelineActive() && isHandoffTeardownReason(pendingReason.get())) {
                StreamLifecycleLogger.warn(
                    "TEARDOWN_WATCHDOG_SKIPPED",
                    "native pipeline active reason=${pendingReason.get()}",
                )
                Log.w(
                    TAG,
                    "JS teardown watchdog skipped — native screen still active reason=${pendingReason.get()}",
                )
                state.set(State.IDLE)
                pendingReason.set(null)
                return@Runnable
            }
            // Also skip when a newer session asked to abort but race left us JS_PENDING —
            // parent-stop watchdogs must not kill capturers during an active start.
            if (StreamSessionRegistry.activeOwner() != StreamSessionRegistry.Owner.NONE ||
                ScreenCaptureForegroundService.isForegroundActive() ||
                NativeStreamPublisher.isRunning()
            ) {
                StreamLifecycleLogger.warn(
                    "TEARDOWN_WATCHDOG_SKIPPED",
                    "screen session active reason=${pendingReason.get()}",
                )
                state.set(State.IDLE)
                pendingReason.set(null)
                return@Runnable
            }
            val reason = pendingReason.getAndSet(null) ?: "watchdog-timeout"
            state.set(State.NATIVE_FORCED)
            StreamLifecycleLogger.warn("TEARDOWN_WATCHDOG", reason)
            Log.w(TAG, "JS teardown watchdog fired after ${WATCHDOG_MS}ms reason=$reason")
            finishNativeTeardown(context, reason, releaseCaptures = true, label = "watchdog")
            state.set(State.IDLE)
        }
        watchdogRunnable.set(runnable)
        mainHandler.postDelayed(runnable, WATCHDOG_MS)
    }

    private fun isHandoffTeardownReason(reason: String?): Boolean {
        if (reason.isNullOrBlank()) return false
        return reason.contains("new native", ignoreCase = true) ||
            reason.contains("starting headless", ignoreCase = true) ||
            reason.contains("handoff", ignoreCase = true)
    }

    private fun cancelWatchdog() {
        watchdogRunnable.getAndSet(null)?.let { mainHandler.removeCallbacks(it) }
    }

    private fun emitJsStop(): Boolean {
        if (!ChildMonitorNativeModule.canDeliverStreamToJs()) {
            StreamLifecycleLogger.log(
                "STREAM_JS_STOP_SKIPPED",
                "React instance inactive — native/FGS teardown only",
            )
            return false
        }
        val delivered = ChildMonitorNativeModule.emitStreamControl("STOP")
        StreamLifecycleLogger.log(
            "STREAM_JS_STOP_EMITTED",
            "delivered=$delivered fgsStop=deferred",
        )
        return delivered
    }

    private fun finishNativeTeardown(
        context: Context,
        reason: String,
        releaseCaptures: Boolean,
        label: String,
    ) {
        StreamLifecycleLogger.warn("TEARDOWN_NATIVE_FINISH", "$label reason=$reason")
        Log.i(TAG, "finishNativeTeardown label=$label releaseCaptures=$releaseCaptures reason=$reason")

        val reactContext = ChildMonitorNativeModule.getSharedReactApplicationContext()
        if (releaseCaptures) {
            releaseHiddenCaptures(reactContext)
            forceStopWebRtcMicrophone(reactContext)
        }

        if (isScreenPipelineActive()) {
            ScreenCaptureForegroundService.stopStream(context)
            NativeStreamPublisher.stop(context)
            ScreenCaptureController.releaseIfIdle(context, "teardown:$reason")
        }

        stopTypedForegroundServicesIfActive(context, releaseCaptures)

        StreamingHeadlessTaskService.stop(context)
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.JS_HIDDEN)
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)

        StreamLifecycleLogger.log("TEARDOWN_COMPLETE", "$label reason=$reason")
    }

    private fun finishKindTeardown(
        context: Context,
        kind: StreamKind,
        reason: String,
        releaseCaptures: Boolean,
    ) {
        val reactContext = ChildMonitorNativeModule.getSharedReactApplicationContext()
        when (kind) {
            StreamKind.AUDIO -> {
                if (releaseCaptures) {
                    reactContext?.let { HiddenAudioCapture.stopCaptureOnly(it) }
                }
                AudioStreamForegroundService.stopSafely(context, reason)
            }
            StreamKind.CAMERA -> {
                if (releaseCaptures) {
                    reactContext?.let { HiddenCameraCapture.stopCaptureOnly(it) }
                }
                CameraStreamForegroundService.stopSafely(context, reason)
            }
            StreamKind.SCREEN -> {
                if (releaseCaptures) {
                    reactContext?.let { HiddenScreenCapture.stop(it) }
                }
                ScreenCaptureForegroundService.stopStream(context)
                NativeStreamPublisher.stop(context)
                ScreenCaptureController.releaseIfIdle(context, "stopKind:$reason")
                StreamSessionRegistry.release(StreamSessionRegistry.Owner.JS_HIDDEN)
                StreamSessionRegistry.release(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)
            }
        }
    }

    private fun stopTypedForegroundServicesIfActive(context: Context, capturesAlreadyReleased: Boolean) {
        if (AudioStreamForegroundService.isActive()) {
            if (!capturesAlreadyReleased) {
                ChildMonitorNativeModule.getSharedReactApplicationContext()?.let {
                    HiddenAudioCapture.stopCaptureOnly(it)
                }
            }
            AudioStreamForegroundService.stopSafely(context, "teardown-finalize")
        }
        if (CameraStreamForegroundService.isActive()) {
            if (!capturesAlreadyReleased) {
                ChildMonitorNativeModule.getSharedReactApplicationContext()?.let {
                    HiddenCameraCapture.stopCaptureOnly(it)
                }
            }
            CameraStreamForegroundService.stopSafely(context, "teardown-finalize")
        }
    }

    private fun releaseHiddenCaptures(reactContext: ReactApplicationContext?) {
        if (reactContext == null) {
            StreamLifecycleLogger.log(
                "STREAM_BRIDGE_STOP_SKIPPED",
                "no ReactApplicationContext — FGS stop only",
            )
            return
        }
        try {
            HiddenAudioCapture.stopCaptureOnly(reactContext)
            HiddenCameraCapture.stopCaptureOnly(reactContext)
            if (isScreenPipelineActive()) {
                HiddenScreenCapture.stop(reactContext)
            }
            StreamLifecycleLogger.log("STREAM_BRIDGE_STOP", "hidden captures released (capture-only)")
        } catch (e: Exception) {
            Log.w(TAG, "Hidden capture bridge stop failed", e)
            StreamLifecycleLogger.warn("STREAM_BRIDGE_STOP_FAILED", e.message)
        }
    }

    /**
     * JS PeerConnection / ADM AudioRecord can outlive HiddenAudioBridge.activeStreamId.
     * Mute + best-effort stopRecording so the green mic privacy indicator clears on network loss.
     */
    private fun forceStopWebRtcMicrophone(reactContext: ReactApplicationContext?) {
        try {
            val options = com.oney.WebRTCModule.WebRTCModuleOptions.getInstance()
            val adm = options.audioDeviceModule
            adm?.setMicrophoneMute(true)
            if (adm != null) {
                try {
                    val stop = adm.javaClass.methods.firstOrNull {
                        it.name == "stopRecording" && it.parameterCount == 0
                    }
                    stop?.invoke(adm)
                    StreamLifecycleLogger.log("ADM_STOP_RECORDING", "invoked=${stop != null}")
                } catch (e: Exception) {
                    Log.w(TAG, "ADM stopRecording invoke failed", e)
                }
            }
            if (reactContext != null) {
                // HiddenAudioBridge no-ops when activeStreamId is already null — call anyway.
                HiddenAudioCapture.stopCaptureOnly(reactContext)
            }
            PlaybackCaptureInjector.setActive(false)
            DevicePlaybackCaptureSession.stop()
        } catch (e: Exception) {
            Log.w(TAG, "forceStopWebRtcMicrophone failed", e)
            StreamLifecycleLogger.warn("ADM_FORCE_STOP_FAILED", e.message)
        }
    }

    private fun isScreenPipelineActive(): Boolean =
        StreamSessionRegistry.activeOwner() != StreamSessionRegistry.Owner.NONE ||
            ScreenCaptureForegroundService.isForegroundActive() ||
            NativeStreamPublisher.isRunning()

    /** Test hook — reset coordinator between instrumented tests. */
    @JvmStatic
    fun resetForTests() {
        cancelWatchdog()
        state.set(State.IDLE)
        pendingReason.set(null)
    }
}
