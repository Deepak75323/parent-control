package com.childmonitor.capture

import android.util.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraXVideoCapturerTest {

    @Test
    fun wantsFrontCamera_defaultsToFrontWhenNameMissing() {
        assertTrue(CameraXVideoCapturer.wantsFrontCamera(null))
        assertTrue(CameraXVideoCapturer.wantsFrontCamera(""))
        assertTrue(CameraXVideoCapturer.wantsFrontCamera("user"))
        assertTrue(CameraXVideoCapturer.wantsFrontCamera("front"))
    }

    @Test
    fun wantsFrontCamera_detectsEnvironmentBackLens() {
        assertFalse(CameraXVideoCapturer.wantsFrontCamera("environment"))
        assertFalse(CameraXVideoCapturer.wantsFrontCamera("ENVIRONMENT"))
        assertFalse(CameraXVideoCapturer.wantsFrontCamera("camera2 environment"))
    }

    @Test
    fun widestFovCaptureDimensions_prefers4x3Over16x9Request() {
        val (width, height) = CameraXVideoCapturer.widestFovCaptureDimensions(1280, 720)
        assertTrue(width == 1280)
        assertTrue(height == 960)
    }

    @Test
    fun widestFovCaptureDimensions_allowsSensorMaxAbove1920() {
        val (width, height) = CameraXVideoCapturer.widestFovCaptureDimensions(3840, 2160)
        assertTrue(width == 3264)
        assertTrue(height == 2448)
    }

    @Test
    fun selectWidestFourThreeSize_prefersLargestFourThree() {
        val chosen = CameraXVideoCapturer.selectWidestFourThreeSize(
            listOf(
                Size(1280, 720),
                Size(1920, 1440),
                Size(2560, 1920),
                Size(3840, 2160),
            ),
        )
        assertEquals(2560, chosen.width)
        assertEquals(1920, chosen.height)
    }

    @Test
    fun selectWidestFourThreeSize_prefersStandardSizeOverMiui1088() {
        val chosen = CameraXVideoCapturer.selectWidestFourThreeSize(
            listOf(
                Size(1440, 1088),
                Size(1920, 1440),
                Size(1280, 720),
            ),
        )
        assertEquals(1920, chosen.width)
        assertEquals(1440, chosen.height)
    }

    @Test
    fun selectWidestFourThreeSize_prefersExactFourThreeOverApproximate() {
        val chosen = CameraXVideoCapturer.selectWidestFourThreeSize(
            listOf(
                Size(1440, 1088),
                Size(1280, 960),
            ),
        )
        assertEquals(1280, chosen.width)
        assertEquals(960, chosen.height)
    }

    @Test
    fun previewZoomRatio_usesNativeOneX() {
        assertEquals(1.0f, CameraXVideoCapturer.previewZoomRatio(false, 0.5f, 4.0f))
        assertEquals(1.0f, CameraXVideoCapturer.previewZoomRatio(true, 0.5f, 4.0f))
        assertEquals(1.0f, CameraXVideoCapturer.previewZoomRatio(true, 1.0f, 1.0f))
    }

  @Test
  fun selectWidestFourThreeSize_usedForFrontAndBack() {
    val sizes = listOf(
      Size(2560, 1920),
      Size(1920, 1080),
      Size(1920, 1440),
    )
    val frontLike = CameraXVideoCapturer.selectWidestFourThreeSize(sizes)
    val backLike = CameraXVideoCapturer.selectWidestFourThreeSize(sizes)
    assertEquals(2560, frontLike.width)
    assertEquals(1920, frontLike.height)
    assertEquals(frontLike, backLike)
  }
}
