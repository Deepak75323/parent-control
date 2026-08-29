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
     */
    fun ensureCameraReadyForCapture(cameraId: String): Boolean {
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
            return displaceOtherApp(manager, cameraId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare camera $cameraId", e)
            currentCameraDevice.getAndSet(null)?.close()
            isManaging.set(false)
            lastProbeSkipped.set(false)
            return false
        }
    }

    /** @deprecated Prefer [ensureCameraReadyForCapture]. Kept for RN debug APIs. */
    fun forceCameraAccess(cameraId: String): Boolean = ensureCameraReadyForCapture(cameraId)

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
        return displaceOtherApp(manager, cameraId)
    }

    private fun displaceOtherApp(manager: CameraManager, cameraId: String): Boolean {
        for (attempt in 1..TAKEOVER_MAX_ATTEMPTS) {
            if (attempt > 1) {
                Log.i(TAG, "Camera displacement retry $attempt/$TAKEOVER_MAX_ATTEMPTS for $cameraId")
                Thread.sleep(TAKEOVER_RETRY_DELAY_MS)
            }
            currentCameraDevice.getAndSet(null)?.close()
            val acquired = tryOpenBriefly(manager, cameraId)
            currentCameraDevice.getAndSet(null)?.close()
            if (acquired) {
                cameraAvailable[cameraId] = true
                Log.i(TAG, "Camera $cameraId displaced; released for CameraX (attempt $attempt)")
                return true
            }
        }
        Log.w(TAG, "Camera displacement failed after $TAKEOVER_MAX_ATTEMPTS attempts for $cameraId")
        isManaging.set(false)
        return false
    }

    private fun tryOpenBriefly(manager: CameraManager, cameraId: String): Boolean {
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
            return latch.await(PROBE_OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS) && success.get()
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
