package com.base.aihelperwearos.data.specialized

import android.util.Log
import com.base.aihelperwearos.data.metodi.MetodiRepository
import com.base.aihelperwearos.data.rag.MathContextRetriever

data class ChatContextResult(
    val context: String?,
    val matchedIds: List<String> = emptyList(),
    val matchedLabels: List<String> = emptyList(),
    val fallbackReason: String? = null
)

interface ChatContextTool {
    val logLabel: String
    suspend fun retrieve(query: String): ChatContextResult
}

class ExerciseRagTool(
    private val retriever: MathContextRetriever,
    override val logLabel: String
) : ChatContextTool {
    override suspend fun retrieve(query: String): ChatContextResult {
        val metadata = retriever.retrieveContextWithMetadata(query)
        return ChatContextResult(
            context = metadata.context,
            matchedIds = metadata.sentExerciseIds,
            matchedLabels = metadata.sentExerciseLabels,
            fallbackReason = metadata.fallbackReason
        )
    }
}

class MetodiTheoryTool(
    private val repository: MetodiRepository
) : ChatContextTool {
    override val logLabel: String = "Metodi theory"

    override suspend fun retrieve(query: String): ChatContextResult {
        val result = repository.retrieveTheoryContext(query)
        return ChatContextResult(
            context = result.context,
            matchedIds = result.matchedIds,
            matchedLabels = result.matchedLabels,
            fallbackReason = result.fallbackReason
        )
    }
}

class MetodiCodeTool(
    private val repository: MetodiRepository
) : ChatContextTool {
    override val logLabel: String = "Metodi code"

    override suspend fun retrieve(query: String): ChatContextResult {
        val result = repository.retrieveCodeContext(query)
        return ChatContextResult(
            context = result.context,
            matchedIds = result.matchedIds,
            matchedLabels = result.matchedLabels,
            fallbackReason = result.fallbackReason
        )
    }
}

fun logContextResult(tag: String, tool: ChatContextTool, result: ChatContextResult) {
    val summary = result.matchedIds.zip(result.matchedLabels)
        .joinToString(" | ") { (id, label) -> "$id [$label]" }
    val fallback = result.fallbackReason?.takeIf { it.isNotBlank() } ?: "no match"
    Log.i(tag, "${tool.logLabel} context sent: ${summary.ifBlank { "none ($fallback)" }}")
}
