package com.base.aihelperwearos.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class ChatMode {
    GENERAL,
    ANALYSIS,
    METODI_TEORIA,
    METODI_CODICE
}

fun ChatMode.isMetodi(): Boolean {
    return this == ChatMode.METODI_TEORIA || this == ChatMode.METODI_CODICE
}
