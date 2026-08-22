package com.lonasharkins.luminahelper.storage

import android.content.Context
import com.lonasharkins.luminahelper.model.InstrumentProfile
import com.lonasharkins.luminahelper.model.MappedKey
import com.lonasharkins.luminahelper.model.ScreenPoint
import org.json.JSONArray
import org.json.JSONObject

class InstrumentProfileRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun loadAll(): List<InstrumentProfile> {
        val encoded = preferences.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    decodeProfile(array.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(profile: InstrumentProfile) {
        require(profile.isCalibrated) { "Somente perfis calibrados podem ser salvos" }

        val updated = buildList {
            add(profile)
            addAll(loadAll().filterNot { it.id == profile.id })
        }
        val array = JSONArray()
        updated.forEach { array.put(encodeProfile(it)) }
        preferences.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun encodeProfile(profile: InstrumentProfile): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("keys", JSONArray().apply {
            profile.keys.forEach { key ->
                put(JSONObject().apply {
                    put("id", key.id)
                    put("label", key.label)
                    put("midiNote", key.midiNote)
                    put("x", key.position?.x ?: JSONObject.NULL)
                    put("y", key.position?.y ?: JSONObject.NULL)
                })
            }
        })
    }

    private fun decodeProfile(json: JSONObject): InstrumentProfile? = runCatching {
        val keysJson = json.getJSONArray("keys")
        val keys = buildList {
            for (index in 0 until keysJson.length()) {
                val keyJson = keysJson.getJSONObject(index)
                val position = if (keyJson.isNull("x") || keyJson.isNull("y")) {
                    null
                } else {
                    ScreenPoint(
                        x = keyJson.getDouble("x").toFloat(),
                        y = keyJson.getDouble("y").toFloat(),
                    )
                }
                add(
                    MappedKey(
                        id = keyJson.getString("id"),
                        label = keyJson.getString("label"),
                        midiNote = keyJson.getInt("midiNote"),
                        position = position,
                    ),
                )
            }
        }

        InstrumentProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            keys = keys,
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "lumina_instrument_profiles"
        const val KEY_PROFILES = "profiles"
    }
}
