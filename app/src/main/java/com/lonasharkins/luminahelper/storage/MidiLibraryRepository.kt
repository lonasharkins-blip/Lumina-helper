package com.lonasharkins.luminahelper.storage

import android.content.Context
import com.lonasharkins.luminahelper.model.ImportedMidiFile
import org.json.JSONArray
import org.json.JSONObject

class MidiLibraryRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun loadAll(): List<ImportedMidiFile> {
        val encoded = preferences.getString(KEY_FILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    decode(array.getJSONObject(index))?.let(::add)
                }
            }.sortedByDescending(ImportedMidiFile::importedAtEpochMs)
        }.getOrDefault(emptyList())
    }

    fun save(file: ImportedMidiFile) {
        val updated = buildList {
            add(file)
            addAll(loadAll().filterNot { it.uri == file.uri })
        }
        persist(updated)
    }

    fun associateProfile(fileId: String, profileId: String) {
        val updated = loadAll().map { file ->
            if (file.id == fileId) file.copy(instrumentProfileId = profileId) else file
        }
        persist(updated)
    }

    private fun persist(files: List<ImportedMidiFile>) {
        val array = JSONArray()
        files.forEach { array.put(encode(it)) }
        preferences.edit().putString(KEY_FILES, array.toString()).apply()
    }

    private fun encode(file: ImportedMidiFile): JSONObject = JSONObject().apply {
        put("id", file.id)
        put("displayName", file.displayName)
        put("uri", file.uri)
        put("songTitle", file.songTitle ?: JSONObject.NULL)
        put("format", file.format)
        put("ticksPerQuarterNote", file.ticksPerQuarterNote)
        put("trackCount", file.trackCount)
        put("durationMs", file.durationMs)
        put("noteCount", file.noteCount)
        put("lowestNote", file.lowestNote ?: JSONObject.NULL)
        put("highestNote", file.highestNote ?: JSONObject.NULL)
        put("tempoChangeCount", file.tempoChangeCount)
        put("instrumentProfileId", file.instrumentProfileId ?: JSONObject.NULL)
        put("importedAtEpochMs", file.importedAtEpochMs)
    }

    private fun decode(json: JSONObject): ImportedMidiFile? = runCatching {
        ImportedMidiFile(
            id = json.getString("id"),
            displayName = json.getString("displayName"),
            uri = json.getString("uri"),
            songTitle = json.optionalString("songTitle"),
            format = json.getInt("format"),
            ticksPerQuarterNote = json.getInt("ticksPerQuarterNote"),
            trackCount = json.getInt("trackCount"),
            durationMs = json.getLong("durationMs"),
            noteCount = json.getInt("noteCount"),
            lowestNote = json.optionalInt("lowestNote"),
            highestNote = json.optionalInt("highestNote"),
            tempoChangeCount = json.getInt("tempoChangeCount"),
            instrumentProfileId = json.optionalString("instrumentProfileId"),
            importedAtEpochMs = json.getLong("importedAtEpochMs"),
        )
    }.getOrNull()

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.optionalInt(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    private companion object {
        const val PREFERENCES_NAME = "lumina_midi_library"
        const val KEY_FILES = "files"
    }
}
