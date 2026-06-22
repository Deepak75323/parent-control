package com.childmonitor

import android.content.Intent
import android.os.Bundle
import com.childmonitor.capture.ScreenFgsStartupGate
import com.childmonitor.capture.StreamFgsPreparer
import com.childmonitor.capture.StreamLifecycleLogger
import com.childmonitor.debug.PcpFlowLogger
import com.childmonitor.platform.Android16CompatibilityLayer
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  override fun getMainComponentName(): String = "ParentalControlChild"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  override fun onCreate(savedInstanceState: Bundle?) {
    // react-native-screens: never restore fragment state after process death / screen off.
    super.onCreate(null)
    Android16CompatibilityLayer.applyWindowInsets(this)
    handleStreamWakeIntent(intent)
  }

  override fun onResume() {
    super.onResume()
    StreamLifecycleLogger.log("APP_FOREGROUND", "MainActivity.onResume")
    ScreenFgsStartupGate.scheduleMarkReady()
    ensureStreamForegroundServices()
  }

  override fun onPause() {
    StreamLifecycleLogger.log("APP_BACKGROUND", "MainActivity.onPause")
    super.onPause()
  }

  override fun onStop() {
    StreamLifecycleLogger.log("APP_BACKGROUND", "MainActivity.onStop uiVisible=false")
    super.onStop()
  }

  override fun onDestroy() {
    StreamLifecycleLogger.log("ACTIVITY_DESTROYED", "MainActivity.onDestroy")
    super.onDestroy()
  }

  /**
   * Pre-start camera/audio FGS while the app is eligible (foreground).
   * Screen capture FGS starts on-demand when parent requests a screen stream.
   */
  private fun ensureStreamForegroundServices() {
    if (!MonitoringPrefs.isPaired(this)) return
    StreamFgsPreparer.ensureAllReadyAsync(applicationContext)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    if (intent != null) {
      setIntent(intent)
      handleStreamWakeIntent(intent)
    }
  }

  private fun handleStreamWakeIntent(intent: Intent?) {
    if (intent?.getBooleanExtra(EXTRA_STREAM_WAKE, false) != true) return
    val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: "SCREEN"
    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
    PcpFlowLogger.log(
      PcpFlowLogger.Category.SCREEN_SHARE_FLOW,
      "MainActivity stream wake",
      "type=$streamType session=$sessionId",
    )
    com.childmonitor.native.ChildMonitorNativeModule.emitStreamWake(
      intent.getStringExtra(EXTRA_ROOM_ID) ?: "",
      streamType,
      sessionId,
      intent.getStringExtra(EXTRA_CAMERA_FACING),
    )
  }

  companion object {
    const val EXTRA_STREAM_WAKE = "com.childmonitor.STREAM_WAKE"
    const val EXTRA_ROOM_ID = "roomId"
    const val EXTRA_STREAM_TYPE = "streamType"
    const val EXTRA_SESSION_ID = "sessionId"
    const val EXTRA_CAMERA_FACING = "cameraFacing"
  }
}
