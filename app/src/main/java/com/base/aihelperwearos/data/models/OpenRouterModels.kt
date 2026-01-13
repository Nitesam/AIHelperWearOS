package com.base.aihelperwearos.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens") val maxTokens: Int = 2000,
    val temperature: Double = 0.7
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class MultimodalMessage(
    val role: String,
    val content: JsonElement
)

@Serializable
data class TextContent(
    val type: String = "text",
    val text: String
)

@Serializable
data class InputAudioContent(
    val type: String = "input_audio",
    @SerialName("input_audio") val inputAudio: AudioData
)

@Serializable
data class AudioData(
    val data: String,
    val format: String = "wav"
)

@Serializable
data class MultimodalRequest(
    val model: String,
    val messages: List<MultimodalMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 1000,
    val temperature: Double = 0.7
)

@Serializable
data class OpenRouterResponse(
    val id: String,
    val choices: List<Choice>,
    val model: String,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val message: Message,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

data class TranscriptionResult(
    val transcription: String,
    val keywords: List<String>,
    val displayText: String = transcription,
    val isTheoryQuery: Boolean = keywords.firstOrNull() == "teoria" || keywords.firstOrNull() == "theory"
)
