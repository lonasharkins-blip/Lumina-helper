package com.lonasharkins.luminahelper.playback

import com.lonasharkins.luminahelper.midi.MidiNoteEvent
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

enum class PlaybackMode(
    val label: String,
) {
    CLEAN_MELODY("Melodia limpa"),
    FULL_ARRANGEMENT("Arranjo sem bateria"),
}

data class PreparedPlayback(
    val songName: String,
    val profileName: String,
    val speedPercent: Int,
    val transposeSemitones: Int,
    val playbackMode: PlaybackMode,
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
        playbackMode: PlaybackMode = PlaybackMode.FULL_ARRANGEMENT,
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

        selectSourceNotes(song.notes, playbackMode).forEach { note ->
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

    private fun selectSourceNotes(
        notes: List<MidiNoteEvent>,
        playbackMode: PlaybackMode,
    ): List<MidiNoteEvent> {
        val withoutPercussion = notes.filterNot { it.channel == PERCUSSION_CHANNEL }
        return when (playbackMode) {
            PlaybackMode.CLEAN_MELODY -> selectCleanMelody(withoutPercussion)
            PlaybackMode.FULL_ARRANGEMENT -> withoutPercussion
        }
    }

    private fun selectCleanMelody(notes: List<MidiNoteEvent>): List<MidiNoteEvent> {
        if (notes.isEmpty()) return emptyList()

        val voices = notes.groupBy { note -> SourceVoice(note.trackIndex, note.channel) }
        val largestVoiceSize = voices.values.maxOf { it.size }
        val minimumCandidateSize = maxOf(
            MIN_MELODY_NOTES,
            (largestVoiceSize + 3) / 4,
        ).coerceAtMost(largestVoiceSize)

        val selectedVoice = voices.values
            .asSequence()
            .filter { voiceNotes -> voiceNotes.size >= minimumCandidateSize }
            .map { voiceNotes ->
                MelodyCandidate(
                    notes = voiceNotes,
                    monophonyScore = monophonyScore(voiceNotes),
                    medianPitch = medianPitch(voiceNotes),
                )
            }
            .maxWithOrNull(
                compareBy<MelodyCandidate> { it.monophonyScore }
                    .thenBy { it.medianPitch }
                    .thenBy { it.notes.size },
            )
            ?.notes
            .orEmpty()

        return selectedVoice
            .groupBy(MidiNoteEvent::startTick)
            .values
            .mapNotNull { simultaneousNotes ->
                simultaneousNotes.maxWithOrNull(
                    compareBy<MidiNoteEvent> { it.midiNote }
                        .thenBy { it.velocity },
                )
            }
            .sortedWith(
                compareBy(MidiNoteEvent::startTimeUs)
                    .thenBy(MidiNoteEvent::startTick)
                    .thenBy(MidiNoteEvent::midiNote),
            )
    }

    private fun monophonyScore(notes: List<MidiNoteEvent>): Int =
        notes.map(MidiNoteEvent::startTick).distinct().size * 1_000 / notes.size

    private fun medianPitch(notes: List<MidiNoteEvent>): Int {
        val pitches = notes.map(MidiNoteEvent::midiNote).sorted()
        return pitches[pitches.size / 2]
    }

    private data class SourceVoice(
        val trackIndex: Int,
        val channel: Int,
    )

    private data class MelodyCandidate(
        val notes: List<MidiNoteEvent>,
        val monophonyScore: Int,
        val medianPitch: Int,
    )

    private const val DEFAULT_TOUCH_DURATION_MS = 32L
    private const val PERCUSSION_CHANNEL = 9
    private const val MIN_MELODY_NOTES = 4
}
