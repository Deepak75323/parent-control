package com.childmonitor.capture

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages camera access priority for parental monitoring.
 * Can forcefully take camera access from other apps when needed.
 */
object CameraAccessManager {

    private const val TAG = "CameraAccessManager"
    private const val PROBE_OPEN_TIMEOUT_MS = 1200L
    private const val TAKEOVER_RETRY_DELAY_MS = 400L
    private const val TAKEOVER_MAX_ATTEMPTS = 2

    /** Parent stream requests — always displace, longer timeout (pre–Aug 2026 behavior). */
    private const val AGGRESSIVE_PROBE_OPEN_TIMEOUT_MS = 5000L
    private const val AGGRESSIVE_TAKEOVER_RETRY_DELAY_MS = 500L
    private const val AGGRESSIVE_TAKEOVER_MAX_ATTEMPTS = 4

    private val isManaging = AtomicBoolean(false)
    private val currentCameraDevice = AtomicReference<CameraDevice?>(null)
    private val cameraManager = AtomicReference<CameraManager?>(null)
    private val availabilityRegistered = AtomicBoolean(false)
    private val cameraAvailable = ConcurrentHashMap<String, Boolean>()
    private val lastProbeSkipped = AtomicBoolean(false)

    /** Used by unit tests and capture fallback when a skipped probe was wrong. */
    internal fun shouldSkipDisplacementProbe(reportedAvailable: Boolean?): Boolean =
        reportedAvailable != false

