package com.lonasharkins.luminahelper.music

import com.lonasharkins.luminahelper.model.InstrumentProfile
import com.lonasharkins.luminahelper.model.MappedKey

object KeyLayoutFactory {
    fun centeredChromatic(
        id: String,
        name: String,
        keyCount: Int,
        centerNote: Int = 60,
    ): InstrumentProfile {
        require(keyCount in 1..88) { "A quantidade de teclas precisa estar entre 1 e 88" }
        require(centerNote in 0..127) { "A nota central precisa estar entre 0 e 127" }

        val desiredFirst = centerNote - ((keyCount - 1) / 2)
        val firstNote = desiredFirst.coerceIn(0, 128 - keyCount)
        val keys = List(keyCount) { index ->
            val note = firstNote + index
            MappedKey(
                id = "key-$index",
                label = noteName(note),
                midiNote = note,
            )
        }

        return InstrumentProfile(
            id = id,
            name = name,
            keys = keys,
        )
    }

    fun noteName(note: Int): String {
        require(note in 0..127) { "A nota MIDI precisa estar entre 0 e 127" }
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (note / 12) - 1
        return "${names[note % 12]}$octave"
    }
}

