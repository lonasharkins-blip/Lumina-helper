package com.lonasharkins.luminahelper.midi

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiParserTest {
    @Test
    fun parsesFormatZeroNotesTitleAndTiming() {
        val track = ByteArrayOutputStream().apply {
            event(0, 0xFF, 0x03, 0x04)
            write("Demo".toByteArray())
            event(0, 0xFF, 0x51, 0x03, 0x07, 0xA1, 0x20)
            event(0, 0x90, 60, 100)
            event(480, 0x80, 60, 64)
            event(0, 0x90, 64, 90)
            event(240, 0x90, 64, 0)
            event(0, 0xFF, 0x2F, 0)
        }.toByteArray()

        val song = MidiParser.parse(midi(format = 0, division = 480, track))

        assertEquals("Demo", song.title)
        assertEquals(2, song.notes.size)
        assertEquals(750L, song.durationMs)
        assertEquals(60, song.lowestNote)
        assertEquals(64, song.highestNote)
        assertEquals(0L, song.notes[0].startTimeMs)
        assertEquals(500L, song.notes[0].durationMs)
        assertEquals(500L, song.notes[1].startTimeMs)
        assertEquals(250L, song.notes[1].durationMs)
    }

    @Test
    fun supportsRunningStatusAndNoteOnWithZeroVelocity() {
        val track = ByteArrayOutputStream().apply {
            event(0, 0x90, 60, 100)
            event(120, 62, 80)
            event(120, 60, 0)
            event(0, 62, 0)
            event(0, 0xFF, 0x2F, 0)
        }.toByteArray()

        val song = MidiParser.parse(midi(format = 0, division = 480, track))

        assertEquals(2, song.notes.size)
        assertEquals(240L, song.notes[0].durationTicks)
        assertEquals(120L, song.notes[1].startTick)
        assertEquals(125L, song.notes[1].startTimeMs)
        assertEquals(125L, song.notes[1].durationMs)
    }

    @Test
    fun appliesTempoTrackToAllTracksInFormatOne() {
        val tempoTrack = ByteArrayOutputStream().apply {
            event(480, 0xFF, 0x51, 0x03, 0x0F, 0x42, 0x40)
            event(480, 0xFF, 0x2F, 0)
        }.toByteArray()
        val notesTrack = ByteArrayOutputStream().apply {
            event(0, 0x90, 67, 100)
            event(960, 0x80, 67, 0)
            event(0, 0xFF, 0x2F, 0)
        }.toByteArray()

        val song = MidiParser.parse(midi(format = 1, division = 480, tempoTrack, notesTrack))

        assertEquals(2, song.trackCount)
        assertEquals(2, song.tempoChanges.size)
        assertEquals(1_500L, song.durationMs)
        assertEquals(1_500L, song.notes.single().durationMs)
    }

    @Test
    fun rejectsInvalidHeader() {
        val error = assertThrows(MidiParseException::class.java) {
            MidiParser.parse("not midi".toByteArray())
        }

        assertTrue(error.message.orEmpty().contains("cabeçalho"))
    }

    @Test
    fun rejectsSmpteTimeDivisionWithClearMessage() {
        val emptyTrack = ByteArrayOutputStream().apply {
            event(0, 0xFF, 0x2F, 0)
        }.toByteArray()

        val error = assertThrows(MidiParseException::class.java) {
            MidiParser.parse(midi(format = 0, division = 0xE728, emptyTrack))
        }

        assertTrue(error.message.orEmpty().contains("SMPTE"))
    }

    private fun midi(format: Int, division: Int, vararg tracks: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write("MThd".toByteArray())
            int32(6)
            int16(format)
            int16(tracks.size)
            int16(division)
            tracks.forEach { track ->
                write("MTrk".toByteArray())
                int32(track.size)
                write(track)
            }
        }.toByteArray()

    private fun ByteArrayOutputStream.event(delta: Long, vararg data: Int) {
        write(variableLength(delta))
        data.forEach(::write)
    }

    private fun ByteArrayOutputStream.int16(value: Int) {
        write(value ushr 8)
        write(value)
    }

    private fun ByteArrayOutputStream.int32(value: Int) {
        write(value ushr 24)
        write(value ushr 16)
        write(value ushr 8)
        write(value)
    }

    private fun variableLength(value: Long): ByteArray {
        require(value in 0..0x0FFFFFFF)
        var remaining = value shr 7
        var buffer = value and 0x7F
        while (remaining > 0) {
            buffer = (buffer shl 8) or ((remaining and 0x7F) or 0x80)
            remaining = remaining shr 7
        }

        return ByteArrayOutputStream().apply {
            while (true) {
                write((buffer and 0xFF).toInt())
                if (buffer and 0x80 != 0L) buffer = buffer shr 8 else break
            }
        }.toByteArray()
    }
}
