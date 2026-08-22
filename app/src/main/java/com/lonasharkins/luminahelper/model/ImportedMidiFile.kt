package com.lonasharkins.luminahelper.model

import com.lonasharkins.luminahelper.midi.MidiSong
import java.util.UUID

data class ImportedMidiFile(
    val id: String,
    val displayName: String,
    val uri: String,
    val songTitle: String?,
    val format: Int,
    val ticksPerQuarterNote: Int,
    val trackCount: Int,
    val durationMs: Long,
    val noteCount: Int,
    val lowestNote: Int?,
    val highestNote: Int?,
    val tempoChangeCount: Int,
    val instrumentProfileId: String?,
    val importedAtEpochMs: Long,
) {
    companion object {
        fun fromSong(
            displayName: String,
            uri: String,
            song: MidiSong,
            instrumentProfileId: String?,
        ): ImportedMidiFile = ImportedMidiFile(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            uri = uri,
            songTitle = song.title,
            format = song.format,
            ticksPerQuarterNote = song.ticksPerQuarterNote,
            trackCount = song.trackCount,
            durationMs = song.durationMs,
            noteCount = song.notes.size,
            lowestNote = song.lowestNote,
            highestNote = song.highestNote,
            tempoChangeCount = song.tempoChanges.size,
            instrumentProfileId = instrumentProfileId,
            importedAtEpochMs = System.currentTimeMillis(),
        )
    }
}
