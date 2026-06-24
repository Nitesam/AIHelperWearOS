package com.base.aihelperwearos.data.rag.models

sealed class RagResult {
    data class Success(
        val exercises: List<Exercise>,
        val matchedCategory: String?,
        val matchedSubtype: String?,
        val confidence: Float
    ) : RagResult() {
        /**
         * Formats all matched exercises as context text.
         */
        fun formatForPrompt(
            header: String = "ESEMPI RILEVANTI DALLA PROFESSORESSA:",
            solutionLabel: String = "Svolgimento della professoressa",
            itemLabel: String = "Esercizio"
        ): String {
            if (exercises.isEmpty()) return ""
            
            return buildString {
                appendLine()
                appendLine(header)
                appendLine("─".repeat(40))
                exercises.forEachIndexed { index, exercise ->
                    if (index > 0) appendLine()
                    append(exercise.formatForPrompt(solutionLabel = solutionLabel, itemLabel = itemLabel))
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
