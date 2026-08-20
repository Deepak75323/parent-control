package com.childmonitor.capture

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.debug.StreamState
import com.childmonitor.sync.DeviceHttpClient
import com.childmonitor.sync.NativeStompOwner
import com.childmonitor.telemetry.StreamLifecycleTelemetry
import org.json.JSONObject
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Native WebRTC screen publisher using stored MediaProjection token (headless-only path).
 * JS hidden capture owns the foreground path when React is active.
 */
object NativeStreamPublisher {

    private const val TAG = "NativeStreamPublisher"
    private const val FIRST_FRAME_TIMEOUT_MS = 8_000L
    private val running = AtomicBoolean(false)
    private val webRtcGlobalInitDone = AtomicBoolean(false)
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var eglBase: EglBase? = null
    private var capturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioDeviceModule: org.webrtc.audio.AudioDeviceModule? = null
    private var pollHandler: Handler? = null
    private var pollThread: HandlerThread? = null
    private var pollRunnable: Runnable? = null
    private var appContext: Context? = null
    private var activeRoomId: String? = null
    private var activeSessionId: String? = null
    private val seenMessageIds = mutableSetOf<String>()
    private var remoteAnswerApplied = false
    private val publisherGeneration = AtomicLong(0)

    private var iceRecoveryRunnable: Runnable? = null
    private var iceRecoveryStage = 0

