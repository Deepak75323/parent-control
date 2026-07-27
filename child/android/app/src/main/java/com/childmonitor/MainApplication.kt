package com.childmonitor

import android.app.Application
import com.childmonitor.sync.CommandRecoveryManager
import com.childmonitor.capture.MediaProjectionConsentState
import com.childmonitor.capture.MediaProjectionLossHandler
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.capture.PlaybackCaptureInjector
import com.childmonitor.monitoring.BootWakeChain
import com.childmonitor.monitoring.MonitoringServiceLauncher
import com.childmonitor.monitoring.PresenceHeartbeatScheduler
import com.childmonitor.network.NetworkConnectivityMonitor
import com.childmonitor.native.ChildMonitorNativePackage
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
import com.oney.WebRTCModule.WebRTCModuleOptions
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

class MainApplication : Application(), ReactApplication {

  private val startupExecutor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "child-monitor-startup").apply { isDaemon = true }
  }

  override val reactNativeHost: ReactNativeHost =
      object : DefaultReactNativeHost(this) {
        override fun getPackages(): List<ReactPackage> =
            PackageList(this).packages.apply {
              add(ChildMonitorNativePackage())
            }

        override fun getJSMainModuleName(): String = "index"

        // When EMBEDDED_BUNDLE=true (Oracle staging APKs), use the assets bundle without Metro.
        override fun getUseDeveloperSupport(): Boolean =
            BuildConfig.DEBUG && !BuildConfig.EMBEDDED_BUNDLE

        override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
      }

  override val reactHost: ReactHost
    get() = getDefaultReactHost(applicationContext, reactNativeHost)

  override fun onCreate() {
    super.onCreate()
    // Use ScreenCaptureForegroundService (mediaProjection FGS) only — WebRTC's
    // MediaProjectionService crashes on Android 14+ when started alongside it.
    WebRTCModuleOptions.getInstance().enableMediaProjectionService = false
    // Install ADM before WebRTCModule init so AudioPlaybackCapture can inject system
    // audio into getUserMedia audio tracks (device / screen-share audio).
    installPlaybackCaptureAudioDeviceModule()
    SoLoader.init(this, OpenSourceMergedSoMapping)
    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
      load()
    }
    startupExecutor.execute { ensureMonitoringServiceIfPaired() }
    SetupAccessNotificationHelper.sync(applicationContext)
    // P1-05: reconcile USE_FULL_SCREEN_INTENT (Android 14+) — detect revoke after upgrade.
    if (com.childmonitor.capture.FullScreenIntentHelper.isRequired()) {
      com.childmonitor.capture.FullScreenIntentHelper.validateOnProcessStart(applicationContext)
    }
  }

  /** Restart native monitoring when process starts (boot, FGS restart, or after swipe-kill). */
  private fun ensureMonitoringServiceIfPaired() {
    if (!MonitoringPrefs.isPaired(this)) return
    CommandRecoveryManager.onProcessStart(applicationContext, trigger = "app_start")
    BootWakeChain.reinforceIfPostBoot(this)
    when (MediaProjectionTokenStore.getConsentState(this)) {
        MediaProjectionConsentState.LOST_AFTER_PROCESS_DEATH,
        MediaProjectionConsentState.REQUIRES_RECONSENT,
        MediaProjectionConsentState.RECOVERY_FAILED,
        MediaProjectionConsentState.RECOVERY_PENDING,
        MediaProjectionConsentState.RECOVERY_DEFERRED,
        ->
            // Mark LOST + notify parent — never auto-launch Cast on process start.
            MediaProjectionLossHandler.onProjectionUnavailable(applicationContext, "app_process_start")
        else -> { }
    }
    PresenceHeartbeatScheduler.schedule(applicationContext)
    // Start network connectivity monitoring for immediate offline detection
    NetworkConnectivityMonitor.startMonitoring(applicationContext)
    MonitoringServiceLauncher.requestMonitoring(
        applicationContext,
        MonitoringServiceLauncher.Reason.APP_START,
    )
  }

  private fun installPlaybackCaptureAudioDeviceModule() {
    try {
      val adm = JavaAudioDeviceModule.builder(this)
          .setEnableVolumeLogger(false)
          .setUseHardwareAcousticEchoCanceler(false)  // Disable AEC for system audio
          .setUseHardwareNoiseSuppressor(false)       // Disable NS for system audio
          .setAudioRecordDataCallback { _audioFormat, channelCount, sampleRate, audioBuffer ->
            PlaybackCaptureInjector.fillWebRtcBuffer(channelCount, sampleRate, audioBuffer)
          }
          .createAudioDeviceModule()
      WebRTCModuleOptions.getInstance().audioDeviceModule = adm
      android.util.Log.i("MainApplication", "Playback-capture ADM installed successfully")
    } catch (e: Exception) {
      android.util.Log.e("MainApplication", "playback-capture ADM install failed", e)
    }
  }
}
