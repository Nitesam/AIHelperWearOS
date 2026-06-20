package com.base.aihelperwearos.data.rag

import android.util.Log
import com.base.aihelperwearos.data.rag.models.Exercise
import com.base.aihelperwearos.data.rag.models.RagResult

class MathContextRetriever(
    private val ragRepository: RagRepository,
    private val maxExercises: Int = DEFAULT_MAX_EXERCISES,
    private val maxPromptLength: Int = DEFAULT_MAX_PROMPT_LENGTH,
    private val contextHeader: String = "ESEMPI RILEVANTI DALLA PROFESSORESSA:",
    private val solutionLabel: String = "Svolgimento della professoressa"
) {
    
    companion object {
        private const val TAG = "MathContextRetriever"
        private const val DEFAULT_MAX_EXERCISES = 2
        private const val DEFAULT_MAX_PROMPT_LENGTH = 4096
    }
    
    /**
     * Retrieves formatted RAG context for a query.
     *
     * @param query user query text.
     * @return formatted context, or `null` if unavailable.
     */
    suspend fun retrieveContext(query: String): String? {
        if (query.isBlank()) {
            Log.d(TAG, "Empty query, skipping RAG")
            return null
        }

        val result = runCatching { ragRepository.findRelevantExercises(query, maxExercises) }
            .onFailure { Log.w(TAG, "Failed to retrieve RAG context", it) }
            .getOrNull() ?: return null

        return when (result) {
            is RagResult.Success -> {
                if (result.exercises.isEmpty()) {
                    Log.d(TAG, "No exercises in success result")
                    null
                } else {
                    formatContextForPrompt(result).text
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
        if (query.isBlank()) {
            return ContextWithMetadata(
                context = null,
                success = false,
                category = null,
                subtype = null,
                confidence = 0f,
                exerciseCount = 0
            )
        }

        val result = runCatching { ragRepository.findRelevantExercises(query, maxExercises) }
            .onFailure { Log.w(TAG, "Failed to retrieve RAG context metadata", it) }
            .getOrNull() ?: return ContextWithMetadata(
            context = null,
            success = false,
            category = null,
            subtype = null,
            confidence = 0f,
            exerciseCount = 0,
            fallbackReason = "repository unavailable"
        )

        return when (result) {
            is RagResult.Success -> {
                val promptContext = formatContextForPrompt(result)
                ContextWithMetadata(
                    context = promptContext.text,
                    success = true,
                    category = result.matchedCategory,
                    subtype = result.matchedSubtype,
                    confidence = result.confidence,
                    exerciseCount = promptContext.includedExercises.size,
                    sentExerciseIds = promptContext.includedExercises.map { it.id },
                    sentExerciseLabels = promptContext.includedExercises.map {
                        "${it.categoria} / ${it.sottotipo}"
                    }
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
                    sentExerciseIds = emptyList(),
                    sentExerciseLabels = emptyList(),
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
                    sentExerciseIds = emptyList(),
                    sentExerciseLabels = emptyList(),
                    fallbackReason = result.message
                )
            }
        }
    }
    
    /**
     * Formats a success result into context text.
     *
     * @param result successful RAG result to format.
     * @return prompt context `String?`, or `null` if empty.
     */
    private fun formatContextForPrompt(result: RagResult.Success): PromptContext {
        if (result.exercises.isEmpty()) return PromptContext(null, emptyList())
        
        val formatted = result.formatForPrompt(
            header = contextHeader,
            solutionLabel = solutionLabel
        )
        
        return if (formatted.length > maxPromptLength) {
            Log.w(TAG, "Context too long (${formatted.length}), truncating to $maxPromptLength")
            PromptContext(
                text = truncateContext(result.exercises, maxPromptLength),
                includedExercises = result.exercises.take(1)
            )
        } else {
            PromptContext(
                text = formatted,
                includedExercises = result.exercises
            )
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
            appendLine(contextHeader.removeSuffix(":") + ":")
            appendLine("─".repeat(40))
            append(first.formatForPrompt(solutionLabel))
            appendLine("─".repeat(40))
        }
        
        return if (singleFormat.length <= maxLength) {
            singleFormat
        } else {
            buildString {
                appendLine()
                appendLine("ESEMPIO RILEVANTE:")
                appendLine("[${first.categoria}] ${first.testo.take(200)}...")
                appendLine("Svolgimento: ${first.svolgimento.take(500)}...")
            }
        }
    }
    
    /**
     * Reports whether the RAG repository is initialized.
     *
     * @return `Boolean` indicating readiness.
     */
    fun isAvailable(): Boolean = ragRepository.isReady()

    private data class PromptContext(
        val text: String?,
        val includedExercises: List<Exercise>
    )
}

data class ContextWithMetadata(
    val context: String?,
    val success: Boolean,
    val category: String?,
    val subtype: String?,
    val confidence: Float,
    val exerciseCount: Int,
    val sentExerciseIds: List<String> = emptyList(),
    val sentExerciseLabels: List<String> = emptyList(),
    val fallbackReason: String? = null
)
