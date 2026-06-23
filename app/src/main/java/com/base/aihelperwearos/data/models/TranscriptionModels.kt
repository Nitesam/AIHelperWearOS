package com.base.aihelperwearos.data.models

data class TranscriptionModelOption(
    val id: String,
    val displayName: String
)

object TranscriptionModels {
    const val DEFAULT_ID = "google/gemini-3-flash-preview"

    val options = listOf(
        TranscriptionModelOption(
            id = DEFAULT_ID,
            displayName = "Gemini 3 Flash Preview"
        ),
        TranscriptionModelOption(
            id = "google/gemini-3.1-flash-lite",
            displayName = "Gemini 3.1 Flash Lite"
        ),
        TranscriptionModelOption(
            id = "google/gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash"
        ),
        TranscriptionModelOption(
            id = "google/gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash"
        ),
        TranscriptionModelOption(
            id = "google/gemini-2.0-flash-001",
            displayName = "Gemini 2.0 Flash"
        )
    )

    fun normalizeId(modelId: String?): String {
        return options.firstOrNull { it.id == modelId }?.id ?: DEFAULT_ID
    }
}