    fun start(context: Context, roomId: String, sessionId: String): Boolean {
        if (!ScreenCaptureController.canUseSilentCapture(context)) {
            Log.w(TAG, "Silent capture not available")
            return false
        }
        if (StreamSessionRegistry.isBlocked(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)) {
            Log.w(TAG, "Screen capture owned by ${StreamSessionRegistry.activeOwner()} — skip native start")
            return false
        }
        if (!StreamSessionRegistry.tryAcquire(StreamSessionRegistry.Owner.NATIVE_PUBLISHER, sessionId, roomId)) {
            return false
        }

        if (running.getAndSet(true)) {
            stopCaptureOnly()
        }

        appContext = context.applicationContext
        activeRoomId = roomId
        activeSessionId = sessionId
        seenMessageIds.clear()
        remoteAnswerApplied = false
        val generation = publisherGeneration.incrementAndGet()

        return try {
            PcpFlowLogger.transitionState(StreamState.CAPTURE_STARTING, "NativeStreamPublisher.start",
                "room=$roomId session=$sessionId")
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "native capture start",
                "room=$roomId session=$sessionId",
            )
            if (MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(context)) {
                MediaProjectionTokenStore.logProjectionLostAfterProcessRestart(context)
                throw IllegalStateException("Projection unavailable after process restart")
            }
            if (!ScreenCaptureForegroundService.startAndWait(context)) {
                throw IllegalStateException("Screen capture FGS did not start")
            }
            val projection = ScreenCaptureController.acquireProjectionForCapture(context)
                ?: throw IllegalStateException("MediaProjection unavailable — re-grant screen capture")
            PcpFlowLogger.transitionState(StreamState.PERMISSION_VERIFIED, "NativeStreamPublisher.start",
                "projection acquired session=$sessionId")
            if (!ScreenCaptureController.projectionObtainedWithFgs()) {
                throw IllegalStateException("MediaProjection obtained before screen FGS was active")
            }

            initWebRtc(context)
            val factory = requirePeerConnectionFactory()
            val pc = requirePeerConnection()
            val egl = requireEglBase()
            val metrics = context.resources.displayMetrics
            val screenCapturer = ReuseProjectionCapturer(
                projection,
                metrics.densityDpi,
                onProjectionLost = {
                    // MediaProjection.onStop fired mid-stream — OS revoked the token.
                    // Stop the publisher so the session registry is released and the
                    // coordinator can attempt recovery on the next STOMP command.
                    PcpFlowLogger.warn(
                        PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                        "native publisher: projection lost mid-stream — stopping",
                        "room=$activeRoomId session=$activeSessionId",
                    )
                    stop(context)
                    ScreenCaptureForegroundService.stopStream(context)
                },
            )
            capturer = screenCapturer
            val helper = SurfaceTextureHelper.create("ScreenCaptureThread", egl.eglBaseContext)
            val source = factory.createVideoSource(screenCapturer.isScreencast)
            videoSource = source
            screenCapturer.initialize(helper, context, source.capturerObserver)
            ReuseProjectionCapturer.resetFirstFrameFlag()
            val width = metrics.widthPixels.coerceAtLeast(360)
            val height = metrics.heightPixels.coerceAtLeast(640)
            screenCapturer.startCapture(width, height, 15)

            if (!awaitFirstCaptureFrame(FIRST_FRAME_TIMEOUT_MS)) {
                throw IllegalStateException("No screen capture frames within ${FIRST_FRAME_TIMEOUT_MS}ms")
            }
            PcpFlowLogger.transitionState(StreamState.CAPTURE_RUNNING, "NativeStreamPublisher.start",
                "firstFrame session=$sessionId")

            val videoTrack = factory.createVideoTrack("screen_video", source)
            pc.addTrack(videoTrack, listOf("stream"))

            // System / device audio via AudioPlaybackCapture on the same MediaProjection.
            try {
                if (DevicePlaybackAudioCapturer.start(projection)) {
                    val audioSource = factory.createAudioSource(MediaConstraints())
                    val audioTrack = factory.createAudioTrack("screen_audio", audioSource)
                    pc.addTrack(audioTrack, listOf("stream"))
                    (audioDeviceModule as? JavaAudioDeviceModule)?.setMicrophoneMute(true)
                    Log.i(TAG, "PLAYBACK_CAPTURE_STARTED native screen audio track added")
                }
            } catch (e: Exception) {
                Log.w(TAG, "PLAYBACK_CAPTURE_FAILED native path reason=${e.message}")
            }

            PcpFlowLogger.transitionState(StreamState.WEBRTC_CONNECTING, "NativeStreamPublisher.start",
                "peerConnection ready session=$sessionId")

            createAndSendOffer(context, roomId, sessionId, generation)
            // STOMP push + HTTP poll (must run off main — NetworkOnMainThreadException otherwise).
            NativeStompOwner.subscribeSignalingRoom(roomId)
            startSignalingPoll(context, roomId)
            true
        } catch (e: Exception) {
            PcpFlowLogger.transitionState(StreamState.FAILED, "NativeStreamPublisher.start",
                e.message ?: "unknown")
            Log.e(TAG, "start failed — stored consent kept for retry", e)
            running.set(false)
            StreamSessionRegistry.release(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)
            // Report the capture/setup failure so the parent UI shows a clear error
            // instead of hanging on the offer-wait timeout (~60s).
            reportFailureAsync(context, sessionId, classifyStartFailure(e))
            stop(context)
            false
        }
    }

    private fun awaitFirstCaptureFrame(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (CaptureFrameLogger.hasReceivedFrame()) {
                return true
            }
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return CaptureFrameLogger.hasReceivedFrame()
    }

    private fun classifyStartFailure(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("process restart", ignoreCase = true) -> "PROJECTION_LOST_AFTER_RESTART"
            msg.contains("re-grant", ignoreCase = true) -> "PROJECTION_REQUIRES_RECONSENT"
            msg.contains("FGS", ignoreCase = true) -> "FGS_NOT_READY"
            msg.contains("MediaProjection", ignoreCase = true) -> "NO_SCREEN_CAPTURE_CONSENT"
            msg.contains("No screen capture frames", ignoreCase = true) -> "NO_CAPTURE_FRAMES"
            else -> "NATIVE_PUBLISHER_START_FAILED"
        }
    }

    private fun reportFailureAsync(context: Context, sessionId: String, reason: String) {
        Thread {
            try {
                DeviceHttpClient.reportStreamFailure(context, sessionId, reason)
            } catch (e: Exception) {
                Log.w(TAG, "reportStreamFailure failed", e)
            }
        }.start()
    }

    fun stop(context: Context) {
        publisherGeneration.incrementAndGet()
        running.set(false)
        DevicePlaybackAudioCapturer.stop()
        try {
            (audioDeviceModule as? JavaAudioDeviceModule)?.setMicrophoneMute(false)
        } catch (_: Exception) {
        }
        val room = activeRoomId
        stopSignalingPoll()
        iceRecoveryStage = 0
        if (!room.isNullOrBlank()) {
            NativeStompOwner.unsubscribeSignalingRoom(room)
        }
        activeRoomId = null
        activeSessionId = null
        appContext = null
        seenMessageIds.clear()
        remoteAnswerApplied = false
        StreamSessionRegistry.release(StreamSessionRegistry.Owner.NATIVE_PUBLISHER)

        stopCaptureOnly()

        peerConnection?.close()
        peerConnection = null

        // Keep the warm MediaProjection session for the next parent request — releasing it
        // forces getMediaProjection on API 34+ which needs project_media we no longer hold.
    }
    fun onSignalingPush(roomId: String, body: String) {
        if (!running.get() || roomId != activeRoomId) return
        val ctx = appContext ?: return
        try {
            val msg = JSONObject(body)
            handleParentSignalingMessage(ctx, msg)
        } catch (e: Exception) {
            Log.w(TAG, "onSignalingPush parse failed", e)
        }
    }

    private fun stopCaptureOnly() {
        try {
            capturer?.stopCapture()
        } catch (_: Exception) {
        }
        try {
            capturer?.dispose()
        } catch (_: Exception) {
        }
        capturer = null
        try {
            videoSource?.dispose()
        } catch (_: Exception) {
        }
        videoSource = null
        // Must match ReuseProjectionCapturer.GATE_OWNER — previous "native-publisher-stop"
        // never released the gate and caused CAPTURE_GATE_BLOCKED on the next stream.
        MediaProjectionCaptureGate.release(ReuseProjectionCapturer.GATE_OWNER)
        MediaProjectionCaptureGate.forceRelease("native-publisher-stopCaptureOnly")
    }

    fun isRunning(): Boolean = running.get()

    internal fun isActiveGenerationForTesting(
        generation: Long,
        expectedSessionId: String?,
        runningValue: Boolean,
        activeSessionIdValue: String?,
        currentGeneration: Long,
    ): Boolean {
        if (!runningValue) return false
        if (generation != currentGeneration) return false
        if (expectedSessionId.isNullOrBlank()) return true
        return expectedSessionId == activeSessionIdValue
    }

    private fun isActiveGeneration(generation: Long, expectedSessionId: String? = null): Boolean =
        isActiveGenerationForTesting(
            generation = generation,
            expectedSessionId = expectedSessionId,
            runningValue = running.get(),
            activeSessionIdValue = activeSessionId,
            currentGeneration = publisherGeneration.get(),
        )

    private fun requireEglBase(): EglBase =
        eglBase ?: throw IllegalStateException("WebRTC EGL context not initialized")

    private fun requirePeerConnectionFactory(): PeerConnectionFactory =
        peerConnectionFactory ?: throw IllegalStateException("PeerConnectionFactory not initialized")

    private fun requirePeerConnection(): PeerConnection =
        peerConnection ?: throw IllegalStateException("PeerConnection not initialized")

    private fun initWebRtc(context: Context) {
        if (peerConnectionFactory == null) {
            val egl = EglBase.create()
            eglBase = egl
            if (webRtcGlobalInitDone.compareAndSet(false, true)) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions(),
                )
            }
            val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
            val adm = JavaAudioDeviceModule.builder(context)
                .setEnableVolumeLogger(false)
                .setAudioRecordDataCallback { _, channelCount, sampleRate, audioBuffer ->
                    PlaybackCaptureInjector.fillWebRtcBuffer(channelCount, sampleRate, audioBuffer)
                }
                .createAudioDeviceModule()
            audioDeviceModule = adm
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
        }

        peerConnection?.close()
        peerConnection = null

        // P2-4: pass sessionId so we receive session-bound TURN credentials (short TTL,
        // limited to this stream session).
        val iceServers = WebrtcConfigLoader.loadIceServers(context, activeSessionId)
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }
        val factory = requirePeerConnectionFactory()
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val room = activeRoomId ?: return
                val session = activeSessionId ?: return
                val candType = when {
                    candidate.sdp.contains("typ relay") -> "relay"
                    candidate.sdp.contains("typ srflx") -> "srflx"
                    candidate.sdp.contains("typ host") -> "host"
                    else -> "unknown"
                }
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.WEBRTC_FLOW,
                    "onIceCandidate",
                    "Local ICE candidate generated type=$candType",
                    candidate.sdp.take(80),
                )
                SignalingHttpClient.postMessage(
                    context,
                    room,
                    "ice",
                    JSONObject()
                        .put("candidate", candidate.sdp)
                        .put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex)
                        .toString(),
                    session,
                )
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onIceCandidatesRemoved", "ICE candidates removed", "count=${candidates?.size ?: 0}")
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onSignalingChange", "Signaling state changed", "state=$state")
                Log.i(TAG, "signalingState=$state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onIceConnectionChange", "ICE connection state changed", "state=$state room=$activeRoomId")
                Log.i(TAG, "iceConnectionState=$state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        iceRecoveryStage = 0
                        iceRecoveryRunnable?.let { pollHandler?.removeCallbacks(it) }
                        iceRecoveryRunnable = null
                        StreamSessionRegistry.markActive()
                        PcpFlowLogger.transitionState(StreamState.CONNECTED, "onIceConnectionChange",
                            "state=$state room=$activeRoomId")
                        PcpFlowLogger.log(
                            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                            "ICE connected",
                            "state=$state room=$activeRoomId",
                        )
                        StreamLifecycleTelemetry.record("ice_recovered", mapOf("state" to state?.name))
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> scheduleIceRecovery(context)
                    else -> Unit
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onIceConnectionReceivingChange", "ICE receiving changed", "receiving=$receiving")
            }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onIceGatheringChange", "ICE gathering state changed", "state=$state")
                Log.i(TAG, "iceGatheringState=$state")
            }
            override fun onAddStream(stream: org.webrtc.MediaStream?) {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onAddStream", "Remote stream added", "streamId=${stream?.id}")
            }
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onRemoveStream", "Remote stream removed", "streamId=${stream?.id}")
            }
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onDataChannel", "Data channel event", "label=${channel?.label()}")
            }
            override fun onRenegotiationNeeded() {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onRenegotiationNeeded", "Renegotiation needed")
            }
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {
                PcpFlowLogger.log(PcpFlowLogger.Category.WEBRTC_FLOW, "onAddTrack", "Track added", "streams=${streams?.size ?: 0}")
            }
        })
    }

    private fun createAndSendOffer(context: Context, roomId: String, sessionId: String, generation: Long) {
        PcpFlowLogger.log(
            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
            "createOffer start",
            "room=$roomId session=$sessionId",
        )
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        val pc = peerConnection
        if (pc == null) {
            Log.e(TAG, "createOffer skipped — PeerConnection not initialized")
            reportFailureAsync(context, sessionId, "NATIVE_PUBLISHER_START_FAILED")
            return
        }
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                if (!isActiveGeneration(generation, sessionId)) {
                    Log.w(TAG, "createOffer success ignored for stale session=$sessionId generation=$generation")
                    return
                }
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "createOffer success",
                    "room=$roomId session=$sessionId",
                )
                pc.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        if (!isActiveGeneration(generation, sessionId)) {
                            Log.w(TAG, "setLocalDescription ignored for stale session=$sessionId generation=$generation")
                            return
                        }
                        val payload = JSONObject()
                            .put("type", desc.type.canonicalForm())
                            .put("sdp", desc.description)
                            .toString()
                        SignalingHttpClient.postMessage(
                            context,
                            roomId,
                            "offer",
                            payload,
                            sessionId,
                        )
                        PcpFlowLogger.transitionState(StreamState.LOCAL_DESCRIPTION_SET, "createAndSendOffer",
                            "room=$roomId session=$sessionId")
                        PcpFlowLogger.log(
                            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                            "offer sent",
                            "room=$roomId session=$sessionId",
                        )
                        Log.i(TAG, "offer sent room=$roomId")
                    }
                }, desc)
            }
        }, constraints)
    }

    private fun startSignalingPoll(context: Context, roomId: String) {
        stopSignalingPoll()
        val appCtx = context.applicationContext
        val handler = ensureBackgroundPollHandler()
        pollRunnable = object : Runnable {
            override fun run() {
                if (!running.get()) return
                try {
                    processSignalingMessages(appCtx, roomId)
                } catch (e: Exception) {
                    Log.w(TAG, "signaling poll failed", e)
                }
                if (running.get()) {
                    pollHandler?.postDelayed(this, 1500)
                }
            }
        }
        val runnable = pollRunnable ?: return
        handler.post(runnable)
    }

    private fun ensureBackgroundPollHandler(): Handler {
        pollHandler?.let { return it }
        val thread = HandlerThread("NativeSignalingPoll").also { it.start() }
        pollThread = thread
        return Handler(thread.looper).also { pollHandler = it }
    }

    private fun stopSignalingPoll() {
        iceRecoveryRunnable?.let { pollHandler?.removeCallbacks(it) }
        iceRecoveryRunnable = null
        pollRunnable?.let { pollHandler?.removeCallbacks(it) }
        pollRunnable = null
        pollHandler = null
        pollThread?.quitSafely()
        pollThread = null
    }

    private fun processSignalingMessages(context: Context, roomId: String) {
        val messages = SignalingHttpClient.fetchRecent(context, roomId) ?: return
        // Answers first, then ICE (ICE needs remote description).
        for (i in 0 until messages.length()) {
            val msg = messages.optJSONObject(i) ?: continue
            if (msg.optString("messageType") != "answer") continue
            handleParentSignalingMessage(context, msg)
        }
        if (!remoteAnswerApplied) return
        for (i in 0 until messages.length()) {
            val msg = messages.optJSONObject(i) ?: continue
            if (msg.optString("messageType") != "ice") continue
            handleParentSignalingMessage(context, msg)
        }
    }

    private fun handleParentSignalingMessage(context: Context, msg: JSONObject) {
        val id = msg.optString("id", "${msg.optString("messageType")}-${msg.optString("payload").hashCode()}")
        if (seenMessageIds.contains(id) || msg.optString("senderRole") == "CHILD") return
        val sessionId = activeSessionId
        when (msg.optString("messageType")) {
            "answer" -> applyAnswer(context, msg.optString("payload"), id, sessionId, publisherGeneration.get())
            "ice" -> {
                if (!remoteAnswerApplied) return
                applyIce(msg.optString("payload"), id, sessionId, publisherGeneration.get())
            }
        }
    }

    private fun applyAnswer(
        context: Context,
        payload: String,
        messageId: String,
        sessionId: String?,
        generation: Long,
    ) {
        val sdp = extractSdp(payload)
        val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                if (!isActiveGeneration(generation, sessionId)) {
                    Log.w(TAG, "remote answer ignored for stale session=$sessionId generation=$generation")
                    return
                }
                seenMessageIds.add(messageId)
                remoteAnswerApplied = true
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                    "answer received",
                    "room=$activeRoomId",
                )
                Log.i(TAG, "remote answer applied")
            }
            override fun onSetFailure(error: String?) {
                if (!isActiveGeneration(generation, sessionId)) {
                    Log.w(TAG, "remote answer failure ignored for stale session=$sessionId generation=$generation")
                    return
                }
                Log.e(TAG, "remote answer failed: $error")
                val session = activeSessionId
                if (session != null) {
                    reportFailureAsync(context, session, "ANSWER_APPLY_FAILED")
                }
            }
        }, desc)
    }

    private fun applyIce(payload: String, messageId: String, sessionId: String?, generation: Long) {
        if (!isActiveGeneration(generation, sessionId)) {
            Log.w(TAG, "ICE candidate ignored for stale session=$sessionId generation=$generation")
            return
        }
        try {
            val json = JSONObject(payload)
            val candidate = IceCandidate(
                json.optString("sdpMid"),
                json.optInt("sdpMLineIndex"),
                json.optString("candidate"),
            )
            peerConnection?.addIceCandidate(candidate)
            seenMessageIds.add(messageId)
        } catch (e: Exception) {
            Log.w(TAG, "ice parse failed", e)
        }
    }

    private fun scheduleIceRecovery(context: Context) {
        if (iceRecoveryRunnable != null || !running.get()) return
        val handler = ensureBackgroundPollHandler()
        iceRecoveryRunnable = Runnable {
            iceRecoveryRunnable = null
            if (!running.get()) return@Runnable
            val generation = publisherGeneration.get()
            val state = peerConnection?.iceConnectionState()
            if (state == PeerConnection.IceConnectionState.CONNECTED ||
                state == PeerConnection.IceConnectionState.COMPLETED
            ) {
                return@Runnable
            }
            val room = activeRoomId ?: return@Runnable
            val session = activeSessionId ?: return@Runnable
            if (iceRecoveryStage == 0) {
                iceRecoveryStage = 1
                StreamSessionRegistry.markReconnecting()
                StreamLifecycleTelemetry.record("ice_restart_attempt", StreamSessionRegistry.snapshot())
                PcpFlowLogger.log(PcpFlowLogger.Category.SCREEN_SHARE_FLOW, "native ICE restart", "room=$room")
                renegotiateWithIceRestart(context, room, session, generation)
                handler.postDelayed({
                    if (!isActiveGeneration(generation, session)) return@postDelayed
                    val after = peerConnection?.iceConnectionState()
                    if (after == PeerConnection.IceConnectionState.CONNECTED ||
                        after == PeerConnection.IceConnectionState.COMPLETED
                    ) {
                        iceRecoveryStage = 0
                        return@postDelayed
                    }
                    iceRecoveryStage = 2
                    StreamLifecycleTelemetry.record("ice_recovery_failed", StreamSessionRegistry.snapshot())
                    PcpFlowLogger.warn(PcpFlowLogger.Category.SCREEN_SHARE_FLOW, "native ICE recovery exhausted", "room=$room")
                    // Mirror the JS publisher behaviour: tell the backend the session is dead
                    // so the parent UI sees a "stream failed" message instead of a frozen view.
                    reportFailureAsync(context, session, "ICE_RECOVERY_EXHAUSTED")
                }, 12_000)
            }
        }
        val delay = if (peerConnection?.iceConnectionState() == PeerConnection.IceConnectionState.FAILED) {
            3000L
        } else {
            6000L
        }
        val runnable = iceRecoveryRunnable ?: return
        handler.postDelayed(runnable, delay)
    }

    private fun renegotiateWithIceRestart(
        context: Context,
        roomId: String,
        sessionId: String,
        generation: Long,
    ) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        val pc = peerConnection
        if (pc == null) {
            Log.w(TAG, "ice-restart offer skipped — PeerConnection gone")
            return
        }
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                if (!isActiveGeneration(generation, sessionId)) {
                    Log.w(TAG, "ice-restart offer ignored for stale session=$sessionId generation=$generation")
                    return
                }
                pc.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        if (!isActiveGeneration(generation, sessionId)) {
                            Log.w(TAG, "ice-restart local description ignored for stale session=$sessionId generation=$generation")
                            return
                        }
                        SignalingHttpClient.postMessage(
                            context,
                            roomId,
                            "offer",
                            JSONObject()
                                .put("type", desc.type.canonicalForm())
                                .put("sdp", desc.description)
                                .toString(),
                            sessionId,
                        )
                        Log.i(TAG, "ice-restart offer sent room=$roomId")
                    }
                }, desc)
            }
        }, constraints)
    }

    private fun extractSdp(payload: String): String {
        return try {
            val json = JSONObject(payload)
            if (json.has("sdp")) json.getString("sdp") else payload
        } catch (_: Exception) {
            payload
        }
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "SDP create failure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "SDP set failure: $error")
        }
    }
}
