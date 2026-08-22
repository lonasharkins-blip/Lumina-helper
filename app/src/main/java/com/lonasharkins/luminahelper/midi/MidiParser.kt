package com.lonasharkins.luminahelper.midi

import java.util.ArrayDeque

object MidiParser {
    const val MAX_FILE_SIZE_BYTES: Int = 8 * 1024 * 1024

    private const val DEFAULT_TEMPO_US = 500_000
    private const val MAX_TRACKS = 256
    private const val MAX_EVENTS = 1_000_000
    private const val MAX_NOTES = 500_000
    private const val MAX_TICK = 10_000_000_000L

    fun parse(bytes: ByteArray): MidiSong {
        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            throw MidiParseException("O arquivo MIDI ultrapassa o limite de 8 MB")
        }

        val reader = MidiReader(bytes)
        if (reader.readAscii(4) != "MThd") {
            throw MidiParseException("O arquivo não possui um cabeçalho MIDI válido")
        }

        val headerLength = reader.readLength("cabeçalho")
        if (headerLength < 6) {
            throw MidiParseException("O cabeçalho MIDI está incompleto")
        }
        val header = reader.readSection(headerLength, "cabeçalho")
        val format = header.readUnsignedShort()
        val trackCount = header.readUnsignedShort()
        val division = header.readUnsignedShort()

        if (format !in 0..1) {
            throw MidiParseException("Somente arquivos MIDI dos formatos 0 e 1 são aceitos")
        }
        if (trackCount !in 1..MAX_TRACKS) {
            throw MidiParseException("A quantidade de faixas MIDI é inválida")
        }
        if (format == 0 && trackCount != 1) {
            throw MidiParseException("Um MIDI de formato 0 precisa ter exatamente uma faixa")
        }
        if (division and 0x8000 != 0) {
            throw MidiParseException("MIDI com divisão de tempo SMPTE ainda não é compatível")
        }
        if (division == 0) {
            throw MidiParseException("A resolução de tempo do MIDI é inválida")
        }

        val rawNotes = mutableListOf<RawNote>()
        val tempoMarkers = mutableListOf<TempoMarker>()
        var title: String? = null
        var durationTicks = 0L
        var totalEvents = 0

        repeat(trackCount) { trackIndex ->
            if (reader.readAscii(4) != "MTrk") {
                throw MidiParseException("A faixa ${trackIndex + 1} não possui um cabeçalho válido")
            }
            val trackLength = reader.readLength("faixa ${trackIndex + 1}")
            val trackReader = reader.readSection(trackLength, "faixa ${trackIndex + 1}")
            val result = parseTrack(trackReader, trackIndex, totalEvents, rawNotes.size)
            totalEvents += result.eventCount
            rawNotes += result.notes
            tempoMarkers += result.tempoMarkers
            if (title == null && !result.title.isNullOrBlank()) title = result.title
            durationTicks = maxOf(durationTicks, result.endTick)
        }

        val tempos = normalizeTempoChanges(tempoMarkers)
        val timeline = TempoTimeline(division, tempos)
        val notes = rawNotes
            .sortedWith(compareBy(RawNote::startTick, RawNote::trackIndex, RawNote::midiNote))
            .map { note ->
                val startUs = timeline.timeAt(note.startTick)
                val endUs = timeline.timeAt(note.endTick)
                MidiNoteEvent(
                    midiNote = note.midiNote,
                    velocity = note.velocity,
                    channel = note.channel,
                    trackIndex = note.trackIndex,
                    startTick = note.startTick,
                    durationTicks = note.endTick - note.startTick,
                    startTimeUs = startUs,
                    durationUs = endUs - startUs,
                )
            }

