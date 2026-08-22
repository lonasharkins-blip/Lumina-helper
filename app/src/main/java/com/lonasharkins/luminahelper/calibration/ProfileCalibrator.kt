package com.lonasharkins.luminahelper.calibration

import com.lonasharkins.luminahelper.model.InstrumentProfile
import com.lonasharkins.luminahelper.model.ScreenPoint

object ProfileCalibrator {
    fun applyPositions(
        profile: InstrumentProfile,
        positions: List<ScreenPoint>,
    ): InstrumentProfile {
        require(positions.size == profile.keys.size) {
            "A quantidade de posições precisa ser igual à quantidade de teclas"
        }

        val orderedKeys = profile.keys.sortedBy { it.midiNote }
        val orderedPositions = positions.sortedWith(horizontalPositionComparator)
        return profile.copy(
            keys = orderedKeys.zip(orderedPositions) { key, position ->
                key.copy(position = position)
            },
        )
    }

    fun normalizeHorizontal(profile: InstrumentProfile): InstrumentProfile {
        val positions = profile.keys.mapNotNull { it.position }
        return if (positions.size == profile.keys.size) {
            applyPositions(profile, positions)
        } else {
            profile
        }
    }

    private val horizontalPositionComparator =
        compareBy<ScreenPoint> { it.x }.thenBy { it.y }
}
