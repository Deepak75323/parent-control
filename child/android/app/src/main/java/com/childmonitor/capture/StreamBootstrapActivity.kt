package com.childmonitor.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.childmonitor.MonitoringPrefs
import com.childmonitor.StreamingHeadlessTaskService
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.native.ChildMonitorNativeModule
import com.childmonitor.sync.NativeStreamingCoordinator
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Invisible activity that satisfies Android 14+ eligibility to start typed foreground
 * services (camera, microphone, screen capture) without showing the child app UI.
 *
 * FGS promotion runs from [onResume] (not [onCreate]) so the process is already in a
 * while-in-use eligible state before [Service.startForeground] with type camera/mic.
 */
class StreamBootstrapActivity : Activity() {

    private val bootstrapStarted = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        val kind = streamKindFrom(intent.getStringExtra(EXTRA_STREAM_KIND))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            // Only screen capture may need to wake the display; camera/audio bootstrap must
            // stay invisible so parent monitoring does not flash over YouTube / games.
            if (kind == StreamKind.SCREEN) {
                setTurnScreenOn(true)
            }
        }
        // Cancel the stream-request notification immediately — it was only shown to obtain a
        // legal foreground window token to bypass MIUI BAL; now that the activity is alive the
        // notification heads-up must not remain visible.
        StreamRequestNotificationHelper.cancel(this)
        if (!MonitoringPrefs.isPaired(this)) {
            finishImmediate()
            return
        }

        if (!hasPermissionFor(kind)) {
            Log.e(TAG, "Missing permission for $kind — abort bootstrap")
            finishImmediate()
            return
        }

        // FGS may already be pre-warmed — skip visible transition work when possible.
        if (StreamFgsHelper.isActive(kind)) {
            startStreamAfterFgs(kind, intent)
            finishImmediate()
        }
    }

    override fun onStart() {
        super.onStart()
        AppForegroundState.onActivityVisible()
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        val kind = streamKindFrom(intent.getStringExtra(EXTRA_STREAM_KIND))
        if (StreamFgsHelper.isActive(kind)) {
            finishImmediate()
            return
        }
        if (!bootstrapStarted.compareAndSet(false, true)) {
            finishImmediate()
            return
        }

        val roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val fgsOnly = intent.getBooleanExtra(EXTRA_FGS_ONLY, false)
        val jsListenerActive = intent.getBooleanExtra(EXTRA_JS_LISTENER_ACTIVE, false)

        PcpFlowLogger.log(
            PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
            "StreamBootstrapActivity",
            "onResume kind=$kind room=$roomId session=$sessionId jsActive=$jsListenerActive fgsOnly=$fgsOnly",
        )

        // Android 14+ trampoline activities must call finish() before onResume() returns.
        // Never block the main thread here — that triggers ANR and IllegalStateException on Android 16.
        val bootstrapIntent = intent
        val fgsAlreadyActive = StreamFgsHelper.isActive(kind)
        if (!fgsAlreadyActive) {
            StreamFgsHelper.startServiceIntent(applicationContext, kind)
        }
        Thread({
            var fgsReady = fgsAlreadyActive
            try {
                if (!fgsReady) {
                    fgsReady =
                        StreamFgsPreparer.ensureKindReadyForStreamRequest(applicationContext, kind)
                }
                if (!fgsReady) {
                    Log.e(TAG, "$kind FGS bootstrap failed after resume")
                } else if (!fgsOnly && roomId.isNotBlank()) {
                    startStreamAfterFgs(kind, bootstrapIntent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "$kind FGS bootstrap crashed", t)
            } finally {
                StreamFgsBootstrap.signalComplete(fgsReady)
            }
        }, "stream-bootstrap-fgs").start()
        finishImmediate()
    }

    override fun onStop() {
        AppForegroundState.onActivityHidden()
        super.onStop()
    }

    private fun startStreamAfterFgs(kind: StreamKind, intent: Intent) {
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: kind.name
        val cameraFacing = intent.getStringExtra(EXTRA_CAMERA_FACING)
        val useNativeScreen =
            intent.getBooleanExtra(EXTRA_USE_NATIVE_SCREEN, false) &&
                kind == StreamKind.SCREEN

        if (roomId.isBlank()) return
        if (useNativeScreen) {
            ScreenCaptureForegroundService.startStream(applicationContext, roomId, sessionId)
            return
        }
        if (ChildMonitorNativeModule.canDeliverStreamToJs() &&
            ChildMonitorNativeModule.emitStreamWake(roomId, streamType, sessionId, cameraFacing)
        ) {
            NativeStreamingCoordinator.onJsWakeEmitted(roomId, streamType, sessionId, cameraFacing)
            return
        }
        StreamingHeadlessTaskService.start(
            applicationContext,
            roomId,
            sessionId,
            streamType,
            cameraFacing,
        )
    }

    private fun finishImmediate() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun hasPermissionFor(kind: StreamKind): Boolean = when (kind) {
        StreamKind.CAMERA ->
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        StreamKind.AUDIO ->
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        StreamKind.SCREEN -> ScreenCaptureController.canUseSilentCapture(this)
    }

    companion object {
        private const val TAG = "StreamBootstrap"
        private const val EXTRA_STREAM_KIND = "streamKind"
        private const val EXTRA_ROOM_ID = "roomId"
        private const val EXTRA_SESSION_ID = "sessionId"
        private const val EXTRA_STREAM_TYPE = "streamType"
        private const val EXTRA_CAMERA_FACING = "cameraFacing"
        private const val EXTRA_JS_LISTENER_ACTIVE = "jsListenerActive"
        private const val EXTRA_FGS_ONLY = "fgsOnly"
        private const val EXTRA_USE_NATIVE_SCREEN = "useNativeScreen"

        @JvmStatic
        fun startFgsOnly(context: Context, kind: StreamKind) {
            val intent = Intent(context, StreamBootstrapActivity::class.java).apply {
                putExtra(EXTRA_STREAM_KIND, kind.name)
                putExtra(EXTRA_FGS_ONLY, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /**
         * Start [StreamBootstrapActivity] via [BalSafeActivityLauncher] — required for
         * MIUI/HyperOS where direct startActivity() from a service context is silently
         * blocked. See [BalSafeActivityLauncher] for the fallback chain.
         */
        fun startWithBalFallback(
            context: Context,
            kind: StreamKind,
            roomId: String,
            sessionId: String,
            streamType: String,
            cameraFacing: String?,
            jsListenerActive: Boolean,
            useNativeScreen: Boolean = false,
        ) {
            val intent = buildBootstrapIntent(
                context, kind, roomId, sessionId, streamType,
                cameraFacing, jsListenerActive, useNativeScreen,
            )
            launchWithFallback(context, intent, kind)
        }

        /** FGS-only variant (no stream routing — used by [StreamFgsBootstrap]). */
        fun startFgsOnlyWithFallback(context: Context, kind: StreamKind) {
            val intent = Intent(context, StreamBootstrapActivity::class.java).apply {
                putExtra(EXTRA_STREAM_KIND, kind.name)
                putExtra(EXTRA_FGS_ONLY, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchWithFallback(context, intent, kind)
        }

        private fun launchWithFallback(context: Context, intent: Intent, kind: StreamKind) {
            BalSafeActivityLauncher.launch(context, intent, "StreamBootstrap(kind=$kind)")
        }

        fun start(
            context: Context,
            kind: StreamKind,
            roomId: String,
            sessionId: String,
            streamType: String,
            cameraFacing: String?,
            jsListenerActive: Boolean,
            useNativeScreen: Boolean = false,
        ) {
            val intent = buildBootstrapIntent(
                context, kind, roomId, sessionId, streamType,
                cameraFacing, jsListenerActive, useNativeScreen,
            )
            context.startActivity(intent)
        }

        private fun buildBootstrapIntent(
            context: Context,
            kind: StreamKind,
            roomId: String,
            sessionId: String,
            streamType: String,
            cameraFacing: String?,
            jsListenerActive: Boolean,
            useNativeScreen: Boolean,
        ): Intent = Intent(context, StreamBootstrapActivity::class.java).apply {
            putExtra(EXTRA_STREAM_KIND, kind.name)
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_STREAM_TYPE, streamType)
            putExtra(EXTRA_CAMERA_FACING, cameraFacing)
            putExtra(EXTRA_JS_LISTENER_ACTIVE, jsListenerActive)
            putExtra(EXTRA_USE_NATIVE_SCREEN, useNativeScreen)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        private fun streamKindFrom(raw: String?): StreamKind = when (raw?.uppercase()) {
            "AUDIO" -> StreamKind.AUDIO
            "SCREEN" -> StreamKind.SCREEN
            else -> StreamKind.CAMERA
        }
    }
}