        return MidiSong(
            title = title,
            format = format,
            ticksPerQuarterNote = division,
            trackCount = trackCount,
            durationTicks = durationTicks,
            durationUs = timeline.timeAt(durationTicks),
            tempoChanges = tempos,
            notes = notes,
        )
    }

    private fun parseTrack(
        reader: MidiReader,
        trackIndex: Int,
        previousEventCount: Int,
        previousNoteCount: Int,
    ): TrackResult {
        val activeNotes = mutableMapOf<NoteKey, ArrayDeque<PendingNote>>()
        val notes = mutableListOf<RawNote>()
        val tempos = mutableListOf<TempoMarker>()
        var absoluteTick = 0L
        var runningStatus: Int? = null
        var eventCount = 0
        var title: String? = null
        var reachedEnd = false

        while (reader.hasRemaining() && !reachedEnd) {
            eventCount++
            if (previousEventCount + eventCount > MAX_EVENTS) {
                throw MidiParseException("O arquivo MIDI possui eventos demais")
            }

            val delta = reader.readVariableLengthQuantity()
            if (absoluteTick > MAX_TICK - delta) {
                throw MidiParseException("A duração em pulsos do MIDI é excessiva")
            }
            absoluteTick += delta

            val statusOrData = reader.readUnsignedByte()
            val status: Int
            val firstData: Int?
            if (statusOrData < 0x80) {
                status = runningStatus
                    ?: throw MidiParseException("Evento MIDI sem status na faixa ${trackIndex + 1}")
                firstData = statusOrData
            } else {
                status = statusOrData
                firstData = null
                runningStatus = if (status in 0x80..0xEF) status else null
            }

            when {
                status in 0x80..0xEF -> {
                    val eventType = status and 0xF0
                    val channel = status and 0x0F
                    val dataLength = if (eventType == 0xC0 || eventType == 0xD0) 1 else 2
                    val data1 = firstData ?: reader.readDataByte()
                    val data2 = if (dataLength == 2) reader.readDataByte() else null

                    if (eventType == 0x90 && data2 != 0) {
                        val key = NoteKey(channel, data1)
                        activeNotes.getOrPut(key) { ArrayDeque() }.addLast(
                            PendingNote(absoluteTick, data2 ?: 0),
                        )
                    } else if (eventType == 0x80 || (eventType == 0x90 && data2 == 0)) {
                        val key = NoteKey(channel, data1)
                        val pending = activeNotes[key]?.pollFirst()
                        if (pending != null && absoluteTick >= pending.startTick) {
                            notes += RawNote(
                                midiNote = data1,
                                velocity = pending.velocity,
                                channel = channel,
                                trackIndex = trackIndex,
                                startTick = pending.startTick,
                                endTick = absoluteTick,
                            )
                            if (previousNoteCount + notes.size > MAX_NOTES) {
                                throw MidiParseException("O arquivo MIDI possui notas demais")
                            }
                        }
                        if (activeNotes[key]?.isEmpty() == true) activeNotes.remove(key)
                    }
                }

                status == 0xFF -> {
                    val metaType = reader.readUnsignedByte()
                    val length = reader.readVariableLengthQuantityAsInt("evento meta")
                    when (metaType) {
                        0x03 -> {
                            val candidate = reader.readText(length).trim().takeIf { it.isNotBlank() }
                            if (title == null) title = candidate
                        }

                        0x51 -> {
                            if (length != 3) {
                                reader.skip(length, "andamento MIDI")
                            } else {
                                val tempo = (reader.readUnsignedByte() shl 16) or
                                    (reader.readUnsignedByte() shl 8) or
                                    reader.readUnsignedByte()
                                if (tempo > 0) {
                                    tempos += TempoMarker(
                                        tick = absoluteTick,
                                        microsecondsPerQuarterNote = tempo,
                                        sourceOrder = (trackIndex.toLong() shl 32) or eventCount.toLong(),
                                    )
                                }
                            }
                        }

                        0x2F -> {
                            reader.skip(length, "fim da faixa")
                            reachedEnd = true
                        }

                        else -> reader.skip(length, "evento meta")
                    }
                }

                status == 0xF0 || status == 0xF7 -> {
                    val length = reader.readVariableLengthQuantityAsInt("evento SysEx")
                    reader.skip(length, "evento SysEx")
                }

                else -> throw MidiParseException(
                    "Evento MIDI 0x${status.toString(16).uppercase()} não reconhecido",
                )
            }
        }

        activeNotes.forEach { (key, pendingQueue) ->
            pendingQueue.forEach { pending ->
                if (absoluteTick > pending.startTick) {
                    notes += RawNote(
                        midiNote = key.midiNote,
                        velocity = pending.velocity,
                        channel = key.channel,
                        trackIndex = trackIndex,
                        startTick = pending.startTick,
                        endTick = absoluteTick,
                    )
                }
            }
        }
        if (previousNoteCount + notes.size > MAX_NOTES) {
            throw MidiParseException("O arquivo MIDI possui notas demais")
        }

        return TrackResult(
            notes = notes,
            tempoMarkers = tempos,
            title = title,
            endTick = absoluteTick,
            eventCount = eventCount,
        )
    }

    private fun normalizeTempoChanges(markers: List<TempoMarker>): List<MidiTempoChange> {
        val temposByTick = linkedMapOf(0L to DEFAULT_TEMPO_US)
        markers
            .sortedWith(compareBy(TempoMarker::tick, TempoMarker::sourceOrder))
            .forEach { marker -> temposByTick[marker.tick] = marker.microsecondsPerQuarterNote }
        return temposByTick.entries
            .sortedBy { it.key }
            .map { MidiTempoChange(it.key, it.value) }
    }

    private class TempoTimeline(
        private val ticksPerQuarterNote: Int,
        tempoChanges: List<MidiTempoChange>,
    ) {
        private val segments: List<TempoSegment> = buildList {
            var currentTick = 0L
            var currentTimeUs = 0L
            var currentTempo = DEFAULT_TEMPO_US

            tempoChanges.forEach { change ->
                if (change.tick > currentTick) {
                    currentTimeUs += tickDurationUs(
                        change.tick - currentTick,
                        currentTempo,
                        ticksPerQuarterNote,
                    )
                    currentTick = change.tick
                }
                currentTempo = change.microsecondsPerQuarterNote
                if (isNotEmpty() && last().tick == currentTick) removeAt(lastIndex)
                add(TempoSegment(currentTick, currentTimeUs, currentTempo))
            }
        }

        fun timeAt(tick: Long): Long {
            var low = 0
            var high = segments.lastIndex
            while (low <= high) {
                val middle = (low + high) ushr 1
                if (segments[middle].tick <= tick) low = middle + 1 else high = middle - 1
            }
            val segment = segments[maxOf(0, high)]
            return segment.startTimeUs + tickDurationUs(
                tick - segment.tick,
                segment.microsecondsPerQuarterNote,
                ticksPerQuarterNote,
            )
        }
    }

    private fun tickDurationUs(ticks: Long, tempo: Int, ticksPerQuarterNote: Int): Long =
        (ticks * tempo.toLong()) / ticksPerQuarterNote.toLong()

    private data class NoteKey(val channel: Int, val midiNote: Int)
    private data class PendingNote(val startTick: Long, val velocity: Int)
    private data class RawNote(
        val midiNote: Int,
        val velocity: Int,
        val channel: Int,
        val trackIndex: Int,
        val startTick: Long,
        val endTick: Long,
    )

    private data class TempoMarker(
        val tick: Long,
        val microsecondsPerQuarterNote: Int,
        val sourceOrder: Long,
    )

    private data class TempoSegment(
        val tick: Long,
        val startTimeUs: Long,
        val microsecondsPerQuarterNote: Int,
    )

    private data class TrackResult(
        val notes: List<RawNote>,
        val tempoMarkers: List<TempoMarker>,
        val title: String?,
        val endTick: Long,
        val eventCount: Int,
    )

    private class MidiReader(
        private val bytes: ByteArray,
        private var position: Int = 0,
        private val limit: Int = bytes.size,
    ) {
        fun hasRemaining(): Boolean = position < limit

        fun readUnsignedByte(): Int {
            ensureAvailable(1, "byte")
            return bytes[position++].toInt() and 0xFF
        }

        fun readDataByte(): Int {
            val value = readUnsignedByte()
            if (value >= 0x80) throw MidiParseException("Byte de dados MIDI inválido")
            return value
        }

        fun readUnsignedShort(): Int = (readUnsignedByte() shl 8) or readUnsignedByte()

        fun readLength(context: String): Int {
            val value = (readUnsignedByte().toLong() shl 24) or
                (readUnsignedByte().toLong() shl 16) or
                (readUnsignedByte().toLong() shl 8) or
                readUnsignedByte().toLong()
            if (value > Int.MAX_VALUE) throw MidiParseException("Tamanho inválido em $context")
            return value.toInt()
        }

        fun readVariableLengthQuantity(): Long {
            var value = 0L
            repeat(4) {
                val next = readUnsignedByte()
                value = (value shl 7) or (next and 0x7F).toLong()
                if (next and 0x80 == 0) return value
            }
            throw MidiParseException("Quantidade variável MIDI inválida")
        }

        fun readVariableLengthQuantityAsInt(context: String): Int {
            val value = readVariableLengthQuantity()
            if (value > Int.MAX_VALUE) throw MidiParseException("Tamanho inválido em $context")
            return value.toInt()
        }

        fun readAscii(length: Int): String = readText(length, Charsets.US_ASCII)

        fun readText(length: Int, charset: java.nio.charset.Charset = Charsets.UTF_8): String {
            ensureAvailable(length, "texto")
            val result = bytes.copyOfRange(position, position + length).toString(charset)
            position += length
            return result
        }

        fun readSection(length: Int, context: String): MidiReader {
            ensureAvailable(length, context)
            val section = MidiReader(bytes, position, position + length)
            position += length
            return section
        }

        fun skip(length: Int, context: String) {
            ensureAvailable(length, context)
            position += length
        }

        private fun ensureAvailable(length: Int, context: String) {
            if (length < 0 || position > limit - length) {
                throw MidiParseException("O arquivo terminou no meio de $context")
            }
        }
    }
}
