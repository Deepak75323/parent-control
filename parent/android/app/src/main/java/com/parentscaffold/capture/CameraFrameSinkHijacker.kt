package com.parentscaffold.capture

import android.util.Log
import android.view.View
import com.oney.WebRTCModule.WebRTCView
import com.parentscaffold.BuildConfig
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

/**
 * Inserts a [RotatingVideoSink] between a [VideoTrack] and [WebRTCView]'s
 * [org.webrtc.SurfaceViewRenderer], rewriting [VideoFrame.rotation] so a portrait-held
 * child phone renders upright — matching the web portal.
 *
 * [WebRTCView] re-attaches its renderer directly to the track whenever the track is re-set,
 * which silently restores the uncorrected path, so the detach is re-asserted on every scan.
 */
object CameraFrameSinkHijacker {
    private const val TAG = "CameraFrameSink"
    private const val LOG_EVERY_FRAMES = 150

    /** `adb shell setprop debug.pcp.rot <0|90|180|270>` overrides the computed rotation. */
    private const val ROTATION_OVERRIDE_PROP = "debug.pcp.rot"

    private class RotatingVideoSink(
        private val delegate: VideoSink,
    ) : VideoSink {
        @Volatile var manualRotationDeg: Int = 0
        @Volatile var frontCamera: Boolean = true
        private var frameCount = 0
        private var lastLoggedCvo = Int.MIN_VALUE

        override fun onFrame(frame: VideoFrame) {
            val w = frame.buffer.width
            val h = frame.buffer.height
            val computed =
                CameraFrameRotation.rendererRotationDegrees(frame.rotation, manualRotationDeg)
            val override = rotationOverride()
            val outputRotation = override ?: computed

            // A tilt can start and end between periodic samples, so log every orientation change.
            val cvoChanged = frame.rotation != lastLoggedCvo
            if (frameCount++ % LOG_EVERY_FRAMES == 0 || cvoChanged) {
                lastLoggedCvo = frame.rotation
                Log.i(
                    TAG,
                    "frame #$frameCount cvo=${frame.rotation} computed=$computed " +
                        "override=$override -> renderer=$outputRotation buffer=${w}x$h",
                )
            }

            if (outputRotation == frame.rotation) {
                delegate.onFrame(frame)
            } else {
                delegate.onFrame(VideoFrame(frame.buffer, outputRotation, frame.timestampNs))
            }
        }
    }

    private class Hijack(
        val sink: RotatingVideoSink,
        val track: VideoTrack,
        val renderer: VideoSink,
    )

    private val hijacks = mutableMapOf<Int, Hijack>()

    @Volatile private var cachedOverride: Int? = null
    @Volatile private var overrideCheckedAtMs = 0L

    private fun rotationOverride(): Int? {
        if (!BuildConfig.DEBUG) {
            return null
        }
        val now = System.currentTimeMillis()
        if (now - overrideCheckedAtMs < 1000L) {
            return cachedOverride
        }
        overrideCheckedAtMs = now
        cachedOverride =
            try {
                val cls = Class.forName("android.os.SystemProperties")
                val get = cls.getMethod("get", String::class.java, String::class.java)
                val raw = get.invoke(null, ROTATION_OVERRIDE_PROP, "") as? String
                raw?.toIntOrNull()?.let { ((it % 360) + 360) % 360 }
            } catch (_: Exception) {
                null
            }
        return cachedOverride
    }

    /** Returns true when this view's track is routed through the rotating sink. */
    fun attach(
        webrtcView: WebRTCView,
        frontCamera: Boolean,
        manualRotationDeg: Int,
    ): Boolean {
        val key = System.identityHashCode(webrtcView)
        hijacks[key]?.let { existing ->
            existing.sink.frontCamera = frontCamera
            existing.sink.manualRotationDeg = manualRotationDeg
            reassertDetach(existing)
            return true
        }

        try {
            if (!isRendererAttached(webrtcView)) {
                return false
            }
            val renderer = readField(webrtcView, "surfaceViewRenderer") as? VideoSink ?: return false
            val track = readField(webrtcView, "videoTrack") as? VideoTrack ?: return false

            val sink = RotatingVideoSink(renderer)
            sink.frontCamera = frontCamera
            sink.manualRotationDeg = manualRotationDeg
            hijacks[key] = Hijack(sink, track, renderer)

            runOnWebRtcExecutor {
                try {
                    track.removeSink(renderer)
                    track.addSink(sink)
                    Log.i(TAG, "hijacked WebRTCView key=$key front=$frontCamera")
                } catch (tr: Throwable) {
                    Log.w(TAG, "hijack failed", tr)
                    hijacks.remove(key)
                }
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "attach failed", e)
            return false
        }
    }

    /** removeSink is a no-op when absent, so this is safe to repeat every scan. */
    private fun reassertDetach(hijack: Hijack) {
        runOnWebRtcExecutor {
            try {
                hijack.track.removeSink(hijack.renderer)
            } catch (_: Throwable) {
                // Track disposed — the scanner will drop this hijack on the next sweep.
            }
        }
    }

    fun detach(webrtcView: View) {
        detachKey(System.identityHashCode(webrtcView))
    }

    /** Keys of views currently routed through a rotating sink. */
    fun activeKeys(): Set<Int> = hijacks.keys.toSet()

    /** Releases the sink so a disposed VideoTrack does not retain it. */
    fun detachKey(key: Int) {
        val hijack = hijacks.remove(key) ?: return
        runOnWebRtcExecutor {
            try {
                hijack.track.removeSink(hijack.sink)
                Log.i(TAG, "released hijack key=$key")
            } catch (tr: Throwable) {
                // Track already released by WebRTCModule — nothing to detach.
            }
        }
    }

    private fun readField(view: WebRTCView, name: String): Any? {
        val field = WebRTCView::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(view)
    }

    private fun isRendererAttached(webrtcView: WebRTCView): Boolean =
        try {
            val field = WebRTCView::class.java.getDeclaredField("rendererAttached")
            field.isAccessible = true
            field.getBoolean(webrtcView)
        } catch (_: Exception) {
            false
        }

    private fun runOnWebRtcExecutor(runnable: Runnable) {
        try {
            val threadUtils = Class.forName("com.oney.WebRTCModule.ThreadUtils")
            val method = threadUtils.getDeclaredMethod("runOnExecutor", Runnable::class.java)
            method.invoke(null, runnable)
        } catch (e: Exception) {
            Log.w(TAG, "ThreadUtils unavailable", e)
            runnable.run()
        }
    }
}
