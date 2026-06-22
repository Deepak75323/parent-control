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
