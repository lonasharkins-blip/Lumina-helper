package com.lonasharkins.luminahelper.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyLayoutFactoryTest {
    @Test
    fun createsSmallCustomInstrument() {
        val profile = KeyLayoutFactory.centeredChromatic(
            id = "jjs-emoji-piano",
            name = "Piano de emoji",
            keyCount = 5,
        )

        assertEquals(5, profile.keys.size)
        assertFalse(profile.isCalibrated)
    }

    @Test
    fun createsTwentyOneKeyInstrument() {
        val profile = KeyLayoutFactory.centeredChromatic(
            id = "large-piano",
            name = "Piano de 21 teclas",
            keyCount = 21,
        )

        assertEquals(21, profile.keys.size)
        assertEquals(21, profile.keys.map { it.midiNote }.distinct().size)
    }
}