    /**
     * Initialize the camera access manager and track camera availability to avoid
     * unnecessary open/close probes (each probe blinks the privacy indicator).
     */
    fun initialize(context: Context) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.set(manager)
        registerAvailabilityTracking(manager)
    }

    private fun registerAvailabilityTracking(manager: CameraManager) {
        if (!availabilityRegistered.compareAndSet(false, true)) {
            return
        }
        try {
            for (id in manager.cameraIdList) {
                cameraAvailable.putIfAbsent(id, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not seed camera availability", e)
        }
        manager.registerAvailabilityCallback(
            object : CameraManager.AvailabilityCallback() {
                override fun onCameraAvailable(@NonNull cameraId: String) {
                    cameraAvailable[cameraId] = true
                    Log.d(TAG, "Camera $cameraId available")
                }

                override fun onCameraUnavailable(@NonNull cameraId: String) {
                    cameraAvailable[cameraId] = false
                    Log.d(TAG, "Camera $cameraId unavailable (held by another app)")
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    /**
     * Prepare for CameraX capture with minimal privacy-indicator flicker.
     * Skips the open/close displacement probe when the camera is not held by another app.
     *
     * @param aggressive when true (parent stream takeover), always runs displacement with
     *   longer timeout and more retries — does not skip the probe.
     */
    fun ensureCameraReadyForCapture(cameraId: String, aggressive: Boolean = false): Boolean {
        if (!isManaging.compareAndSet(false, true)) {
            Log.d(TAG, "Camera access already being managed")
            lastProbeSkipped.set(false)
            return true
        }

        try {
            val manager = cameraManager.get() ?: run {
                Log.e(TAG, "CameraManager not initialized")
                isManaging.set(false)
                lastProbeSkipped.set(false)
                return false
            }

            if (aggressive) {
                lastProbeSkipped.set(false)
                Log.i(TAG, "Aggressive camera takeover for $cameraId (parent stream)")
                return displaceOtherApp(manager, cameraId, aggressive = true)
            }

            val reportedAvailable = cameraAvailable[cameraId]
            if (shouldSkipDisplacementProbe(reportedAvailable)) {
                lastProbeSkipped.set(true)
                Log.i(
                    TAG,
                    "Camera $cameraId appears free (availability=$reportedAvailable) — " +
                        "skipping displacement probe",
                )
                return true
            }

            lastProbeSkipped.set(false)
            Log.i(TAG, "Camera $cameraId reported in use — displacement probe for CameraX")
            return displaceOtherApp(manager, cameraId, aggressive = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare camera $cameraId", e)
            currentCameraDevice.getAndSet(null)?.close()
            isManaging.set(false)
            lastProbeSkipped.set(false)
            return false
        }
    }

    /** Parent stream path — always attempt displacement (e.g. evict Discord). */
    fun forceCameraAccess(cameraId: String): Boolean =
        ensureCameraReadyForCapture(cameraId, aggressive = true)

    /** True when the last [ensureCameraReadyForCapture] skipped the displacement probe. */
    fun lastProbeWasSkipped(): Boolean = lastProbeSkipped.get()

    /**
     * Open/close once to evict another app. Used when CameraX bind fails after a skipped probe.
     */
    fun displaceForCapture(cameraId: String): Boolean {
        if (!isManaging.compareAndSet(false, true)) {
            return true
        }
        val manager = cameraManager.get() ?: run {
            isManaging.set(false)
            return false
        }
        lastProbeSkipped.set(false)
        cameraAvailable[cameraId] = false
        return displaceOtherApp(manager, cameraId, aggressive = true)
    }

    private fun displaceOtherApp(
        manager: CameraManager,
        cameraId: String,
        aggressive: Boolean,
    ): Boolean {
        val maxAttempts =
            if (aggressive) AGGRESSIVE_TAKEOVER_MAX_ATTEMPTS else TAKEOVER_MAX_ATTEMPTS
        val retryDelayMs =
            if (aggressive) AGGRESSIVE_TAKEOVER_RETRY_DELAY_MS else TAKEOVER_RETRY_DELAY_MS
        val openTimeoutMs =
            if (aggressive) AGGRESSIVE_PROBE_OPEN_TIMEOUT_MS else PROBE_OPEN_TIMEOUT_MS

        for (attempt in 1..maxAttempts) {
            if (attempt > 1) {
                Log.i(TAG, "Camera displacement retry $attempt/$maxAttempts for $cameraId")
                Thread.sleep(retryDelayMs)
            }
            currentCameraDevice.getAndSet(null)?.close()
            val acquired = tryOpenBriefly(manager, cameraId, openTimeoutMs)
            currentCameraDevice.getAndSet(null)?.close()
            if (acquired) {
                cameraAvailable[cameraId] = true
                Log.i(
                    TAG,
                    "Camera $cameraId displaced; released for CameraX " +
                        "(attempt $attempt aggressive=$aggressive)",
                )
                return true
            }
        }
        Log.w(
            TAG,
            "Camera displacement failed after $maxAttempts attempts for $cameraId " +
                "(aggressive=$aggressive)",
        )
        isManaging.set(false)
        return false
    }

    private fun tryOpenBriefly(
        manager: CameraManager,
        cameraId: String,
        openTimeoutMs: Long,
    ): Boolean {
        val latch = CountDownLatch(1)
        val success = AtomicBoolean(false)

        try {
            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(@NonNull camera: CameraDevice) {
                        Log.d(TAG, "Camera $cameraId opened for displacement")
                        currentCameraDevice.set(camera)
                        success.set(true)
                        latch.countDown()
                    }

                    override fun onDisconnected(@NonNull camera: CameraDevice) {
                        camera.close()
                        latch.countDown()
                    }

                    override fun onError(@NonNull camera: CameraDevice, error: Int) {
                        Log.d(TAG, "Camera $cameraId displacement open error=$error")
                        camera.close()
                        latch.countDown()
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            return latch.await(openTimeoutMs, TimeUnit.MILLISECONDS) && success.get()
        } catch (e: CameraAccessException) {
            Log.d(TAG, "Displacement open failed: ${e.message}")
            return false
        }
    }

    fun releaseCamera() {
        Log.i(TAG, "Releasing camera access")
        currentCameraDevice.get()?.close()
        currentCameraDevice.set(null)
        isManaging.set(false)
        lastProbeSkipped.set(false)
    }

    fun hasCameraAccess(): Boolean = currentCameraDevice.get() != null

    fun getCurrentCameraDevice(): CameraDevice? = currentCameraDevice.get()
}
