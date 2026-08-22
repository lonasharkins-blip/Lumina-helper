package com.lonasharkins.luminahelper.music

import com.lonasharkins.luminahelper.model.InstrumentProfile
import com.lonasharkins.luminahelper.model.MappedKey
import kotlin.math.abs

class NoteMapper(
    private val profile: InstrumentProfile,
) {
    private val sortedKeys = profile.keys.sortedBy(MappedKey::midiNote)

    fun map(
        sourceNote: Int,
        transpose: Int = 0,
        foldOctaves: Boolean = true,
    ): MappedKey? {
        if (sourceNote !in 0..127) return null

        var target = (sourceNote + transpose).coerceIn(0, 127)
        if (foldOctaves) {
            val minimum = sortedKeys.first().midiNote
            val maximum = sortedKeys.last().midiNote

            while (target < minimum && target + 12 <= 127) target += 12
            while (target > maximum && target - 12 >= 0) target -= 12
        }

        return sortedKeys.minWithOrNull(
            compareBy<MappedKey> { abs(it.midiNote - target) }
                .thenBy(MappedKey::midiNote),
        )
    }

    fun mapChord(
        sourceNotes: Collection<Int>,
        transpose: Int = 0,
        foldOctaves: Boolean = true,
    ): List<MappedKey> = sourceNotes
        .mapNotNull { map(it, transpose, foldOctaves) }
        .distinctBy(MappedKey::id)
}

