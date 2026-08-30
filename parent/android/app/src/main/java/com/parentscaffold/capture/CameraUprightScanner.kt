package com.parentscaffold.capture

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.facebook.react.R
import com.oney.WebRTCModule.WebRTCView
import java.lang.ref.WeakReference

/**
 * Polls the view tree for the live camera [WebRTCView] and routes its frames through
 * [CameraFrameSinkHijacker].
 *
 * Scanning is native because Fabric returns null from JS `findNodeHandle` for host
 * components. A view qualifies as the camera when it carries the [CAMERA_NATIVE_ID]
 * `nativeID` prop, or when JS explicitly enables camera mode — screen share never matches
 * either, so it stays untouched.
 */
object CameraUprightScanner {
    private const val TAG = "CameraUprightScanner"
    private const val SCAN_MS = 500L
    private const val DIAG_EVERY = 8
    const val CAMERA_NATIVE_ID = "primaryCameraRtc"

    private val handler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null

    private var jsCameraEnabled = false
    private var resumed = false
    private var running = false
    private var scanCount = 0

    @Volatile private var frontCamera = true
    @Volatile private var manualRotationDeg = 0

    /** Optional hint from JS; scanning also works without it via [CAMERA_NATIVE_ID]. */
    fun setCameraEnabled(enabled: Boolean, front: Boolean, manualDeg: Int) {
        frontCamera = front
        manualRotationDeg = manualDeg
        jsCameraEnabled = enabled
        Log.i(TAG, "js camera hint enabled=$enabled front=$front manual=$manualDeg")
    }

    fun onActivityResumed(activity: Activity) {
        activityRef = WeakReference(activity)
        resumed = true
        syncRunning()
    }

    fun onActivityPaused() {
        resumed = false
        syncRunning()
    }

    private fun syncRunning() {
        if (resumed == running) {
            return
        }
        running = resumed
        handler.removeCallbacks(scanRunnable)
        if (running) {
            scanCount = 0
            Log.i(TAG, "scanning started")
            handler.post(scanRunnable)
        } else {
            Log.i(TAG, "scanning stopped")
        }
    }

    private val scanRunnable =
        object : Runnable {
            override fun run() {
                if (!running) {
                    return
                }
                try {
                    scanOnce()
                } catch (e: Exception) {
                    Log.w(TAG, "scan failed", e)
                }
                handler.postDelayed(this, SCAN_MS)
            }
        }

    private fun scanOnce() {
        val activity = activityRef?.get()
        if (activity == null || activity.isFinishing) {
            return
        }
        val decor = activity.window?.decorView ?: return

        val views = mutableListOf<WebRTCView>()
        collectWebRtcViews(decor, views)

        releaseStaleHijacks(views)

        val diag = scanCount++ % DIAG_EVERY == 0
        if (views.isEmpty()) {
            if (diag) {
                Log.d(TAG, "no WebRTCView in tree (jsCameraEnabled=$jsCameraEnabled)")
            }
            return
        }

        for (view in views) {
            val nativeId = nativeIdOf(view)
            val isCamera = nativeId == CAMERA_NATIVE_ID || jsCameraEnabled
            if (!isCamera) {
                if (diag) {
                    Log.d(TAG, "skip non-camera view nativeID=$nativeId")
                }
                continue
            }
            val attached = CameraFrameSinkHijacker.attach(view, frontCamera, manualRotationDeg)
            if (!attached && diag) {
                Log.d(TAG, "camera view nativeID=$nativeId not ready (renderer/track pending)")
            }
        }
    }

    /** A hijacked view that left the tree still holds a sink on a track about to be disposed. */
    private fun releaseStaleHijacks(liveViews: List<WebRTCView>) {
        val liveKeys = liveViews.mapTo(mutableSetOf()) { System.identityHashCode(it) }
        for (key in CameraFrameSinkHijacker.activeKeys()) {
            if (key !in liveKeys) {
                CameraFrameSinkHijacker.detachKey(key)
            }
        }
    }

    private fun nativeIdOf(view: View): String? =
        try {
            view.getTag(R.id.view_tag_native_id) as? String
        } catch (_: Exception) {
            null
        }

    private fun collectWebRtcViews(root: View, out: MutableList<WebRTCView>) {
        if (root is WebRTCView) {
            out.add(root)
            return
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                collectWebRtcViews(root.getChildAt(i), out)
            }
        }
    }
}
