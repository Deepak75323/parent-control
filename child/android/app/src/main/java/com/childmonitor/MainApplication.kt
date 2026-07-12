package com.childmonitor

import android.app.Application
import com.childmonitor.sync.CommandRecoveryManager
import com.childmonitor.capture.MediaProjectionConsentState
import com.childmonitor.capture.MediaProjectionLossHandler
import com.childmonitor.capture.MediaProjectionTokenStore
import com.childmonitor.monitoring.BootWakeChain
import com.childmonitor.monitoring.MonitoringServiceLauncher
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

        override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

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
        -> MediaProjectionLossHandler.onProjectionUnavailable(applicationContext, "app_process_start")
        MediaProjectionConsentState.RECOVERY_PENDING ->
            com.childmonitor.capture.MediaProjectionRecoveryManager.attemptRecovery(
                applicationContext,
                "stale_recovery_resume",
            )
        else -> { }
    }
    MonitoringServiceLauncher.requestMonitoring(
        applicationContext,
        MonitoringServiceLauncher.Reason.APP_START,
    )
  }
}
