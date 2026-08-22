package com.lonasharkins.luminahelper.calibration

import com.lonasharkins.luminahelper.model.ScreenPoint
import com.lonasharkins.luminahelper.music.KeyLayoutFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCalibratorTest {
    @Test
    fun appliesPositionsInKeyOrder() {
        val profile = KeyLayoutFactory.centeredChromatic(
            id = "manual",
            name = "Manual",
            keyCount = 3,
        )
        val positions = listOf(
            ScreenPoint(0.1f, 0.5f),
            ScreenPoint(0.3f, 0.5f),
            ScreenPoint(0.5f, 0.5f),
        )

        val calibrated = ProfileCalibrator.applyPositions(profile, positions)

        assertTrue(calibrated.isCalibrated)
        assertEquals(positions, calibrated.keys.map { it.position })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteCalibration() {
        val profile = KeyLayoutFactory.centeredChromatic(
            id = "manual",
            name = "Manual",
            keyCount = 3,
        )

        ProfileCalibrator.applyPositions(
            profile = profile,
            positions = listOf(ScreenPoint(0.1f, 0.5f)),
        )
    }
}

