package com.childmonitor.native

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.childmonitor.AccessibilityHealthChecker
import com.childmonitor.AppBlockingAccessibilityService
import com.childmonitor.MainActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.childmonitor.LocationReader
import com.childmonitor.MonitoringForegroundService
import com.childmonitor.MonitoringPrefs
import com.childmonitor.monitoring.MonitoringServiceLauncher
import com.childmonitor.NativeSyncUploader
import com.childmonitor.sync.NativeStompOwner
import com.childmonitor.sync.NativeStreamingCoordinator
import com.childmonitor.NotificationStore
import com.childmonitor.PolicySyncManager
import com.childmonitor.permissions.PermissionSettingsLauncher
import com.childmonitor.TamperProtectionManager
import com.childmonitor.UsageStatsReader
import com.childmonitor.accessibility.ScreenCaptureAutoApprove
import com.childmonitor.capture.DevicePlaybackCaptureSession
import com.childmonitor.capture.DevicePlaybackAudioCapturer
import com.childmonitor.capture.PlaybackCaptureInjector
import com.childmonitor.capture.JsViewerPresenceTracker
import com.childmonitor.capture.HiddenAudioCapture
import com.childmonitor.capture.CameraAccessManager
import com.childmonitor.capture.HiddenCameraCapture
import com.childmonitor.capture.HiddenScreenCapture
import com.childmonitor.capture.MediaProjectionConsentHelper
import com.childmonitor.capture.MediaProjectionSessionKeeper
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.capture.NativeStreamPublisher
import com.childmonitor.capture.ScreenCaptureController
import com.childmonitor.capture.ScreenCaptureForegroundService
import com.childmonitor.capture.StreamLifecycleLogger
import com.childmonitor.capture.StreamSessionRegistry
import com.childmonitor.capture.StreamTeardownCoordinator
import com.childmonitor.telemetry.StreamLifecycleTelemetry
import com.childmonitor.deviceowner.DeviceOwnerManager
import com.childmonitor.deviceowner.MediaProjectionConsentActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.modules.core.PermissionListener
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.debug.PermissionDebugLogger
import com.childmonitor.security.DeviceTrustSignalsCollector
import com.childmonitor.security.PlayIntegrityHelper

class ChildMonitorNativeModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private lateinit var chainAdvancePromise: ChainAdvancePromise

    init {
        Companion.eventContext = reactContext
        chainAdvancePromise = ChainAdvancePromise { finishRuntimeChainStep() }
    }

    private var cameraMicPromise: Promise? = null
    private var singlePermissionPromise: Promise? = null
    private var singlePermissionGranted: (() -> Boolean)? = null
    private val cameraMicPermissionListener = PermissionListener { requestCode, _, _ ->
        if (requestCode == CAMERA_MIC_REQUEST_CODE) {
            cameraMicPromise?.resolve(hasCameraAndMic())
            cameraMicPromise = null
            true
        } else {
            false
        }
    }
    private val singlePermissionListener = PermissionListener { requestCode, _, _ ->
        if (
            requestCode == CAMERA_REQUEST_CODE ||
            requestCode == MIC_REQUEST_CODE ||
            requestCode == POST_NOTIFICATIONS_REQUEST_CODE ||
            requestCode == LOCATION_REQUEST_CODE
        ) {
            val pending = singlePermissionPromise
            val granted = singlePermissionGranted?.invoke() ?: false
            singlePermissionPromise = null
            singlePermissionGranted = null
            pending?.resolve(granted)
            if (inRuntimeChain && pending !== chainAdvancePromise) {
                finishRuntimeChainStep()
            }
            true
        } else {
            false
        }
    }

    private var runtimeChainPromise: Promise? = null
    private var runtimeChainIndex = 0
    private val runtimeChainSteps = mutableListOf<() -> Boolean>()
    private var inRuntimeChain = false

    private fun finishRuntimeChainStep() {
        if (!inRuntimeChain) return
        runtimeChainIndex++
        runtimeChainAdvance()
    }

    override fun getName(): String = "ChildMonitorNative"

    @ReactMethod
    fun getAndroidId(promise: Promise) {
        try {
            val id = Settings.Secure.getString(reactContext.contentResolver, Settings.Secure.ANDROID_ID)
            promise.resolve(id ?: "")
        } catch (e: Exception) {
            promise.reject("ANDROID_ID_ERROR", e)
        }
    }

    @ReactMethod
    fun setJsStreamingListenerActive(active: Boolean, promise: Promise) {
        try {
            StreamLifecycleLogger.log(
                if (active) "JS_LISTENER_ACTIVE" else "JS_LISTENER_INACTIVE",
                "reactActive=${canDeliverStreamToJs()}",
            )
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "setJsStreamingListenerActive from JS",
                "active=$active",
            )
            NativeStreamingCoordinator.setJsListenerActive(active)
            if (active) {
                NativeStompOwner.ensureConnected(reactContext.applicationContext)
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("STREAMING_LISTENER_ERROR", e)
        }
    }

    @ReactMethod
    fun notifyStreamTeardownComplete(detail: String?, promise: Promise) {
        try {
            StreamTeardownCoordinator.onJsTeardownComplete(reactContext.applicationContext, detail)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("TEARDOWN_COMPLETE_ERROR", e)
        }
    }

    @ReactMethod
    fun reportJsIceConnectionState(state: String, promise: Promise) {
        try {
            JsViewerPresenceTracker.onIceState(state)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ICE_STATE_ERROR", e)
        }
    }

    @ReactMethod
    fun logStreamLifecycle(stage: String, detail: String?, promise: Promise) {
        try {
            StreamLifecycleLogger.log(stage, detail)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("LIFECYCLE_LOG_ERROR", e)
        }
    }

    @ReactMethod
    fun acquireJsStreamSession(sessionId: String, roomId: String, promise: Promise) {
        try {
            val ok = StreamSessionRegistry.tryAcquire(
                StreamSessionRegistry.Owner.JS_HIDDEN,
                sessionId,
                roomId,
            )
            promise.resolve(ok)
        } catch (e: Exception) {
            promise.reject("SESSION_ACQUIRE_ERROR", e)
        }
    }

    @ReactMethod
    fun markJsStreamSessionActive(sessionId: String, promise: Promise) {
        try {
            if (StreamSessionRegistry.activeSessionId() == sessionId) {
                StreamSessionRegistry.markActive()
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SESSION_ACTIVE_ERROR", e)
        }
    }

    @ReactMethod
    fun releaseJsStreamSession(promise: Promise) {
        try {
            StreamSessionRegistry.release(StreamSessionRegistry.Owner.JS_HIDDEN)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SESSION_RELEASE_ERROR", e)
        }
    }

    @ReactMethod
    fun getStreamSessionSnapshot(promise: Promise) {
        try {
            val snap = StreamSessionRegistry.snapshot()
            val map = Arguments.createMap()
            for ((key, value) in snap) {
                when (value) {
                    is String -> map.putString(key, value)
                    is Long -> map.putDouble(key, value.toDouble())
                    is Int -> map.putInt(key, value)
                    is Boolean -> map.putBoolean(key, value)
                    null -> map.putNull(key)
                    else -> map.putString(key, value.toString())
                }
            }
            map.putBoolean("nativePublisherRunning", NativeStreamPublisher.isRunning())
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("SESSION_SNAPSHOT_ERROR", e)
        }
    }

    @ReactMethod
    fun subscribeNativeSignalingRoom(roomId: String, promise: Promise) {
        try {
            NativeStompOwner.subscribeSignalingRoom(roomId)
            NativeStompOwner.ensureConnected(reactContext.applicationContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SIGNALING_SUBSCRIBE_ERROR", e)
        }
    }

    @ReactMethod
    fun unsubscribeNativeSignalingRoom(roomId: String, promise: Promise) {
        try {
            NativeStompOwner.unsubscribeSignalingRoom(roomId)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SIGNALING_UNSUBSCRIBE_ERROR", e)
        }
    }

    @ReactMethod
    fun ensureNativeStompConnected(promise: Promise) {
        try {
            NativeStompOwner.ensureConnected(reactContext.applicationContext)
            promise.resolve(NativeStompOwner.isConnected())
        } catch (e: Exception) {
            promise.reject("STOMP_ENSURE_ERROR", e)
        }
    }

    @ReactMethod
    fun getStompOwnershipHealth(promise: Promise) {
        try {
            val map = Arguments.createMap()
            for ((key, value) in NativeStompOwner.healthSnapshot()) {
                when (value) {
                    is Boolean -> map.putBoolean(key, value)
                    is String -> map.putString(key, value)
                    is List<*> -> map.putArray(key, Arguments.fromList(value))
                    is Number -> map.putDouble(key, value.toDouble())
                    else -> map.putString(key, value?.toString())
                }
            }
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("STOMP_HEALTH_ERROR", e)
        }
    }

    @ReactMethod
    fun updateApiBaseUrl(apiBaseUrl: String, promise: Promise) {
        try {
            val normalized = apiBaseUrl.trimEnd('/')
            val previous = MonitoringPrefs.apiBaseUrl(reactContext)?.trimEnd('/')
            MonitoringPrefs.updateApiBaseUrl(reactContext, normalized)
            // Reconnecting STOMP on every heartbeat/poll (same URL) tears down
            // signaling subscriptions mid-stream — only reconnect when the base changes.
            if (previous != normalized) {
                NativeStreamingCoordinator.onApiBaseUrlChanged(reactContext)
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("UPDATE_API_URL_ERROR", e)
        }
    }

    /** TurboModule-safe: 3 string args + promise (parent name via [saveParentDisplayName]). */
    @ReactMethod
    fun saveSyncConfig(
        apiBaseUrl: String,
        deviceToken: String,
        deviceHmacKey: String,
        promise: Promise,
    ) {
        try {
            val hmac = deviceHmacKey.takeIf { it.isNotBlank() }
            MonitoringPrefs.saveSyncConfig(
                reactContext,
                apiBaseUrl,
                deviceToken,
                hmac,
                null,
            )
            MonitoringServiceLauncher.onDevicePaired(reactContext.applicationContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SAVE_CONFIG_ERROR", e)
        }
    }

    @ReactMethod
    fun saveParentDisplayName(parentDisplayName: String, promise: Promise) {
        try {
            MonitoringPrefs.setParentDisplayName(
                reactContext,
                parentDisplayName.takeIf { it.isNotBlank() },
            )
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SAVE_PARENT_NAME_ERROR", e)
        }
    }

    @ReactMethod
    fun getDeviceInfo(promise: Promise) {
        try {
            val map = Arguments.createMap()
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            map.putString("deviceModel", Build.MODEL)
            map.putString("deviceName", "$manufacturer ${Build.MODEL}".trim())
            map.putString("osVersion", Build.VERSION.RELEASE)
            val versionName = try {
                reactContext.packageManager
                    .getPackageInfo(reactContext.packageName, 0).versionName ?: "1.0.0"
            } catch (_: Exception) {
                "1.0.0"
            }
            map.putString("appVersion", versionName)
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("DEVICE_INFO_ERROR", e)
        }
    }

    @ReactMethod
    fun getParentDisplayName(promise: Promise) {
        promise.resolve(MonitoringPrefs.parentDisplayName(reactContext))
    }

    @ReactMethod
    fun setMonitoringAudioMode(enabled: Boolean, promise: Promise) {
        try {
            val audioManager = reactContext.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            if (enabled) {
                // Communication mode enables hardware AEC on the mic path; keep speaker off.
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            } else {
                audioManager.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("AUDIO_MODE_ERROR", e)
        }
    }

    @ReactMethod
    fun getStoredSyncConfig(promise: Promise) {
        try {
            val map = Arguments.createMap()
            map.putBoolean("isPaired", MonitoringPrefs.isPaired(reactContext))
            map.putString("deviceToken", MonitoringPrefs.deviceToken(reactContext))
            map.putString("deviceHmacKey", MonitoringPrefs.deviceHmacKey(reactContext))
            map.putString("parentDisplayName", MonitoringPrefs.parentDisplayName(reactContext))
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("LOAD_CONFIG_ERROR", e)
        }
    }

    @ReactMethod
    fun startForegroundService(promise: Promise) {
        try {
            MonitoringServiceLauncher.requestMonitoring(
                reactContext.applicationContext,
                MonitoringServiceLauncher.Reason.USER,
                force = true,
            )
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SERVICE_ERROR", e)
        }
    }

    @ReactMethod
    fun stopForegroundService(promise: Promise) {
        try {
            reactContext.stopService(Intent(reactContext, MonitoringForegroundService::class.java))
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SERVICE_ERROR", e)
        }
    }

    @ReactMethod
    fun runSyncNow(promise: Promise) {
        Thread {
            try {
                val ok = NativeSyncUploader.syncAll(reactContext)
                promise.resolve(ok)
            } catch (e: Exception) {
                promise.reject("SYNC_ERROR", e)
            }
        }.start()
    }

    @ReactMethod
    fun isNotificationListenerEnabled(promise: Promise) {
        val enabled = Settings.Secure.getString(
            reactContext.contentResolver,
            "enabled_notification_listeners",
        )?.contains(reactContext.packageName) == true
        promise.resolve(enabled)
    }

    @ReactMethod
    fun openNotificationListenerSettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactContext.startActivity(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SETTINGS_ERROR", e)
        }
    }

    @ReactMethod
    fun isUsageStatsGranted(promise: Promise) {
        promise.resolve(UsageStatsReader.hasPermission(reactContext))
    }

    @ReactMethod
    fun openUsageStatsSettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactContext.startActivity(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SETTINGS_ERROR", e)
        }
    }

    @ReactMethod
    fun isCameraGranted(promise: Promise) {
        promise.resolve(
            ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    @ReactMethod
    fun isMicGranted(promise: Promise) {
        promise.resolve(
            ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    @ReactMethod
    fun requestCameraAndMicPermissions(promise: Promise) {
        val activity = currentActivity
        if (activity == null) {
            promise.resolve(hasCameraAndMic())
            return
        }
        if (hasCameraAndMic()) {
            promise.resolve(true)
            return
        }
        cameraMicPromise = promise
        val permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
        )
        val permissionAware = activity as? com.facebook.react.modules.core.PermissionAwareActivity
        if (permissionAware != null) {
            permissionAware.requestPermissions(permissions, CAMERA_MIC_REQUEST_CODE, cameraMicPermissionListener)
        } else {
            ActivityCompat.requestPermissions(activity, permissions, CAMERA_MIC_REQUEST_CODE)
            Handler(Looper.getMainLooper()).postDelayed({
                if (cameraMicPromise != null) {
                    cameraMicPromise?.resolve(hasCameraAndMic())
                    cameraMicPromise = null
                }
            }, 1500)
        }
    }

    @ReactMethod
    fun requestCameraPermission(promise: Promise) {
        requestSingleRuntimePermission(
            android.Manifest.permission.CAMERA,
            CAMERA_REQUEST_CODE,
            promise,
        ) {
            ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @ReactMethod
    fun requestMicrophonePermission(promise: Promise) {
        requestSingleRuntimePermission(
            android.Manifest.permission.RECORD_AUDIO,
            MIC_REQUEST_CODE,
            promise,
        ) {
            ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @ReactMethod
    fun isPostNotificationsGranted(promise: Promise) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            promise.resolve(true)
            return
        }
        promise.resolve(
            ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    @ReactMethod
    fun requestPostNotificationsPermission(promise: Promise) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            promise.resolve(true)
            return
        }
        requestSingleRuntimePermission(
            android.Manifest.permission.POST_NOTIFICATIONS,
            POST_NOTIFICATIONS_REQUEST_CODE,
            promise,
        ) {
            ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestSingleRuntimePermission(
        permission: String,
        requestCode: Int,
        promise: Promise,
        isGranted: () -> Boolean,
    ) {
        if (isGranted()) {
            promise.resolve(true)
            if (inRuntimeChain && promise !== chainAdvancePromise) {
                finishRuntimeChainStep()
            }
            return
        }
        val activity = currentActivity
        if (activity == null) {
            promise.resolve(isGranted())
            return
        }
        singlePermissionPromise = promise
        singlePermissionGranted = isGranted
        val permissionAware = activity as? com.facebook.react.modules.core.PermissionAwareActivity
        if (permissionAware != null) {
            permissionAware.requestPermissions(arrayOf(permission), requestCode, singlePermissionListener)
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
            Handler(Looper.getMainLooper()).postDelayed({
                if (singlePermissionPromise != null) {
                    val pending = singlePermissionPromise
                    val granted = isGranted()
                    singlePermissionPromise = null
                    singlePermissionGranted = null
                    pending?.resolve(granted)
                    if (inRuntimeChain && pending !== chainAdvancePromise) {
                        finishRuntimeChainStep()
                    }
                }
            }, 1500)
        }
    }

    private fun hasCameraAndMic(): Boolean {
        val camera = ContextCompat.checkSelfPermission(
            reactContext,
            android.Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(
            reactContext,
            android.Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        return camera && mic
    }

    @ReactMethod
    fun isLocationGranted(promise: Promise) {
        promise.resolve(LocationReader.hasPermission(reactContext))
    }

    @ReactMethod
    fun requestLocationPermission(promise: Promise) {
        requestLocationPermissionInternal(promise)
    }

    private fun requestLocationPermissionInternal(promise: Promise) {
        if (LocationReader.hasPermission(reactContext)) {
            promise.resolve(true)
            if (inRuntimeChain && promise !== chainAdvancePromise) {
                finishRuntimeChainStep()
            }
            return
        }
        val activity = currentActivity
        if (activity == null) {
            promise.resolve(LocationReader.hasPermission(reactContext))
            return
        }
        singlePermissionPromise = promise
        singlePermissionGranted = { LocationReader.hasPermission(reactContext) }
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        val permissionAware = activity as? com.facebook.react.modules.core.PermissionAwareActivity
        if (permissionAware != null) {
            permissionAware.requestPermissions(permissions, LOCATION_REQUEST_CODE, singlePermissionListener)
        } else {
            ActivityCompat.requestPermissions(activity, permissions, LOCATION_REQUEST_CODE)
            Handler(Looper.getMainLooper()).postDelayed({
                if (singlePermissionPromise != null) {
                    val pending = singlePermissionPromise
                    val granted = LocationReader.hasPermission(reactContext)
                    singlePermissionPromise = null
                    singlePermissionGranted = null
                    pending?.resolve(granted)
                    if (inRuntimeChain && pending !== chainAdvancePromise) {
                        finishRuntimeChainStep()
                    }
                }
            }, 1500)
        }
    }

    @ReactMethod
    fun requestAllRuntimePermissions(promise: Promise) {
        val activity = currentActivity
        if (activity == null) {
            promise.resolve(allRuntimePermissionsGranted())
            return
        }
        runtimeChainPromise = promise
        runtimeChainIndex = 0
        inRuntimeChain = true
        runtimeChainSteps.clear()
        runtimeChainSteps.add { LocationReader.hasPermission(reactContext) }
        runtimeChainSteps.add {
            ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        }
        runtimeChainSteps.add {
            ContextCompat.checkSelfPermission(
                reactContext,
                android.Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimeChainSteps.add {
                ContextCompat.checkSelfPermission(
                    reactContext,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        runtimeChainAdvance()
    }

    private fun allRuntimePermissionsGranted(): Boolean {
        if (!LocationReader.hasPermission(reactContext)) return false
        if (ContextCompat.checkSelfPermission(reactContext, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (ContextCompat.checkSelfPermission(reactContext, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(reactContext, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    private fun runtimeChainAdvance() {
        val chainPromise = runtimeChainPromise ?: return
        while (runtimeChainIndex < runtimeChainSteps.size && runtimeChainSteps[runtimeChainIndex]()) {
            runtimeChainIndex++
        }
        if (runtimeChainIndex >= runtimeChainSteps.size) {
            inRuntimeChain = false
            runtimeChainPromise = null
            runtimeChainIndex = 0
            chainPromise.resolve(allRuntimePermissionsGranted())
            return
        }
        when (runtimeChainIndex) {
            0 -> requestLocationPermissionInternal(chainAdvancePromise)
            1 -> requestMicrophonePermission(chainAdvancePromise)
            2 -> requestCameraPermission(chainAdvancePromise)
            3 -> requestPostNotificationsPermission(chainAdvancePromise)
            else -> {
                runtimeChainIndex++
                runtimeChainAdvance()
            }
        }
    }

    @ReactMethod
    fun getPendingNotifications(promise: Promise) {
        try {
            val arr: WritableArray = Arguments.createArray()
            NotificationStore.getPending(reactContext).forEach { n ->
                val map: WritableMap = Arguments.createMap()
                map.putString("localId", n.localId)
                map.putString("packageName", n.packageName)
                map.putString("title", n.title)
                map.putString("body", n.body)
                map.putDouble("postedAt", n.postedAtMs.toDouble())
                arr.pushMap(map)
            }
            promise.resolve(arr)
        } catch (e: Exception) {
            promise.reject("NOTIFICATIONS_ERROR", e)
        }
    }

    @ReactMethod
    fun clearPendingNotifications(localIds: com.facebook.react.bridge.ReadableArray, promise: Promise) {
        try {
            val ids = mutableListOf<String>()
            for (i in 0 until localIds.size()) {
                localIds.getString(i)?.let { ids.add(it) }
            }
            NotificationStore.markSynced(reactContext, ids)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("NOTIFICATIONS_ERROR", e)
        }
    }

    @ReactMethod
    fun getUsageStats(promise: Promise) {
        try {
            // Full day snapshot — matches NativeSyncUploader so JS ingest stays complete.
            val since = UsageStatsReader.startOfTodayMs()
            val arr: WritableArray = Arguments.createArray()
            UsageStatsReader.readSince(reactContext, since).forEach { entry ->
                val map: WritableMap = Arguments.createMap()
                map.putString("packageName", entry.packageName)
                map.putDouble("usageMs", entry.usageMs.toDouble())
                map.putString("recordedAt", entry.recordedAt.toString())
                arr.pushMap(map)
            }
            promise.resolve(arr)
        } catch (e: Exception) {
            promise.reject("USAGE_ERROR", e)
        }
    }

    @ReactMethod
    fun getCurrentLocation(promise: Promise) {
        try {
            if (!LocationReader.hasPermission(reactContext)) {
                promise.resolve(null)
                return
            }
            val loc = LocationReader.getLastKnown(reactContext)
            if (loc == null) {
                promise.resolve(null)
                return
            }
            val map: WritableMap = Arguments.createMap()
            map.putDouble("latitude", loc.latitude)
            map.putDouble("longitude", loc.longitude)
            if (loc.accuracyM != null) map.putDouble("accuracyM", loc.accuracyM.toDouble())
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("LOCATION_ERROR", e)
        }
    }

    @ReactMethod
    fun armScreenCaptureAutoApprove(promise: Promise) {
        try {
            ScreenCaptureAutoApprove.armForConsent(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("AUTO_APPROVE_ERROR", e)
        }
    }

    @ReactMethod
    fun disarmScreenCaptureAutoApprove(promise: Promise) {
        try {
            ScreenCaptureAutoApprove.disarm(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("DISARM_AUTO_APPROVE_ERROR", e)
        }
    }

    @ReactMethod
    fun setPermissionSetupInProgress(inProgress: Boolean, promise: Promise) {
        try {
            MonitoringPrefs.setPermissionSetupInProgress(reactContext, inProgress)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SETUP_FLAG_ERROR", e)
        }
    }

    @ReactMethod
    fun setSkipAutoRelockUntil(untilEpochMs: Double, promise: Promise) {
        try {
            MonitoringPrefs.setSkipAutoRelockUntilMs(reactContext, untilEpochMs.toLong())
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SKIP_RELOCK_ERROR", e)
        }
    }

    @ReactMethod
    fun isSkipAutoRelockActive(promise: Promise) {
        try {
            promise.resolve(MonitoringPrefs.isSkipAutoRelockActive(reactContext))
        } catch (e: Exception) {
            promise.reject("SKIP_RELOCK_CHECK_ERROR", e)
        }
    }

    @ReactMethod
    fun isScreenCaptureAutoApproveReady(promise: Promise) {
        try {
            promise.resolve(ScreenCaptureAutoApprove.isReady(reactContext))
        } catch (e: Exception) {
            promise.reject("AUTO_APPROVE_READY_ERROR", e)
        }
    }

    @ReactMethod
    fun isAccessibilityEnabled(promise: Promise) {
        promise.resolve(AccessibilityHealthChecker.isAccessibilityEnabled(reactContext))
    }

    @ReactMethod
    fun isAccessibilityServiceConnected(promise: Promise) {
        promise.resolve(AccessibilityHealthChecker.isServiceConnected())
    }

    @ReactMethod
    fun openAccessibilitySettings(promise: Promise) {
        PermissionDebugLogger.enter("ChildMonitorNativeModule.openAccessibilitySettings")
        try {
            val activity = currentActivity
            if (PermissionSettingsLauncher.openAccessibility(reactContext, activity)) {
                PermissionDebugLogger.exit("ChildMonitorNativeModule.openAccessibilitySettings")
                promise.resolve(null)
                return
            }
            val fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (AppBlockingAccessibilityService.tryStartActivity(fallback)) {
                PermissionDebugLogger.exit("ChildMonitorNativeModule.openAccessibilitySettings", "a11y-fallback")
                promise.resolve(null)
                return
            }
            promise.reject("SETTINGS_ERROR", "Could not open accessibility settings on this device")
        } catch (e: Exception) {
            promise.reject("SETTINGS_ERROR", e)
        }
    }

    @ReactMethod
    fun isOverlayGranted(promise: Promise) {
        promise.resolve(Settings.canDrawOverlays(reactContext))
    }

    @ReactMethod
    fun isFullScreenIntentGranted(promise: Promise) {
        try {
            promise.resolve(com.childmonitor.capture.FullScreenIntentHelper.isGranted(reactContext))
        } catch (e: Exception) {
            promise.reject("FULL_SCREEN_INTENT_CHECK_ERROR", e)
        }
    }

    @ReactMethod
    fun isFullScreenIntentRequired(promise: Promise) {
        promise.resolve(com.childmonitor.capture.FullScreenIntentHelper.isRequired())
    }

    @ReactMethod
    fun getFullScreenIntentStatus(promise: Promise) {
        try {
            val helper = com.childmonitor.capture.FullScreenIntentHelper
            val status = helper.validate(reactContext)
            val map = Arguments.createMap()
            map.putString("status", status.name)
            map.putBoolean("required", helper.isRequired())
            map.putBoolean("granted", status != com.childmonitor.capture.FullScreenIntentHelper.Status.DENIED)
            map.putBoolean("needsRecovery", helper.needsRecovery(reactContext))
            map.putBoolean("wasRevoked", helper.wasRevoked(reactContext))
            map.putInt("api", Build.VERSION.SDK_INT)
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("FULL_SCREEN_INTENT_STATUS_ERROR", e)
        }
    }

    @ReactMethod
    fun openFullScreenIntentSettings(promise: Promise) {
        try {
            com.childmonitor.capture.FullScreenIntentHelper.openSettings(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("FULL_SCREEN_INTENT_SETTINGS_ERROR", e)
        }
    }

    @ReactMethod
    fun openOverlaySettings(promise: Promise) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${reactContext.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactContext.startActivity(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("OVERLAY_SETTINGS_ERROR", e)
        }
    }

    @ReactMethod
    fun isBatteryOptimizationExempt(promise: Promise) {
        val pm = reactContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        promise.resolve(pm.isIgnoringBatteryOptimizations(reactContext.packageName))
    }

    @ReactMethod
    fun openBatteryOptimizationSettings(promise: Promise) {
        try {
            val intent = com.childmonitor.oem.OemCompatibilityManager
                .buildBatteryOptimizationIntent(reactContext)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reactContext.startActivity(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${reactContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                reactContext.startActivity(fallback)
                promise.resolve(null)
            } catch (e2: Exception) {
                promise.reject("BATTERY_SETTINGS_ERROR", e2)
            }
        }
    }

    @ReactMethod
    fun isXiaomiDevice(promise: Promise) {
        promise.resolve(com.childmonitor.oem.OemSetupValidator.isXiaomiFamily())
    }

    @ReactMethod
    fun getOemVendor(promise: Promise) {
        promise.resolve(com.childmonitor.oem.OemSetupValidator.vendor().id)
    }

    @ReactMethod
    fun getOemVendorDisplayName(promise: Promise) {
        promise.resolve(com.childmonitor.oem.OemSetupValidator.vendor().displayName)
    }

    @ReactMethod
    fun requiresOemBackgroundSetup(promise: Promise) {
        promise.resolve(com.childmonitor.oem.OemSetupValidator.requiresAutostartPrompt())
    }

    @ReactMethod
    fun getDeviceReadinessScore(promise: Promise) {
        promise.resolve(com.childmonitor.oem.OemSetupValidator.computeReadinessScore(reactContext))
    }

    @ReactMethod
    fun getOemDiagnostics(promise: Promise) {
        val map = Arguments.createMap()
        for ((key, value) in com.childmonitor.oem.OemDiagnosticsCollector.collect(reactContext)) {
            when (value) {
                is Boolean -> map.putBoolean(key, value)
                is Int -> map.putInt(key, value)
                is Long -> map.putDouble(key, value.toDouble())
                is Double -> map.putDouble(key, value)
                is String -> map.putString(key, value)
                is List<*> -> map.putArray(key, Arguments.fromList(value))
                else -> map.putString(key, value?.toString())
            }
        }
        promise.resolve(map)
    }

    @ReactMethod
    fun isOemSetupComplete(promise: Promise) {
        promise.resolve(com.childmonitor.oem.OemSetupValidator.isOemSetupComplete(reactContext))
    }

    @ReactMethod
    fun getOemSetupWizard(promise: Promise) {
        try {
            val steps = com.childmonitor.oem.OemSetupWizard.steps(reactContext)
            val arr = Arguments.createArray()
            for (step in steps) {
                val map = Arguments.createMap()
                map.putString("id", step.id)
                map.putString("title", step.title)
                map.putString("description", step.description)
                map.putBoolean("autoVerifiable", step.autoVerifiable)
                map.putBoolean("complete", com.childmonitor.oem.OemSetupWizard.isStepComplete(reactContext, step.id))
                val instructions = Arguments.createArray()
                for (line in step.instructions) {
                    instructions.pushString(line)
                }
                map.putArray("instructions", instructions)
                arr.pushMap(map)
            }
            val result = Arguments.createMap()
            result.putArray("steps", arr)
            result.putBoolean(
                "complete",
                com.childmonitor.oem.OemSetupWizard.isWizardComplete(reactContext),
            )
            result.putString("vendorName", com.childmonitor.oem.OemSetupValidator.vendor().displayName)
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("OEM_WIZARD_ERROR", e)
        }
    }

    @ReactMethod
    fun openOemSetupWizardStep(stepId: String, promise: Promise) {
        try {
            com.childmonitor.oem.OemSetupWizard.openStep(reactContext, stepId)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("OEM_WIZARD_OPEN_ERROR", e)
        }
    }

    @ReactMethod
    fun confirmOemSetupWizardStep(stepId: String, promise: Promise) {
        try {
            com.childmonitor.oem.OemSetupWizard.confirmManualStep(reactContext, stepId)
            promise.resolve(com.childmonitor.oem.OemSetupWizard.isStepComplete(reactContext, stepId))
        } catch (e: Exception) {
            promise.reject("OEM_WIZARD_CONFIRM_ERROR", e)
        }
    }

    @ReactMethod
    fun isMiuiAutostartPromptDone(promise: Promise) {
        promise.resolve(com.childmonitor.oem.OemSetupValidator.isAutostartPromptCompleted(reactContext))
    }

    @ReactMethod
    fun isOemAutostartPromptDone(promise: Promise) {
        promise.resolve(com.childmonitor.oem.OemSetupValidator.isAutostartPromptCompleted(reactContext))
    }

    @ReactMethod
    fun openMiuiAutostartSettings(promise: Promise) {
        try {
            com.childmonitor.oem.OemSetupValidator.openOemBackgroundSettings(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("OEM_BACKGROUND_ERROR", e)
        }
    }

    @ReactMethod
    fun openOemBackgroundSettings(promise: Promise) {
        try {
            val ctx = currentActivity ?: reactApplicationContext
            com.childmonitor.oem.OemSetupValidator.openOemBackgroundSettings(ctx)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("OEM_BACKGROUND_ERROR", e)
        }
    }

    @ReactMethod
    fun markOemAutostartPromptDone(promise: Promise) {
        try {
            com.childmonitor.oem.OemSetupValidator.markAutostartPromptCompleted(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("OEM_AUTOSTART_MARK_ERROR", e)
        }
    }

    @ReactMethod
    fun clearOemAutostartPromptDone(promise: Promise) {
        try {
            com.childmonitor.oem.OemSetupValidator.clearAutostartPromptCompleted(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("OEM_AUTOSTART_CLEAR_ERROR", e)
        }
    }

    @ReactMethod
    fun syncPoliciesNow(promise: Promise) {
        Thread {
            try {
                promise.resolve(PolicySyncManager.syncPolicies(reactContext))
            } catch (e: Exception) {
                promise.reject("POLICY_SYNC_ERROR", e)
            }
        }.start()
    }

    @ReactMethod
    fun isDeviceAdminActive(promise: Promise) {
        promise.resolve(TamperProtectionManager.isDeviceAdminActive(reactContext))
    }

    @ReactMethod
    fun requestDeviceAdmin(promise: Promise) {
        try {
            if (TamperProtectionManager.isDeviceAdminActive(reactContext)) {
                promise.resolve(null)
                return
            }
            val activity = currentActivity
            if (activity == null) {
                promise.reject(
                    "NO_ACTIVITY",
                    "Bring the app to the foreground, then try again.",
                )
                return
            }
            // Grace starts before Settings opens — wizard blur clears setup-in-progress.
            MonitoringPrefs.beginDeviceAdminSetupGrace(reactContext)
            activity.startActivity(
                TamperProtectionManager.buildEnableAdminIntent(reactContext, fromActivity = true),
            )
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("DEVICE_ADMIN_ERROR", e)
        }
    }

    @ReactMethod
    fun openDeviceAdminSettings(promise: Promise) {
        try {
            val activity = currentActivity
            if (!PermissionSettingsLauncher.openDeviceAdmin(reactContext, activity)) {
                promise.reject("SETTINGS_ERROR", "Could not open device admin settings on this device")
                return
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SETTINGS_ERROR", e)
        }
    }

    @ReactMethod
    fun isLauncherHidden(promise: Promise) {
        promise.resolve(TamperProtectionManager.isLauncherHidden(reactContext))
    }

    @ReactMethod
    fun setHideLauncher(hidden: Boolean, promise: Promise) {
        try {
            val ok = TamperProtectionManager.setHideLauncher(reactContext, hidden)
            if (ok) {
                MonitoringPrefs.setHideLauncherEnabled(reactContext, hidden)
            }
            promise.resolve(ok)
        } catch (e: Exception) {
            promise.reject("LAUNCHER_HIDE_ERROR", e)
        }
    }

    @ReactMethod
    fun isAccessibilityTamperProtectionEnabled(promise: Promise) {
        promise.resolve(MonitoringPrefs.isAccessibilityTamperProtectionEnabled(reactContext))
    }

    @ReactMethod
    fun setAccessibilityTamperProtectionEnabled(enabled: Boolean, promise: Promise) {
        try {
            MonitoringPrefs.setAccessibilityTamperProtectionEnabled(reactContext, enabled)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ACCESSIBILITY_TAMPER_ERROR", e)
        }
    }

    @ReactMethod
    fun isDeviceAdminTamperProtectionEnabled(promise: Promise) {
        promise.resolve(MonitoringPrefs.isTamperProtectionEnabled(reactContext))
    }

    @ReactMethod
    fun setDeviceAdminTamperProtectionEnabled(enabled: Boolean, promise: Promise) {
        try {
            MonitoringPrefs.setTamperProtectionEnabled(reactContext, enabled)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("DEVICE_ADMIN_TAMPER_ERROR", e)
        }
    }

    @ReactMethod
    fun applyTamperProtection(promise: Promise) {
        try {
            TamperProtectionManager.applyStoredTamperSettings(reactContext)
            if (DeviceOwnerManager.isDeviceOwner(reactContext)) {
                DeviceOwnerManager.applyOwnerPolicies(reactContext)
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("TAMPER_ERROR", e)
        }
    }

    @ReactMethod
    fun setTamperUnlockUntil(untilEpochMs: Double, promise: Promise) {
        try {
            MonitoringPrefs.setTamperUnlockUntilMs(reactContext, untilEpochMs.toLong())
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("TAMPER_UNLOCK_ERROR", e)
        }
    }

    /**
     * Online-first tamper unlock (falls back to cached offline verifier when API unreachable).
     * Returns map: verified, unlockExpiresAt, usedOffline, error.
     */
    @ReactMethod
    fun verifyTamperSecret(pin: String?, code: String?, target: String?, promise: Promise) {
        Thread {
            try {
                val outcome = com.childmonitor.tamper.TamperApiClient.verifyDetailed(
                    reactContext,
                    pin = pin?.trim()?.takeIf { it.isNotEmpty() },
                    code = code?.trim()?.takeIf { it.isNotEmpty() },
                    target = target,
                )
                val map = Arguments.createMap()
                when (outcome) {
                    is com.childmonitor.tamper.TamperApiClient.VerifyOutcome.Success -> {
                        val r = outcome.result
                        if (r.verified) {
                            com.childmonitor.tamper.TamperApiClient.applyUnlock(
                                reactContext,
                                r.unlockExpiresAt,
                            )
                        }
                        map.putBoolean("verified", r.verified)
                        map.putBoolean("usedOffline", r.usedOffline)
                        if (r.unlockExpiresAt != null) {
                            map.putString("unlockExpiresAt", r.unlockExpiresAt.toString())
                        } else {
                            map.putNull("unlockExpiresAt")
                        }
                    }
                    is com.childmonitor.tamper.TamperApiClient.VerifyOutcome.Failure -> {
                        map.putBoolean("verified", false)
                        map.putBoolean("usedOffline", false)
                        map.putNull("unlockExpiresAt")
                        val msg = outcome.userMessageOverride
                            ?: reactContext.getString(outcome.userMessageRes)
                        map.putString("error", msg)
                    }
                }
                promise.resolve(map)
            } catch (e: Exception) {
                promise.reject("E_TAMPER_VERIFY", e.message, e)
            }
        }.start()
    }

    @ReactMethod
    fun getWizardPersistenceState(promise: Promise) {
        try {
            promise.resolve(MonitoringPrefs.getWizardPersistenceJson(reactContext))
        } catch (e: Exception) {
            promise.reject("WIZARD_PERSISTENCE_ERROR", e)
        }
    }

    @ReactMethod
    fun setWizardPersistenceState(json: String, promise: Promise) {
        try {
            MonitoringPrefs.setWizardPersistenceJson(reactContext, json)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("WIZARD_PERSISTENCE_ERROR", e)
        }
    }

    @ReactMethod
    fun clearWizardPersistenceState(promise: Promise) {
        try {
            MonitoringPrefs.setWizardPersistenceJson(reactContext, null)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("WIZARD_PERSISTENCE_ERROR", e)
        }
    }

    @ReactMethod
    fun launchTamperGate(promise: Promise) {
        try {
            com.childmonitor.tamper.TamperGateController.launchGate(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("TAMPER_GATE_ERROR", e)
        }
    }

    @ReactMethod
    fun launchTamperGateForAction(action: String, promise: Promise) {
        try {
            com.childmonitor.tamper.TamperGateController.launchGateForAction(reactContext, action)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("TAMPER_GATE_ERROR", e)
        }
    }

    @ReactMethod
    fun isTamperUnlocked(promise: Promise) {
        promise.resolve(MonitoringPrefs.isTamperUnlocked(reactContext))
    }

    @ReactMethod
    fun isDeviceOwner(promise: Promise) {
        promise.resolve(DeviceOwnerManager.isDeviceOwner(reactContext))
    }

    @ReactMethod
    fun getAdbDeviceOwnerCommand(promise: Promise) {
        promise.resolve(DeviceOwnerManager.getAdbProvisionCommand(reactContext))
    }

    @ReactMethod
    fun refreshDeviceOwnerState(promise: Promise) {
        try {
            if (DeviceOwnerManager.isDeviceOwner(reactContext)) {
                DeviceOwnerManager.onDeviceOwnerEstablished(reactContext)
            }
            promise.resolve(DeviceOwnerManager.isDeviceOwner(reactContext))
        } catch (e: Exception) {
            promise.reject("DEVICE_OWNER_ERROR", e)
        }
    }

    /**
     * Pre-start ScreenCaptureForegroundService (mediaProjection FGS) before capture.
     * WebRTC's MediaProjectionService is disabled — it crashes on Android 14+ alongside ours.
     */
    @ReactMethod
    fun startWebRtcMediaProjectionService(promise: Promise) {
        try {
            val ok = com.childmonitor.capture.ScreenCaptureForegroundService.startAndWait(reactContext)
            if (ok) promise.resolve(null) else promise.reject("FGS_FAILED", "Screen capture FGS did not start")
        } catch (e: Exception) {
            promise.reject("FGS_FAILED", e)
        }
    }

    /**
     * Create a screen MediaStream from the one-time MediaProjection consent stored during
     * Permissions setup — avoids the per-session getDisplayMedia() system dialog.
     */
    @ReactMethod
    fun getDisplayMediaFromStoredConsent(promise: Promise) {
        Thread {
            try {
                if (!MediaProjectionTokenStore.hasValidToken(reactContext)) {
                    promise.reject(
                        "NOT_GRANTED",
                        "Screen capture not set up — grant Screen capture once in child Permissions",
                    )
                    return@Thread
                }
                if (MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(reactContext)) {
                    MediaProjectionTokenStore.logProjectionLostAfterProcessRestart(reactContext)
                    promise.reject(
                        "PROJECTION_LOST",
                        "Screen capture consent lost after restart — re-grant in child Permissions",
                    )
                    return@Thread
                }
                val activity = currentActivity
                if (activity == null || activity.isFinishing) {
                    promise.reject("NO_ACTIVITY", "Open the child app to start screen sharing")
                    return@Thread
                }
                if (!com.childmonitor.capture.ScreenCaptureForegroundService.startAndWait(reactContext)) {
                    promise.reject("SCREEN_FGS_FAILED", "Screen capture foreground service did not start")
                    return@Thread
                }
                val projection = ScreenCaptureController.acquireProjectionForCapture(reactContext)
                if (projection == null) {
                    promise.reject(
                        "PROJECTION_EXPIRED",
                        "Screen capture consent expired — re-grant in child Permissions",
                    )
                    return@Thread
                }
                if (!ScreenCaptureController.projectionObtainedWithFgs()) {
                    promise.reject(
                        "PROJECTION_BEFORE_FGS",
                        "Screen capture started before foreground service — retry in a few seconds",
                    )
                    return@Thread
                }
                val webrtc = reactApplicationContext.getNativeModule(
                    com.oney.WebRTCModule.WebRTCModule::class.java,
                )
                if (webrtc == null) {
                    promise.reject("NO_WEBRTC", "WebRTC module unavailable")
                    return@Thread
                }
                val metrics = com.oney.WebRTCModule.DisplayUtils.getDisplayMetrics(activity)
                val controller = com.oney.WebRTCModule.StoredProjectionScreenController(
                    reactContext,
                    projection,
                    metrics.widthPixels,
                    metrics.heightPixels,
                    metrics.densityDpi,
                )
                val track = webrtc.createVideoTrack(controller)
                if (track == null) {
                    promise.reject("TRACK_FAILED", "Could not create screen video track")
                    return@Thread
                }
                webrtc.createStream(arrayOf(track)) { streamId, tracksInfo ->
                    if (tracksInfo.isEmpty()) {
                        promise.reject("NO_TRACK", "Screen track missing after createStream")
                        return@createStream
                    }
                    val data = Arguments.createMap()
                    data.putString("streamId", streamId)
                    data.putMap("track", tracksInfo[0])
                    promise.resolve(data)
                }
            } catch (e: Exception) {
                PcpFlowLogger.error(PcpFlowLogger.Category.MEDIA_PROJECTION, "stored display media failed", e)
                promise.reject("STORED_DISPLAY_ERROR", e)
            }
        }.start()
    }

    @ReactMethod
    fun getHiddenAudioMediaStream(promise: Promise) {
        HiddenAudioCapture.createMediaStream(reactContext, promise)
    }

    /**
     * Ambient room mic for camera monitoring — does **not** take exclusive audio focus or
     * MODE_IN_COMMUNICATION, so YouTube/Spotify keep playing (FlashGet Kids-style).
     */
    @ReactMethod
    fun getHiddenAmbientAudioMediaStream(promise: Promise) {
        HiddenAudioCapture.createAmbientMicMediaStream(reactContext, promise)
    }

    /**
     * Audio track for AudioPlaybackCapture injection — does not steal audio focus from
     * YouTube/Spotify/games (unlike [getHiddenAudioMediaStream]).
     */
    @ReactMethod
    fun getHiddenPlaybackAudioMediaStream(promise: Promise) {
        HiddenAudioCapture.createPlaybackInjectMediaStream(reactContext, promise)
    }

    /**
     * Starts MediaProjection AudioPlaybackCapture and injects device audio into the next
     * WebRTC mic track (used for AUDIO mode and screen-share system audio).
     */
    @ReactMethod
    fun startDevicePlaybackCapture(promise: Promise) {
        Thread {
            try {
                val ok = DevicePlaybackCaptureSession.start(reactContext.applicationContext)
                if (ok) {
                    promise.resolve(true)
                } else {
                    promise.reject("PLAYBACK_CAPTURE_FAILED", "Device playback capture failed to start")
                }
            } catch (e: Exception) {
                promise.reject("PLAYBACK_CAPTURE_FAILED", e)
            }
        }.start()
    }

    @ReactMethod
    fun stopDevicePlaybackCapture(promise: Promise) {
        try {
            DevicePlaybackCaptureSession.stop()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("PLAYBACK_CAPTURE_STOP_ERROR", e)
        }
    }

    /**
     * Get diagnostics for one-way listening audio capture to help troubleshoot issues
     */
    @ReactMethod 
    fun getPlaybackCaptureStats(promise: Promise) {
        try {
            val isCapturerRunning = DevicePlaybackAudioCapturer.isRunning()
            val isInjectorActive = PlaybackCaptureInjector.isActive()
            val hasConsent = MediaProjectionConsentHelper.hasConsent(reactContext.applicationContext)
            val projectionReady = ScreenCaptureController.activeProjection() != null

            val data = Arguments.createMap().apply {
                putBoolean("isCapturerRunning", isCapturerRunning)
                putBoolean("isInjectorActive", isInjectorActive)
                putBoolean("projectionReady", projectionReady)
                putBoolean("hasConsent", hasConsent)
                putString("diagnosis", when {
                    !hasConsent -> "MediaProjection not ready - need screen recording permission"
                    !isCapturerRunning -> "Audio capturer not running - may have failed to start"
                    !isInjectorActive -> "Audio injector not active - WebRTC track may not be created"
                    else -> "All systems active - if silent, child device needs system audio playing"
                })
            }
            promise.resolve(data)
        } catch (e: Exception) {
            promise.reject("PLAYBACK_STATS_ERROR", e)
        }
    }

    @ReactMethod
    fun getHiddenScreenMediaStream(promise: Promise) {
        HiddenScreenCapture.createMediaStream(reactContext, promise)
    }

    @ReactMethod
    fun stopHiddenAudioStream(promise: Promise) {
        try {
            DevicePlaybackCaptureSession.stop()
            HiddenAudioCapture.stop(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("HIDDEN_AUDIO_STOP_ERROR", e)
        }
    }

    @ReactMethod
    fun stopHiddenScreenStream(promise: Promise) {
        try {
            // Screen sessions may have started device-playback audio; always stop it so the
            // parent does not keep a stale "Listening" / audio path after Stop.
            DevicePlaybackCaptureSession.stop()
            HiddenScreenCapture.stop(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("HIDDEN_SCREEN_STOP_ERROR", e)
        }
    }

    @ReactMethod
    fun getHiddenCameraMediaStream(facingMode: String, promise: Promise) {
        val facing = if (facingMode.equals("environment", ignoreCase = true)) "environment" else "user"
        
        // Initialize camera access manager for potential takeover
        CameraAccessManager.initialize(reactContext.applicationContext)
        Log.i("ChildMonitorNativeModule", "Camera access manager initialized for stream creation")
        
        HiddenCameraCapture.createMediaStream(reactContext, facing, promise)
    }
    
    /**
     * Force camera access from other apps for parental monitoring priority
     */
    @ReactMethod
    fun forceCameraAccess(cameraId: String, promise: Promise) {
        try {
            CameraAccessManager.initialize(reactContext.applicationContext)
            val success = CameraAccessManager.forceCameraAccess(cameraId)
            Log.i("ChildMonitorNativeModule", "Force camera access result: $success for camera $cameraId")
            promise.resolve(success)
        } catch (e: Exception) {
            Log.e("ChildMonitorNativeModule", "Failed to force camera access", e)
            promise.reject("CAMERA_FORCE_ERROR", e.message, e)
        }
    }
    
    /**
     * Release forced camera access
     */
    @ReactMethod
    fun releaseCameraAccess(promise: Promise) {
        try {
            CameraAccessManager.releaseCamera()
            Log.i("ChildMonitorNativeModule", "Camera access released")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e("ChildMonitorNativeModule", "Failed to release camera access", e)
            promise.reject("CAMERA_RELEASE_ERROR", e.message, e)
        }
    }

    /** Flip the live hidden camera without recreating the WebRTC track. */
    @ReactMethod
    fun switchHiddenCameraFacing(facingMode: String, promise: Promise) {
        val facing = if (facingMode.equals("environment", ignoreCase = true)) "environment" else "user"
        HiddenCameraCapture.switchFacing(facing, promise)
    }

    /** Get the current camera facing state for debugging and state synchronization. */
    @ReactMethod
    fun getCurrentCameraFacing(promise: Promise) {
        try {
            val facing = HiddenCameraCapture.getCurrentFacing()
            promise.resolve(facing ?: "unknown")
        } catch (e: Exception) {
            promise.reject("GET_FACING_ERROR", e.message, e)
        }
    }

    /** Reset camera facing state for error recovery. */
    @ReactMethod
    fun resetCameraFacingState(facingMode: String, promise: Promise) {
        try {
            val facing = if (facingMode.equals("environment", ignoreCase = true)) "environment" else "user"
            // This is a best-effort state reset - doesn't actually change camera, just updates tracking
            Log.i("ChildMonitorNativeModule", "Resetting camera facing state to: $facing")
            promise.resolve(facing)
        } catch (e: Exception) {
            promise.reject("RESET_FACING_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun stopHiddenCameraStream(promise: Promise) {
        try {
            HiddenCameraCapture.stop(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("HIDDEN_CAMERA_STOP_ERROR", e)
        }
    }

    @ReactMethod
    fun ensureForegroundForScreenCapture(promise: Promise) {
        try {
            PcpFlowLogger.log(
                PcpFlowLogger.Category.PERMISSION_FLOW,
                "ensureForegroundForScreenCapture",
                "hasActivity=${currentActivity != null}",
            )
            val activity = currentActivity
            if (activity != null && !activity.isFinishing) {
                promise.resolve(true)
                return
            }
            val intent = Intent(reactContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            reactContext.startActivity(intent)
            Handler(Looper.getMainLooper()).postDelayed({
                val resumedActivity = currentActivity
                val resumed = resumedActivity != null && !resumedActivity.isFinishing
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.PERMISSION_FLOW,
                    "ensureForeground result",
                    "resumed=$resumed",
                )
                if (resumed) {
                    promise.resolve(true)
                } else {
                    promise.reject("NO_ACTIVITY", "Open the child app to approve screen sharing")
                }
            }, 1500)
        } catch (e: Exception) {
            PcpFlowLogger.error(PcpFlowLogger.Category.PERMISSION_FLOW, "ensureForeground failed", e)
            promise.reject("FOREGROUND_ERROR", e)
        }
    }

    @ReactMethod
    fun getStreamDiagnostics(promise: Promise) {
        try {
            val map = Arguments.createMap()
            map.putBoolean("isPaired", MonitoringPrefs.isPaired(reactContext))
            map.putBoolean("mediaProjectionGranted", MediaProjectionTokenStore.hasValidToken(reactContext))
            map.putBoolean("deviceOwner", DeviceOwnerManager.isDeviceOwner(reactContext))
            map.putBoolean("nativeScreenRunning", NativeStreamPublisher.isRunning())
            map.putBoolean("jsListenerActive", NativeStreamingCoordinator.isJsListenerActive())
            map.putBoolean("hasActivity", currentActivity != null && currentActivity?.isFinishing != true)
            map.putBoolean("cameraGranted", hasCameraAndMic() && ContextCompat.checkSelfPermission(
                reactContext, android.Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED)
            map.putBoolean("micGranted", ContextCompat.checkSelfPermission(
                reactContext, android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED)
            map.putBoolean("overlayGranted", Settings.canDrawOverlays(reactContext))
            val pm = reactContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            map.putBoolean("batteryOptimizationExempt", pm.isIgnoringBatteryOptimizations(reactContext.packageName))
            map.putString("manufacturer", Build.MANUFACTURER)
            map.putString("androidVersion", Build.VERSION.RELEASE)
            map.putInt("sdkInt", Build.VERSION.SDK_INT)
            map.putString("apiBaseUrl", MonitoringPrefs.apiBaseUrl(reactContext))
            val sessionSnap = StreamSessionRegistry.snapshot()
            val sessionMap = Arguments.createMap()
            for ((key, value) in sessionSnap) {
                when (value) {
                    is String -> sessionMap.putString(key, value)
                    is Long -> sessionMap.putDouble(key, value.toDouble())
                    is Boolean -> sessionMap.putBoolean(key, value)
                    else -> sessionMap.putString(key, value?.toString())
                }
            }
            map.putMap("sessionRegistry", sessionMap)
            val telemetry = StreamLifecycleTelemetry.metricsSnapshot()
            val telemetryMap = Arguments.createMap()
            for ((key, value) in telemetry) {
                when (value) {
                    is Int -> telemetryMap.putInt(key, value)
                    is List<*> -> telemetryMap.putArray(key, Arguments.fromList(value))
                    else -> telemetryMap.putString(key, value.toString())
                }
            }
            map.putMap("streamTelemetry", telemetryMap)
            promise.resolve(map)
        } catch (e: Exception) {
            promise.reject("DIAGNOSTICS_ERROR", e)
        }
    }

    @ReactMethod
    fun isMediaProjectionGranted(promise: Promise) {
        val granted = MediaProjectionTokenStore.hasValidToken(reactContext)
        PcpFlowLogger.log(
            PcpFlowLogger.Category.PERMISSION_FLOW,
            "isMediaProjectionGranted",
            "granted=$granted state=${MediaProjectionTokenStore.debugState(reactContext)}",
        )
        promise.resolve(granted)
    }

    @ReactMethod
    fun clearMediaProjectionConsent(promise: Promise) {
        try {
            PermissionSettingsLauncher.clearScreenCaptureConsent(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("MEDIA_PROJECTION_CLEAR_ERROR", e)
        }
    }

    @ReactMethod
    fun getMediaProjectionDebugState(promise: Promise) {
        val state = MediaProjectionTokenStore.debugState(reactContext)
        val map = Arguments.createMap()
        state.forEach { (k, v) ->
            when (v) {
                is Boolean -> map.putBoolean(k, v)
                is Int -> map.putInt(k, v)
                else -> map.putString(k, v.toString())
            }
        }
        promise.resolve(map)
    }

    @ReactMethod
    fun isProjectionLostAfterProcessRestart(promise: Promise) {
        promise.resolve(
            MediaProjectionTokenStore.isProjectionLostAfterProcessRestart(reactContext),
        )
    }

    @ReactMethod
    fun needsScreenCaptureRecovery(promise: Promise) {
        promise.resolve(
            MediaProjectionTokenStore.needsScreenCaptureRecovery(reactContext),
        )
    }

    @ReactMethod
    fun getFcmToken(promise: Promise) {
        // Return cached token first (available without network).
        val cached = MonitoringPrefs.fcmToken(reactContext)
        if (!cached.isNullOrBlank()) {
            promise.resolve(cached)
            return
        }
        // Best-effort blocking fetch (up to 5s) for pairing flow.
        Thread {
            try {
                val latch = java.util.concurrent.CountDownLatch(1)
                val ref = java.util.concurrent.atomic.AtomicReference<String?>()
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        if (!token.isNullOrBlank()) {
                            com.childmonitor.fcm.FcmWakeHandler.onNewToken(reactContext, token)
                            ref.set(token)
                        }
                        latch.countDown()
                    }
                    .addOnFailureListener { latch.countDown() }
                latch.await(5_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
                promise.resolve(ref.get())
            } catch (e: Exception) {
                promise.resolve(null)
            }
        }.start()
    }

    @ReactMethod
    fun warmMediaProjectionSession(promise: Promise) {
        // Do not start screen FGS here — that caused startup crashes on Android 14+.
        // FGS starts on-demand when parent requests a screen stream.
        promise.resolve(MediaProjectionTokenStore.hasValidToken(reactContext))
    }

    @ReactMethod
    fun ensureMediaProjectionSessionReady(promise: Promise) {
        try {
            val ok = com.childmonitor.capture.MediaProjectionSessionKeeper.ensureReady(reactContext)
            promise.resolve(ok)
        } catch (e: Exception) {
            promise.reject("PROJECTION_READY_ERROR", e)
        }
    }

    @ReactMethod
    fun requestMediaProjectionConsent(promise: Promise) {
        PcpFlowLogger.log(
            PcpFlowLogger.Category.PERMISSION_FLOW,
            "requestMediaProjectionConsent called",
            "before=${MediaProjectionTokenStore.debugState(reactContext)}",
        )
        if (MediaProjectionTokenStore.hasValidToken(reactContext)) {
            ScreenCaptureAutoApprove.disarm(reactContext)
            PcpFlowLogger.log(PcpFlowLogger.Category.PERMISSION_FLOW, "requestMediaProjectionConsent — already granted")
            promise.resolve(true)
            return
        }
        Thread {
            try {
                val launchContext = currentActivity ?: reactApplicationContext
                val granted = MediaProjectionConsentHelper.ensureConsent(
                    launchContext,
                    60_000L,
                    com.childmonitor.capture.ConsentLaunchSource.USER_GRANT,
                )
                PcpFlowLogger.log(
                    PcpFlowLogger.Category.PERMISSION_FLOW,
                    "requestMediaProjectionConsent finished",
                    "granted=$granted after=${MediaProjectionTokenStore.debugState(reactContext)}",
                )
                if (granted) {
                    MediaProjectionSessionKeeper.warmSessionAsync(reactContext.applicationContext)
                }
                promise.resolve(granted)
            } catch (e: Exception) {
                PcpFlowLogger.error(PcpFlowLogger.Category.PERMISSION_FLOW, "requestMediaProjectionConsent error", e)
                promise.reject("PROJECTION_CONSENT_ERROR", e)
            }
        }.start()
    }

    @ReactMethod
    fun clearPairedCredentials(promise: Promise) {
        try {
            MonitoringPrefs.clearPairedCredentials(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("CLEAR_CREDENTIALS_ERROR", e)
        }
    }

    @ReactMethod
    fun getDeviceCapabilities(promise: Promise) {
        val deviceOwner = DeviceOwnerManager.isDeviceOwner(reactContext)
        val projectionGranted = MediaProjectionTokenStore.hasValidToken(reactContext)
        val autoApproveReady = ScreenCaptureAutoApprove.isReady(reactContext)
        val map: WritableMap = Arguments.createMap()
        map.putBoolean("deviceOwner", deviceOwner)
        map.putBoolean("mediaProjectionGranted", projectionGranted)
        map.putBoolean("screenCaptureAutoApproveReady", autoApproveReady)
        map.putBoolean("silentScreenCapture", projectionGranted)
        map.putString(
            "protectionLevel",
            when {
                projectionGranted && autoApproveReady -> "FULL"
                projectionGranted -> "ENHANCED"
                autoApproveReady -> "AUTO_APPROVE"
                deviceOwner -> "DEVICE_OWNER"
                else -> "STANDARD"
            },
        )
        promise.resolve(map)
    }

    @ReactMethod
    fun startNativeScreenStream(roomId: String, sessionId: String, promise: Promise) {
        try {
            if (!ScreenCaptureController.canUseSilentCapture(reactContext)) {
                promise.reject("NOT_CAPABLE", "One-time screen capture consent required — grant in Permissions setup")
                return
            }
            // P1-07: do not call getMediaProjection here — startStream → startAndWait promotes
            // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION first, then obtains inside the service.
            val started = ScreenCaptureForegroundService.startStream(reactContext, roomId, sessionId)
            if (!started) {
                promise.reject("FOREGROUND_BLOCKED", "Cannot start screen capture from background — app must be in foreground")
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject("NATIVE_STREAM_ERROR", e)
        }
    }

    @ReactMethod
    fun stopNativeScreenStream(promise: Promise) {
        try {
            ScreenCaptureForegroundService.stopStream(reactContext)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("NATIVE_STREAM_ERROR", e)
        }
    }

    @ReactMethod
    fun isNativeScreenStreamRunning(promise: Promise) {
        promise.resolve(NativeStreamPublisher.isRunning())
    }

    @ReactMethod
    fun enableKioskMode(promise: Promise) {
        try {
            val ok = DeviceOwnerManager.enableKiosk(reactContext)
            if (ok) promise.resolve(true) else promise.reject("KIOSK_ERROR", "Device Owner required for kiosk")
        } catch (e: Exception) {
            promise.reject("KIOSK_ERROR", e)
        }
    }

    @ReactMethod
    fun disableKioskMode(promise: Promise) {
        try {
            DeviceOwnerManager.disableKiosk(reactContext)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("KIOSK_ERROR", e)
        }
    }

    @ReactMethod
    fun isKioskModeActive(promise: Promise) {
        promise.resolve(DeviceOwnerManager.isKioskActive(reactContext))
    }

    @ReactMethod
    fun revokeMonitoringAndStop(promise: Promise) {
        val app = reactContext.applicationContext
        com.childmonitor.monitoring.MonitoringRevokeManager.revokeMonitoringAndStop(
            app,
            source = "js_bridge",
        )
        promise.resolve(null)
    }

    @ReactMethod
    fun getDeviceAttestation(purpose: String, promise: Promise) {
        // Never block the RN native-modules thread — Play Integrity uses a latch of up to 15s
        // and freezing that queue leaves PairingScreen stuck on "Pairing…".
        Thread(
            {
                try {
                    val ctx = reactContext.applicationContext
                    val normalizedPurpose = purpose.ifBlank { "pair" }
                    val integrityToken = PlayIntegrityHelper.requestToken(ctx, normalizedPurpose)
                    val signals = DeviceTrustSignalsCollector.collect(ctx)
                    val map = Arguments.createMap()
                    if (!integrityToken.isNullOrBlank()) {
                        map.putString("integrityToken", integrityToken)
                    }
                    val signalsMap = Arguments.createMap()
                    signalsMap.putBoolean("rootDetected", signals.rootDetected)
                    signalsMap.putBoolean("magiskDetected", signals.magiskDetected)
                    signalsMap.putBoolean("fridaDetected", signals.fridaDetected)
                    signalsMap.putBoolean("emulatorDetected", signals.emulatorDetected)
                    signalsMap.putBoolean("debuggerAttached", signals.debuggerAttached)
                    signalsMap.putBoolean("hooksDetected", signals.hooksDetected)
                    signalsMap.putBoolean("testKeysBuild", signals.testKeysBuild)
                    signalsMap.putString("collectorVersion", signals.collectorVersion)
                    map.putMap("trustSignals", signalsMap)
                    promise.resolve(map)
                } catch (e: Exception) {
                    promise.reject("DEVICE_ATTESTATION_ERROR", e)
                }
            },
            "DeviceAttestation",
        ).start()
    }

    companion object {
        private const val LOCATION_REQUEST_CODE = 2001
        private const val CAMERA_MIC_REQUEST_CODE = 2002
        private const val CAMERA_REQUEST_CODE = 2003
        private const val MIC_REQUEST_CODE = 2004
        private const val POST_NOTIFICATIONS_REQUEST_CODE = 2005
        private var eventContext: ReactApplicationContext? = null

        @JvmStatic
        fun canDeliverStreamToJs(): Boolean {
            val ctx = eventContext ?: return false
            return ctx.hasActiveReactInstance()
        }

        @JvmStatic
        fun getSharedReactApplicationContext(): ReactApplicationContext? = eventContext

        @JvmStatic
        fun emitStreamControl(command: String): Boolean {
            val ctx = eventContext ?: return false
            if (!ctx.hasActiveReactInstance()) return false
            val payload = Arguments.createMap()
            payload.putString("command", command)
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("nativeStreamControl", payload)
            return true
        }

        @JvmStatic
        fun emitMonitoringRevoked(source: String = "native"): Boolean {
            val ctx = eventContext ?: return false
            if (!ctx.hasActiveReactInstance()) return false
            val payload = Arguments.createMap()
            payload.putString("source", source)
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("monitoringRevoked", payload)
            return true
        }

        @JvmStatic
        fun emitPermissionStateChanged(): Boolean {
            val ctx = eventContext ?: return false
            if (!ctx.hasActiveReactInstance()) return false
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("permissionStateChanged", null)
            return true
        }

        @JvmStatic
        fun emitStreamWake(
            roomId: String,
            streamType: String,
            sessionId: String,
            cameraFacing: String?,
        ): Boolean {
            val ctx = eventContext ?: run {
                Log.w("ChildMonitorNative", "emitStreamWake skipped — no React context")
                return false
            }
            if (!ctx.hasActiveReactInstance()) {
                Log.w(
                    "ChildMonitorNative",
                    "emitStreamWake skipped — React instance inactive " +
                        "(type=$streamType session=$sessionId)",
                )
                return false
            }
            PcpFlowLogger.log(
                PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
                "emitStreamWake to JS",
                "type=$streamType session=$sessionId room=$roomId",
            )
            val payload = Arguments.createMap()
            payload.putString("roomId", roomId)
            payload.putString("streamType", streamType)
            payload.putString("sessionId", sessionId)
            if (!cameraFacing.isNullOrBlank()) {
                payload.putString("cameraFacing", cameraFacing)
            }
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("streamRequestWake", payload)
            return true
        }

        /** Relay WebRTC signaling from authoritative native STOMP to JS. */
        @JvmStatic
        fun emitSignalingMessage(roomId: String, body: String): Boolean {
            val ctx = eventContext ?: return false
            if (!ctx.hasActiveReactInstance()) return false
            val payload = Arguments.createMap()
            payload.putString("roomId", roomId)
            payload.putString("body", body)
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("nativeSignalingMessage", payload)
            return true
        }
    }
}
