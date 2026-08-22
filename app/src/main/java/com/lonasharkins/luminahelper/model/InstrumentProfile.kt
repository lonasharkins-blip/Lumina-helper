package com.lonasharkins.luminahelper.model

data class ScreenPoint(
    val x: Float,
    val y: Float,
) {
    init {
        require(x in 0f..1f) { "x precisa estar entre 0 e 1" }
        require(y in 0f..1f) { "y precisa estar entre 0 e 1" }
    }
}

data class MappedKey(
    val id: String,
    val label: String,
    val midiNote: Int,
    val position: ScreenPoint? = null,
) {
    init {
        require(id.isNotBlank()) { "A tecla precisa de um identificador" }
        require(midiNote in 0..127) { "A nota MIDI precisa estar entre 0 e 127" }
    }
}

data class InstrumentProfile(
    val id: String,
    val name: String,
    val keys: List<MappedKey>,
) {
    init {
        require(id.isNotBlank()) { "O perfil precisa de um identificador" }
        require(name.isNotBlank()) { "O perfil precisa de um nome" }
        require(keys.isNotEmpty()) { "O instrumento precisa ter pelo menos uma tecla" }
        require(keys.map(MappedKey::id).distinct().size == keys.size) {
            "As teclas do perfil precisam ter identificadores diferentes"
        }
    }

    val isCalibrated: Boolean
        get() = keys.all { it.position != null }
}

