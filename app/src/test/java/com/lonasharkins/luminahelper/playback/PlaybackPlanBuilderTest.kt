package com.lonasharkins.luminahelper.playback

import com.lonasharkins.luminahelper.midi.MidiNoteEvent
import com.lonasharkins.luminahelper.midi.MidiSong
import com.lonasharkins.luminahelper.midi.MidiTempoChange
import com.lonasharkins.luminahelper.model.InstrumentProfile
import com.lonasharkins.luminahelper.model.MappedKey
import com.lonasharkins.luminahelper.model.ScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackPlanBuilderTest {
    private val left = ScreenPoint(0.2f, 0.7f)
    private val right = ScreenPoint(0.4f, 0.7f)
    private val profile = InstrumentProfile(
        id = "two-keys",
        name = "Duas teclas",
        keys = listOf(
            MappedKey("c", "C", 60, left),
            MappedKey("d", "D", 62, right),
        ),
    )

    @Test
    fun scalesStartTimeWithPlaybackSpeed() {
        val song = song(note(startTimeUs = 1_000_000L, midiNote = 60))

        val plan = PlaybackPlanBuilder.build(song, profile, 200, 0)

        assertEquals(500L, plan.events.single().atMs)
        assertEquals(1_000L, plan.durationMs)
    }

    @Test
    fun appliesTranspositionBeforeMapping() {
        val song = song(note(startTimeUs = 0L, midiNote = 60))

        val plan = PlaybackPlanBuilder.build(song, profile, 100, 2)

        assertEquals(right, plan.events.single().points.single())
    }

    @Test
    fun groupsChordAndRemovesRepeatedMappedPosition() {
        val song = song(
            note(startTimeUs = 250_000L, midiNote = 60),
            note(startTimeUs = 250_000L, midiNote = 72),
            note(startTimeUs = 250_000L, midiNote = 62),
        )

        val plan = PlaybackPlanBuilder.build(song, profile, 100, 0)

        assertEquals(1, plan.events.size)
        assertEquals(listOf(left, right), plan.events.single().points)
        assertEquals(3, plan.sourceNoteCount)
    }

    @Test
    fun rejectsProfileWithoutCalibratedPositions() {
        val uncalibrated = profile.copy(
            keys = profile.keys.map { it.copy(position = null) },
        )

        assertThrows(IllegalArgumentException::class.java) {
            PlaybackPlanBuilder.build(song(note(0L, 60)), uncalibrated, 100, 0)
        }
    }

    private fun song(vararg notes: MidiNoteEvent): MidiSong = MidiSong(
        title = "Teste",
        format = 0,
        ticksPerQuarterNote = 480,
        trackCount = 1,
        durationTicks = 1_920L,
        durationUs = 2_000_000L,
        tempoChanges = listOf(MidiTempoChange(0L, 500_000)),
        notes = notes.toList(),
    )

    private fun note(startTimeUs: Long, midiNote: Int): MidiNoteEvent = MidiNoteEvent(
        midiNote = midiNote,
        velocity = 100,
        channel = 0,
        trackIndex = 0,
        startTick = 0L,
        durationTicks = 120L,
        startTimeUs = startTimeUs,
        durationUs = 125_000L,
    )
}
