package com.lonasharkins.luminahelper.midi

data class MidiTempoChange(
    val tick: Long,
    val microsecondsPerQuarterNote: Int,
)

data class MidiNoteEvent(
    val midiNote: Int,
    val velocity: Int,
    val channel: Int,
    val trackIndex: Int,
    val startTick: Long,
    val durationTicks: Long,
    val startTimeUs: Long,
    val durationUs: Long,
) {
    val startTimeMs: Long
        get() = startTimeUs / 1_000L

    val durationMs: Long
        get() = durationUs / 1_000L
}

data class MidiSong(
    val title: String?,
    val format: Int,
    val ticksPerQuarterNote: Int,
    val trackCount: Int,
    val durationTicks: Long,
    val durationUs: Long,
    val tempoChanges: List<MidiTempoChange>,
    val notes: List<MidiNoteEvent>,
) {
    val durationMs: Long
        get() = durationUs / 1_000L

    val lowestNote: Int?
        get() = notes.minOfOrNull(MidiNoteEvent::midiNote)

    val highestNote: Int?
        get() = notes.maxOfOrNull(MidiNoteEvent::midiNote)
}

class MidiParseException(message: String) : IllegalArgumentException(message)
