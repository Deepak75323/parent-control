package com.childmonitor.capture

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalOrientationHelperTest {

    @Test
    fun portraitUpright_mapsToRotation0() {
        assertEquals(Surface.ROTATION_0, PhysicalOrientationHelper.surfaceRotationFromGravity(0f, -9.8f))
    }

    @Test
    fun landscapeHomeOnLeft_mapsToRotation90() {
        assertEquals(Surface.ROTATION_90, PhysicalOrientationHelper.surfaceRotationFromGravity(-9.8f, 0f))
    }

    @Test
    fun landscapeHomeOnRight_mapsToRotation270() {
        assertEquals(Surface.ROTATION_270, PhysicalOrientationHelper.surfaceRotationFromGravity(9.8f, 0f))
    }

    @Test
    fun upsideDown_staysPortraitWhenCommittedPortrait() {
        assertEquals(
            Surface.ROTATION_0,
            PhysicalOrientationHelper.surfaceRotationFromGravity(0f, 9.8f, Surface.ROTATION_0),
        )
    }

    @Test
    fun streamSurfaceFromGravity_locksUpsideDownToPortrait() {
        assertEquals(Surface.ROTATION_0, PhysicalOrientationHelper.streamSurfaceFromGravity(0f, 9.8f))
    }

    @Test
    fun streamSurfaceFromGravity_followsLandscapeLeft() {
        assertEquals(Surface.ROTATION_90, PhysicalOrientationHelper.streamSurfaceFromGravity(-9.8f, 0f))
    }

    @Test
    fun streamSurfaceFromGravity_followsLandscapeRight() {
        assertEquals(Surface.ROTATION_270, PhysicalOrientationHelper.streamSurfaceFromGravity(9.8f, 0f))
    }

    @Test
    fun partialTilt_staysPortraitUntilClearlyHorizontal() {
        // ~70° tilt — past old 60° threshold but not level horizontal
        val angle = 70
        val rad = Math.toRadians(angle.toDouble())
        val x = (-9.8 * kotlin.math.sin(rad)).toFloat()
        val y = (-9.8 * kotlin.math.cos(rad)).toFloat()
        assertEquals(
            Surface.ROTATION_0,
            PhysicalOrientationHelper.surfaceRotationFromGravity(x, y, Surface.ROTATION_0),
        )
    }

    @Test
    fun clearlyPortrait_snapsBackFromLandscape() {
        assertEquals(
            Surface.ROTATION_0,
            PhysicalOrientationHelper.surfaceRotationFromGravity(0f, -9.8f, Surface.ROTATION_270),
        )
    }

    @Test
    fun hysteresis_keepsPortraitUntilPastEnterThreshold() {
        // ~50° tilt — not level horizontal, should stay portrait
        val angle = 50
        val rad = Math.toRadians(angle.toDouble())
        val x = (-9.8 * kotlin.math.sin(rad)).toFloat()
        val y = (-9.8 * kotlin.math.cos(rad)).toFloat()
        assertEquals(
            Surface.ROTATION_0,
            PhysicalOrientationHelper.surfaceRotationFromGravity(x, y, Surface.ROTATION_0),
        )
    }

    @Test
    fun hysteresis_keepsLandscapeUntilReturnedNearUpright() {
        // ~50° tilt — still landscape if already committed landscape-left
        val angle = 50
        val rad = Math.toRadians(angle.toDouble())
        val x = (-9.8 * kotlin.math.sin(rad)).toFloat()
        val y = (-9.8 * kotlin.math.cos(rad)).toFloat()
        assertEquals(
            Surface.ROTATION_90,
            PhysicalOrientationHelper.surfaceRotationFromGravity(x, y, Surface.ROTATION_90),
        )
    }

    @Test
    fun orientationListener_portraitUpright_mapsToRotation0() {
        assertEquals(Surface.ROTATION_0, PhysicalOrientationHelper.surfaceRotationFromOrientation(0))
    }

    @Test
    fun orientationListener_landscapeLeft_mapsToRotation90() {
        assertEquals(Surface.ROTATION_90, PhysicalOrientationHelper.surfaceRotationFromOrientation(90))
    }

    @Test
    fun orientationListener_landscapeRight_mapsToRotation270() {
        assertEquals(Surface.ROTATION_270, PhysicalOrientationHelper.surfaceRotationFromOrientation(270))
    }

    @Test
    fun orientationListener_upsideDown_staysPortrait() {
        assertEquals(Surface.ROTATION_0, PhysicalOrientationHelper.surfaceRotationFromOrientation(180))
    }

    @Test
    fun orientationListener_partialTilt_staysPortraitUntilClearlyHorizontal() {
        assertEquals(
            Surface.ROTATION_0,
            PhysicalOrientationHelper.surfaceRotationFromOrientation(70, Surface.ROTATION_0),
        )
    }
}
