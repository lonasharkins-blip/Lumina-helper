package com.lonasharkins.luminahelper.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteMapperTest {
    private val profile = KeyLayoutFactory.centeredChromatic(
        id = "small",
        name = "Pequeno",
        keyCount = 5,
        centerNote = 60,
    )
    private val mapper = NoteMapper(profile)

    @Test
    fun foldsDistantNoteIntoAvailableRange() {
        val mapped = mapper.map(sourceNote = 84)

        assertEquals(60, mapped?.midiNote)
    }

    @Test
    fun usesNearestAvailableKey() {
        val mapped = mapper.map(sourceNote = 65, foldOctaves = false)

        assertEquals(62, mapped?.midiNote)
    }

    @Test
    fun removesRepeatedMappedKeysFromChord() {
        val mapped = mapper.mapChord(listOf(84, 72, 60))

        assertEquals(1, mapped.size)
        assertEquals(60, mapped.single().midiNote)
    }

    @Test
    fun rejectsInvalidMidiNote() {
        assertNull(mapper.map(sourceNote = 200))
    }
}

