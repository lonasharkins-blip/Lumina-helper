package com.lonasharkins.luminahelper.playback

import com.lonasharkins.luminahelper.midi.MidiSong
import com.lonasharkins.luminahelper.model.InstrumentProfile
import com.lonasharkins.luminahelper.model.ScreenPoint
import com.lonasharkins.luminahelper.music.NoteMapper
import java.util.SortedMap

data class PlaybackTouchEvent(
    val atMs: Long,
    val touchDurationMs: Long,
    val points: List<ScreenPoint>,
)

data class PlaybackPlan(
    val events: List<PlaybackTouchEvent>,
    val durationMs: Long,
    val sourceNoteCount: Int,
)

data class PreparedPlayback(
    val songName: String,
    val profileName: String,
    val speedPercent: Int,
    val transposeSemitones: Int,
    val plan: PlaybackPlan,
)

object PlaybackPlanBuilder {
    const val MIN_SPEED_PERCENT = 25
    const val MAX_SPEED_PERCENT = 300
    const val MIN_TRANSPOSE = -24
    const val MAX_TRANSPOSE = 24

    fun build(
        song: MidiSong,
        profile: InstrumentProfile,
        speedPercent: Int,
        transposeSemitones: Int,
    ): PlaybackPlan {
        require(profile.isCalibrated) { "O perfil precisa estar completamente calibrado" }
        require(speedPercent in MIN_SPEED_PERCENT..MAX_SPEED_PERCENT) {
            "A velocidade precisa estar entre 25% e 300%"
        }
        require(transposeSemitones in MIN_TRANSPOSE..MAX_TRANSPOSE) {
            "A transposição precisa estar entre -24 e +24 semitons"
        }

        val mapper = NoteMapper(profile)
        val pointsByTime: SortedMap<Long, MutableList<ScreenPoint>> = sortedMapOf()
        var mappedNoteCount = 0

        song.notes.forEach { note ->
            val point = mapper.map(
                sourceNote = note.midiNote,
                transpose = transposeSemitones,
                foldOctaves = true,
            )?.position ?: return@forEach
            val atMs = scaledMilliseconds(note.startTimeUs, speedPercent)
            pointsByTime.getOrPut(atMs) { mutableListOf() }.add(point)
            mappedNoteCount++
        }

        val grouped = pointsByTime.entries.map { (atMs, points) ->
            atMs to points.distinct()
        }
        val events = grouped.mapIndexed { index, (atMs, points) ->
            val nextAtMs = grouped.getOrNull(index + 1)?.first
            val duration = if (nextAtMs == null) {
                DEFAULT_TOUCH_DURATION_MS
            } else {
                (nextAtMs - atMs - 1L).coerceIn(1L, DEFAULT_TOUCH_DURATION_MS)
            }
            PlaybackTouchEvent(
                atMs = atMs,
                touchDurationMs = duration,
                points = points,
            )
        }

        return PlaybackPlan(
            events = events,
            durationMs = scaledMilliseconds(song.durationUs, speedPercent),
            sourceNoteCount = mappedNoteCount,
        )
    }

    private fun scaledMilliseconds(timeUs: Long, speedPercent: Int): Long =
        timeUs.coerceAtLeast(0L) / (speedPercent.toLong() * 10L)

    private const val DEFAULT_TOUCH_DURATION_MS = 32L
}
