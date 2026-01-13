package com.base.aihelperwearos.data.rag.models

sealed class RagResult {
    data class Success(
        val exercises: List<Exercise>,
        val matchedCategory: String?,
        val matchedSubtype: String?,
        val confidence: Float
    ) : RagResult() {
        /**
         * Formats all exercises for prompt injection.
         *
         * @return prompt text as a `String`.
         */
        fun formatForPrompt(): String {
            if (exercises.isEmpty()) return ""
            
            return buildString {
                appendLine()
                appendLine("📚 ESEMPI RILEVANTI DALLA PROFESSORESSA:")
                appendLine("─".repeat(40))
                exercises.forEachIndexed { index, exercise ->
                    if (index > 0) appendLine()
                    append(exercise.formatForPrompt())
                }
                appendLine("─".repeat(40))
            }
        }
    }
    
    data class NoMatch(
        val reason: String = "Nessun esercizio simile trovato nel database"
    ) : RagResult()

    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : RagResult()
}

data class ClassificationResult(
    val category: String?,
    val subtype: String?,
    val confidence: Float,
    val matchedKeywords: List<String>
)
