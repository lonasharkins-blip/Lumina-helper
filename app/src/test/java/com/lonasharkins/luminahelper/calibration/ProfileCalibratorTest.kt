package com.lonasharkins.luminahelper.calibration

import com.lonasharkins.luminahelper.model.ScreenPoint
import com.lonasharkins.luminahelper.music.KeyLayoutFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCalibratorTest {
    @Test
    fun ordersPositionsFromLeftToRightRegardlessOfTapOrder() {
        val profile = KeyLayoutFactory.centeredChromatic(
            id = "manual",
            name = "Manual",
            keyCount = 3,
        )
        val positions = listOf(
            ScreenPoint(0.5f, 0.5f),
            ScreenPoint(0.1f, 0.8f),
            ScreenPoint(0.3f, 0.3f),
        )

        val calibrated = ProfileCalibrator.applyPositions(profile, positions)

        assertTrue(calibrated.isCalibrated)
        assertEquals(
            listOf(
                ScreenPoint(0.1f, 0.8f),
                ScreenPoint(0.3f, 0.3f),
                ScreenPoint(0.5f, 0.5f),
            ),
            calibrated.keys.map { it.position },
        )
    }

    @Test
    fun repairsPreviouslySavedOutOfOrderProfile() {
        val baseProfile = KeyLayoutFactory.centeredChromatic(
            id = "saved",
            name = "Salvo",
            keyCount = 3,
        )
        val profile = baseProfile.copy(
            keys = baseProfile.keys.zip(
                listOf(
                    ScreenPoint(0.8f, 0.7f),
                    ScreenPoint(0.2f, 0.7f),
                    ScreenPoint(0.5f, 0.3f),
                ),
            ) { key, position -> key.copy(position = position) },
        )

        val normalized = ProfileCalibrator.normalizeHorizontal(profile)

        assertEquals(
            listOf(0.2f, 0.5f, 0.8f),
            normalized.keys.map { it.position?.x },
        )
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
