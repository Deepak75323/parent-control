package com.childmonitor.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.childmonitor.MonitoringPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypedStreamFgsTimeoutInstrumentedTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    MonitoringPrefs.setPaired(context, true)
  }

  @Test
  fun stopKind_audio_doesNotCrash() {
    StreamCaptureTeardown.stopKind(context, StreamKind.AUDIO, "test")
    assertFalse(AudioStreamForegroundService.isActive())
  }

  @Test
  fun stopKind_camera_doesNotCrash() {
    StreamCaptureTeardown.stopKind(context, StreamKind.CAMERA, "test")
    assertFalse(CameraStreamForegroundService.isActive())
  }
}
