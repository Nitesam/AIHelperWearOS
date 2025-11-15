package com.base.aihelperwearos.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens") val maxTokens: Int = 1000,
    val temperature: Double = 0.7
)

@Serializable
data class Message(
    val role: String, // "user" o "assistant" o "system"
    val content: String
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

