package com.base.aihelperwearos.data.rag

import android.util.Log
import com.base.aihelperwearos.data.rag.models.Exercise
import com.base.aihelperwearos.data.rag.models.RagResult

class MathContextRetriever(private val ragRepository: RagRepository) {
    
    companion object {
        private const val TAG = "MathContextRetriever"
        private const val MAX_EXERCISES = 2
        private const val MAX_PROMPT_LENGTH = 4096
    }
    
    /**
     * Retrieves formatted RAG context for a query.
     *
     * @param query user query text.
     * @return prompt-ready context `String?`, or `null` if unavailable.
     */
    suspend fun retrieveContext(query: String): String? {
        if (query.isBlank()) {
            Log.d(TAG, "Empty query, skipping RAG")
            return null
        }
        
        if (!ragRepository.isReady()) {
            Log.w(TAG, "RAG repository not ready")
            return null
        }
        
        return when (val result = ragRepository.findRelevantExercises(query, MAX_EXERCISES)) {
            is RagResult.Success -> {
                if (result.exercises.isEmpty()) {
                    Log.d(TAG, "No exercises in success result")
                    null
                } else {
                    formatContextForPrompt(result)
                }
            }
            is RagResult.NoMatch -> {
                Log.d(TAG, "No match: ${result.reason}")
                null
            }
            is RagResult.Error -> {
                Log.e(TAG, "RAG error: ${result.message}", result.exception)
                null
            }
        }
    }
    
    /**
     * Retrieves context along with metadata about the retrieval.
     *
     * @param query user query text.
     * @return `ContextWithMetadata` describing the retrieval result.
     */
    suspend fun retrieveContextWithMetadata(query: String): ContextWithMetadata {
        if (query.isBlank() || !ragRepository.isReady()) {
            return ContextWithMetadata(
                context = null,
                success = false,
                category = null,
                subtype = null,
                confidence = 0f,
                exerciseCount = 0
            )
        }
        
        return when (val result = ragRepository.findRelevantExercises(query, MAX_EXERCISES)) {
            is RagResult.Success -> {
                ContextWithMetadata(
                    context = formatContextForPrompt(result),
                    success = true,
                    category = result.matchedCategory,
                    subtype = result.matchedSubtype,
                    confidence = result.confidence,
                    exerciseCount = result.exercises.size
                )
            }
            is RagResult.NoMatch -> {
                ContextWithMetadata(
                    context = null,
                    success = false,
                    category = null,
                    subtype = null,
                    confidence = 0f,
                    exerciseCount = 0,
                    fallbackReason = result.reason
                )
            }
            is RagResult.Error -> {
                ContextWithMetadata(
                    context = null,
                    success = false,
                    category = null,
                    subtype = null,
                    confidence = 0f,
                    exerciseCount = 0,
                    fallbackReason = result.message
                )
            }
        }
    }
    
    /**
     * Formats a success result into prompt-ready context.
     *
     * @param result successful RAG result to format.
     * @return prompt context `String?`, or `null` if empty.
     */
    private fun formatContextForPrompt(result: RagResult.Success): String? {
        if (result.exercises.isEmpty()) return null
        
        val formatted = result.formatForPrompt()
        
        return if (formatted.length > MAX_PROMPT_LENGTH) {
            Log.w(TAG, "Context too long (${formatted.length}), truncating to $MAX_PROMPT_LENGTH")
            truncateContext(result.exercises, MAX_PROMPT_LENGTH)
        } else {
            formatted
        }
    }
    
    /**
     * Truncates context to fit the maximum length.
     *
     * @param exercises exercises to format.
     * @param maxLength maximum allowed length.
     * @return truncated prompt text as a `String`.
     */
    private fun truncateContext(exercises: List<Exercise>, maxLength: Int): String {
        val first = exercises.firstOrNull() ?: return ""
        val singleFormat = buildString {
            appendLine()
            appendLine("📚 ESEMPIO RILEVANTE DALLA PROFESSORESSA:")
            appendLine("─".repeat(40))
            append(first.formatForPrompt())
            appendLine("─".repeat(40))
        }
        
        return if (singleFormat.length <= maxLength) {
            singleFormat
        } else {
            buildString {
                appendLine()
                appendLine("📚 ESEMPIO RILEVANTE:")
                appendLine("📝 [${first.categoria}] ${first.testo.take(200)}...")
                appendLine("✅ Svolgimento: ${first.svolgimento.take(500)}...")
            }
        }
    }
    
    /**
     * Reports whether the RAG repository is initialized.
     *
     * @return `Boolean` indicating readiness.
     */
    fun isAvailable(): Boolean = ragRepository.isReady()
}

data class ContextWithMetadata(
    val context: String?,
    val success: Boolean,
    val category: String?,
    val subtype: String?,
    val confidence: Float,
    val exerciseCount: Int,
    val fallbackReason: String? = null
)
