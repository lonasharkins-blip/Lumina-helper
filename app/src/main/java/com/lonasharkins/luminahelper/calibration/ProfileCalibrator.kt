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

        return profile.copy(
            keys = profile.keys.zip(positions) { key, position ->
                key.copy(position = position)
            },
        )
    }
}

