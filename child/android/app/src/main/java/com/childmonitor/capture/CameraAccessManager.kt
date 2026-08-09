package com.childmonitor.capture

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
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
    private const val FORCE_TAKEOVER_TIMEOUT_MS = 5000L
    
    private val isManaging = AtomicBoolean(false)
    private val currentCameraDevice = AtomicReference<CameraDevice?>(null)
    private val cameraManager = AtomicReference<CameraManager?>(null)
    
    /**
     * Initialize the camera access manager
     */
    fun initialize(context: Context) {
        cameraManager.set(context.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
    }
    
    /**
     * Attempts to force camera access by closing other apps' camera sessions
     * @param cameraId The camera ID to acquire (usually "0" for back, "1" for front)
     * @return true if camera access was successfully acquired or forced
     */
    fun forceCameraAccess(cameraId: String): Boolean {
        if (!isManaging.compareAndSet(false, true)) {
            Log.d(TAG, "Camera access already being managed")
            return true
        }
        
        try {
            val manager = cameraManager.get() ?: run {
                Log.e(TAG, "CameraManager not initialized")
                isManaging.set(false)
                return false
            }
            
            Log.i(TAG, "Attempting to force camera access for camera $cameraId")
            
            // First, try normal camera access
            if (tryNormalCameraAccess(manager, cameraId)) {
                Log.i(TAG, "Camera $cameraId acquired normally")
                return true
            }
            
            // If normal access failed, force takeover
            Log.w(TAG, "Normal camera access failed, attempting force takeover")
            return forceCamera Takeover(manager, cameraId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force camera access", e)
            isManaging.set(false)
            return false
        }
    }
    
    /**
     * Try normal camera access first
     */
    private fun tryNormalCameraAccess(manager: CameraManager, cameraId: String): Boolean {
        val latch = CountDownLatch(1)
        val success = AtomicBoolean(false)
        
        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(@NonNull camera: CameraDevice) {
                    Log.d(TAG, "Camera $cameraId opened normally")
                    currentCameraDevice.set(camera)
                    success.set(true)
                    latch.countDown()
                }
                
                override fun onDisconnected(@NonNull camera: CameraDevice) {
                    Log.d(TAG, "Camera $cameraId disconnected during normal open")
                    camera.close()
                    latch.countDown()
                }
                
                override fun onError(@NonNull camera: CameraDevice, error: Int) {
                    Log.d(TAG, "Camera $cameraId error during normal open: $error")
                    camera.close()
                    latch.countDown()
                }
            }, Handler(Looper.getMainLooper()))
            
            return latch.await(2000, TimeUnit.MILLISECONDS) && success.get()
            
        } catch (e: CameraAccessException) {
            Log.d(TAG, "Normal camera access failed: ${e.message}")
            return false
        }
    }
    
    /**
     * Force camera takeover by using Android's camera priority system
     */
    private fun forceCameraTakeover(manager: CameraManager, cameraId: String): Boolean {
        Log.i(TAG, "Forcing camera takeover for camera $cameraId")
        
        // Strategy 1: Open camera with high priority callback
        val forceSuccess = AtomicBoolean(false)
        val forceLatch = CountDownLatch(1)
        
        try {
            // Register availability callback to detect when camera becomes available
            val availabilityCallback = object : CameraManager.AvailabilityCallback() {
                override fun onCameraAvailable(@NonNull cameraId: String) {
                    Log.d(TAG, "Camera $cameraId became available during force takeover")
                    // Try to grab it immediately
                    tryImmediateCapture(manager, cameraId, forceSuccess, forceLatch)
                }
                
                override fun onCameraUnavailable(@NonNull cameraId: String) {
                    Log.d(TAG, "Camera $cameraId unavailable during force takeover")
                }
            }
            
            manager.registerAvailabilityCallback(availabilityCallback, Handler(Looper.getMainLooper()))
            
            // Also try immediate capture
            tryImmediateCapture(manager, cameraId, forceSuccess, forceLatch)
            
            val success = forceLatch.await(FORCE_TAKEOVER_TIMEOUT_MS, TimeUnit.MILLISECONDS) && forceSuccess.get()
            
            // Unregister callback
            manager.unregisterAvailabilityCallback(availabilityCallback)
            
            return success
            
        } catch (e: Exception) {
            Log.e(TAG, "Force camera takeover failed", e)
            return false
        }
    }
    
    /**
     * Try to immediately capture the camera
     */
    private fun tryImmediateCapture(
        manager: CameraManager,
        cameraId: String, 
        successFlag: AtomicBoolean,
        latch: CountDownLatch
    ) {
        if (successFlag.get()) return // Already succeeded
        
        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(@NonNull camera: CameraDevice) {
                    Log.i(TAG, "FORCE TAKEOVER SUCCESS: Camera $cameraId acquired from other app")
                    currentCameraDevice.set(camera)
                    successFlag.set(true)
                    latch.countDown()
                }
                
                override fun onDisconnected(@NonNull camera: CameraDevice) {
                    Log.d(TAG, "Camera $cameraId disconnected during force takeover")
                    camera.close()
                    if (!successFlag.get()) {
                        latch.countDown()
                    }
                }
                
                override fun onError(@NonNull camera: CameraDevice, error: Int) {
                    val errorMsg = when (error) {
                        CameraDevice.STATE_ERROR_CAMERA_IN_USE -> "Camera in use by another app"
                        CameraDevice.STATE_ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                        CameraDevice.STATE_ERROR_CAMERA_DISABLED -> "Camera disabled"
                        CameraDevice.STATE_ERROR_CAMERA_DEVICE -> "Camera device error"
                        CameraDevice.STATE_ERROR_CAMERA_SERVICE -> "Camera service error"
                        else -> "Unknown error $error"
                    }
                    Log.w(TAG, "Camera $cameraId error during force takeover: $errorMsg")
                    camera.close()
                    if (!successFlag.get()) {
                        latch.countDown()
                    }
                }
            }, Handler(Looper.getMainLooper()))
            
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Immediate capture failed: ${e.message}")
            if (!successFlag.get()) {
                latch.countDown()
            }
        }
    }
    
    /**
     * Release camera access
     */
    fun releaseCamera() {
        Log.i(TAG, "Releasing camera access")
        currentCameraDevice.get()?.close()
        currentCameraDevice.set(null)
        isManaging.set(false)
    }
    
    /**
     * Check if we currently have camera access
     */
    fun hasCameraAccess(): Boolean {
        return currentCameraDevice.get() != null
    }
    
    /**
     * Get current camera device (for integration with existing capture logic)
     */
    fun getCurrentCameraDevice(): CameraDevice? {
        return currentCameraDevice.get()
    }
}