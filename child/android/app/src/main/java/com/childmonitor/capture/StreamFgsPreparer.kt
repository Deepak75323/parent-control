package com.childmonitor.capture

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.childmonitor.MonitoringForegroundService
import com.childmonitor.MonitoringPrefs
import com.childmonitor.monitoring.MonitoringPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Proactively starts typed stream FGS (camera, mic, screen) while the monitoring
 * process is alive so background parent requests rarely need [StreamBootstrapActivity].
 *
 * On Android 15+ ([MonitoringPlatform.useBurstFgs]) the monitoring service runs in
 * short bursts, not 24/7. [ensureAllReadyAsync] only pre-warms during those bursts to
 * avoid wasting battery. When a parent stream request actually arrives,
 * [ensureKindReadyForStreamRequest] bypasses the burst gate — the inbound request itself
 * is the user-initiated trigger that lets us start typed FGS legally on API 35+.
 */
object StreamFgsPreparer {

    private const val TAG = "StreamFgsPreparer"
    private val preparingAll = AtomicBoolean(false)

    /** Warm all stream FGS on a worker thread (non-blocking for callers). */
    @JvmStatic
    fun ensureAllReadyAsync(context: Context) {
        if (!MonitoringPrefs.isPaired(context)) return
        if (!preparingAll.compareAndSet(false, true)) return
        Thread {
            try {
                // Always pre-warm camera/audio FGS — user-initiated streams don't need monitoring burst.
                // This ensures camera works even when monitoring service is in burst mode and inactive.
                ensureKindReady(context, StreamKind.CAMERA)
                ensureKindReady(context, StreamKind.AUDIO)
                
                // Only gate screen FGS on monitoring burst state — screen capture is background monitoring.
                if (MonitoringPlatform.useBurstFgs() && !MonitoringForegroundService.isBurstActive()) {
                    Log.i(TAG, "Skipping screen FGS pre-warm — no active monitoring burst (Android 15+)")
                } else if (ScreenFgsStartupGate.canStartScreenFgs() &&
                    MediaProjectionConsentHelper.hasConsent(context) &&
                    MediaProjectionSessionKeeper.ensureReady(context)
                ) {
                    ensureKindReady(context, StreamKind.SCREEN)
                }
            } finally {
                preparingAll.set(false)
            }
        }.start()
    }

    /**
     * P1-2: stream request fast path. Called directly from the inbound stream
     * request handler (STOMP/FCM wake) so we may start typed FGS even when there is
     * no active monitoring burst — the parent's request is the user-initiated trigger
     * that satisfies Android 15+ background-start restrictions.
     *
     * Caller must invoke from a worker thread (blocks until FGS active or timeout).
     */
    @JvmStatic
    fun ensureKindReadyForStreamRequest(
        context: Context,
        kind: StreamKind,
        timeoutMs: Long = 8000L,
    ): Boolean {
        if (!MonitoringPrefs.isPaired(context)) return false
        if (StreamFgsHelper.isActive(kind)) return true
        if (!hasPrerequisites(context, kind)) {
            Log.w(TAG, "$kind FGS prerequisites not met for stream request")
            return false
        }
        val appContext = context.applicationContext
        if (kind == StreamKind.SCREEN) {
            // Parent stream request is the user-initiated trigger — open the cold-start
            // gate so MediaProjection FGS can start even when MainActivity never resumed
            // (Android 15 burst + FCM-only wakes).
            if (!ScreenFgsStartupGate.canStartScreenFgs()) {
                ScreenFgsStartupGate.markReadyNow()
                Log.i(TAG, "Screen FGS gate forced open for inbound stream request")
            }
            if (!MediaProjectionSessionKeeper.ensureReady(appContext)) {
                Log.w(TAG, "Screen projection session not ready for stream-request FGS start")
                return false
            }
        }
        if (kind == StreamKind.CAMERA) {
            // Video call always needs the mic FGS alongside camera (Android 14+).
            if (!StreamFgsHelper.isActive(StreamKind.AUDIO)) {
                ensureKindReady(context, StreamKind.AUDIO, timeoutMs)
            }
        }
        val ready = StreamFgsHelper.startAndWait(appContext, kind, timeoutMs)
        if (ready) {
            Log.i(TAG, "$kind FGS started for inbound stream request")
        } else {
            Log.w(TAG, "$kind FGS failed to start for inbound stream request — caller should fall back to StreamBootstrapActivity")
        }
        return ready
    }

    /**
     * Blocks until the typed FGS is active or start fails.
     * Call from a worker thread only.
     */
    @JvmStatic
    fun ensureKindReady(context: Context, kind: StreamKind, timeoutMs: Long = 8000L): Boolean {
        if (!MonitoringPrefs.isPaired(context)) return false
        if (StreamFgsHelper.isActive(kind)) return true
        if (!hasPrerequisites(context, kind)) return false

        val appContext = context.applicationContext
        if (kind == StreamKind.SCREEN) {
            if (!ScreenFgsStartupGate.canStartScreenFgs()) {
                Log.i(TAG, "Screen FGS pre-warm deferred — app still starting")
                return false
            }
            if (!MediaProjectionSessionKeeper.ensureReady(appContext)) {
                Log.w(TAG, "Screen projection session not ready for FGS pre-warm")
                return false
            }
        }

        val ready = StreamFgsHelper.startAndWait(appContext, kind, timeoutMs)
        if (ready) {
            Log.i(TAG, "$kind FGS pre-warmed")
        } else {
            Log.w(TAG, "$kind FGS pre-warm failed")
        }
        return ready
    }

    private fun hasPrerequisites(context: Context, kind: StreamKind): Boolean = when (kind) {
        StreamKind.CAMERA ->
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        StreamKind.AUDIO ->
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        StreamKind.SCREEN ->
            MediaProjectionConsentHelper.hasConsent(context)
    }
}
