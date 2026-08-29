package com.childmonitor.capture

import android.app.Service
import android.app.Service.START_NOT_STICKY
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log

/**
 * Keeps typed foreground services from taking down the process when promotion,
 * teardown, or onStartCommand work fails.
 *
 * Android kills the app if [Service.startForegroundService] is followed by no
 * [Service.startForeground] within ~5 seconds — failure paths must still promote
 * best-effort, then [abandonStartup].
 */
object ForegroundServiceCrashGuard {

    private const val TAG = "FgsCrashGuard"

    fun runOnStartCommand(serviceTag: String, service: Service, block: () -> Int): Int {
        return try {
            block()
        } catch (t: Throwable) {
            Log.e(serviceTag, "onStartCommand crashed — abandoning FGS startup", t)
            abandonStartup(service, serviceTag, "onStartCommand_exception:${t.javaClass.simpleName}")
            START_NOT_STICKY
        }
    }

    fun promoteLegacy(service: Service, notificationId: Int, notification: android.app.Notification): Boolean {
        return try {
            service.startForeground(notificationId, notification)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "legacy startForeground failed service=${service.javaClass.simpleName}", t)
            false
        }
    }

    fun promoteBestEffort(
        service: Service,
        notificationId: Int,
        notification: android.app.Notification,
        preferredTypes: IntArray,
        allowUntypedFallback: Boolean = true,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return promoteLegacy(service, notificationId, notification)
        }
        for (type in preferredTypes) {
            try {
                service.startForeground(notificationId, notification, type)
                return true
            } catch (t: Throwable) {
                Log.w(
                    TAG,
                    "startForeground failed service=${service.javaClass.simpleName} type=$type",
                    t,
                )
            }
        }
        // Camera/mic services declare typed FGS in the manifest. An untyped
        // startForeground() still resolves to that while-in-use type and fails the
        // same background eligibility check. Skip that dead end.
        if (!allowUntypedFallback) {
            return false
        }
        return promoteLegacy(service, notificationId, notification)
    }

    fun cameraPromotionTypes(hasCameraPermission: Boolean): IntArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return IntArray(0)
        }
        // Never fall through to microphone — CameraStream FGS must not require RECORD_AUDIO.
        return if (hasCameraPermission) {
            intArrayOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            IntArray(0)
        }
    }

    fun audioPromotionTypes(): IntArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return IntArray(0)
        }
        return intArrayOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    fun abandonStartup(service: Service, serviceTag: String, reason: String) {
        Log.w(serviceTag, "abandonStartup reason=$reason")
        safeRun(serviceTag, "abandon_stopForeground:$reason") {
            service.stopForeground(STOP_FOREGROUND_REMOVE)
        }
        safeRun(serviceTag, "abandon_stopSelf:$reason") {
            service.stopSelf()
        }
    }

    fun safeRun(serviceTag: String, reason: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(serviceTag, "safeRun failed reason=$reason", t)
        }
    }
}
