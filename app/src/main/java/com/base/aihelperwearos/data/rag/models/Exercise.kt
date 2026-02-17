package com.base.aihelperwearos.data.rag.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
    @SerialName("id")
    val id: String,
    
    @SerialName("categoria")
    val categoria: String,
    
    @SerialName("sottotipo")
    val sottotipo: String,
    
    @SerialName("keywords")
    val keywords: List<String>,
    
    @SerialName("testo")
    val testo: String,
    
    @SerialName("svolgimento")
    val svolgimento: String
) {
    /**
     * Formats the exercise into a prompt-ready string.
     *
     * @return formatted prompt text as a `String`.
     */
    fun formatForPrompt(): String {
        return buildString {
            appendLine("📝 Esercizio [ID: $id | $categoria - $sottotipo]:")
            appendLine(testo)
            appendLine()
            appendLine("✅ Svolgimento della professoressa:")
            appendLine(svolgimento)
        }
    }
    
    /**
     * Builds a searchable term set from keywords and category labels.
     *
     * @return `Set<String>` of lowercase search terms.
     */
    fun getSearchableTerms(): Set<String> {
        return buildSet {
            addAll(
                keywords.map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
            )
            add(categoria.lowercase())
            add(sottotipo.lowercase())
            addAll(tokenizeLabel(categoria))
            addAll(tokenizeLabel(sottotipo))
        }
    }

    /**
     * Tokenizes labels and removes short/noisy chunks.
     */
    private fun tokenizeLabel(label: String): List<String> {
        return label.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 }
            .distinct()
    }
}

@Serializable
data class ExerciseDatabase(
    @SerialName("version")
    val version: String,
    
    @SerialName("last_updated")
    val lastUpdated: String,
    
    @SerialName("max_exercises")
    val maxExercises: Int = 100,
    
    @SerialName("exercises")
    val exercises: List<Exercise>
)
