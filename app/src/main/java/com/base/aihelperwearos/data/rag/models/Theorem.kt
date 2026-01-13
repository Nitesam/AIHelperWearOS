package com.base.aihelperwearos.data.rag.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ContentType {
    EXERCISE,
    
    THEOREM,
    
    UNKNOWN
}

@Serializable
data class Theorem(
    @SerialName("id")
    val id: String,
    
    @SerialName("nome")
    val nome: String,
    
    @SerialName("categoria")
    val categoria: String,
    
    @SerialName("tipo")
    val tipo: String,
    
    @SerialName("keywords")
    val keywords: List<String>,
    
    @SerialName("enunciato")
    val enunciato: String,
    
    @SerialName("dimostrazione")
    val dimostrazione: String? = null,
    
    @SerialName("note")
    val note: String? = null
) {
    /**
     * Formats the theorem for direct display in the UI.
     *
     * @return formatted display text as a `String`.
     */
    fun formatForDisplay(): String {
        return buildString {
            appendLine("**${tipo.uppercase()}: $nome**")
            appendLine()
            appendLine(enunciato)
            
            if (!dimostrazione.isNullOrBlank()) {
                appendLine()
                appendLine("**Dimostrazione:**")
                appendLine(dimostrazione)
            }
            
            if (!note.isNullOrBlank()) {
                appendLine()
                appendLine("**Note:**")
                appendLine(note)
            }
        }
    }
    
    /**
     * Formats the theorem for inclusion in RAG prompt context.
     *
     * @return prompt-ready text as a `String`.
     */
    fun formatForPrompt(): String {
        return buildString {
            appendLine("📖 $tipo: $nome")
            appendLine(enunciato)
            if (!note.isNullOrBlank()) {
                appendLine("💡 $note")
            }
        }
    }
    
    /**
     * Builds searchable terms from theorem metadata.
     *
     * @return `Set<String>` of lowercase search terms.
     */
    fun getSearchableTerms(): Set<String> {
        return buildSet {
            addAll(keywords.map { it.lowercase() })
            add(nome.lowercase())
            add(categoria.lowercase())
            add(tipo.lowercase())
            addAll(nome.lowercase().split(" ", "-", "'"))
        }
    }
}

@Serializable
data class TheoremDatabase(
    @SerialName("version")
    val version: String,
    
    @SerialName("last_updated")
    val lastUpdated: String,
    
    @SerialName("theorems")
    val theorems: List<Theorem>
)

object TheoryKeywords {
    val THEOREM_INDICATORS = setOf(
        "teorema", "theorem",
        "enuncia", "enunciare", "enunciato",
        "dimostra", "dimostrare", "dimostrazione", 
        "definizione", "definisci", "define", "definition",
        "corollario", "corollary",
        "lemma",
        "cos'è", "cosa è", "what is",
        "spiega", "spiegami", "explain",
        "formula", "formule"
    )
    
    val EXERCISE_INDICATORS = setOf(
        "risolvi", "risolvere", "solve",
        "calcola", "calcolare", "calculate", "compute",
        "trova", "trovare", "find",
        "determina", "determinare", "determine",
        "verifica", "verificare", "verify",
        "dimostra che", "prove that",
        "integrale", "derivata",
        "limite", "equazione"
    )
    
    /**
     * Classifies a query as theorem, exercise, or unknown.
     *
     * @param query user query text.
     * @return `ContentType` classification result.
     */
    fun classifyQuery(query: String): ContentType {
        val lowerQuery = query.lowercase()
        
        val hasTheoremIndicator = THEOREM_INDICATORS.any { indicator ->
            lowerQuery.contains(indicator)
        }
        
        val hasExerciseIndicator = EXERCISE_INDICATORS.any { indicator ->
            lowerQuery.contains(indicator)
        }
        
        if (lowerQuery.contains("dimostra che") || lowerQuery.contains("prove that")) {
            return ContentType.EXERCISE
        }
        
        return when {
            hasTheoremIndicator && !hasExerciseIndicator -> ContentType.THEOREM
            hasExerciseIndicator && !hasTheoremIndicator -> ContentType.EXERCISE
            hasTheoremIndicator && hasExerciseIndicator -> {
                val firstTheorem = THEOREM_INDICATORS.mapNotNull { 
                    val idx = lowerQuery.indexOf(it)
                    if (idx >= 0) idx else null 
                }.minOrNull() ?: Int.MAX_VALUE
                
                val firstExercise = EXERCISE_INDICATORS.mapNotNull { 
                    val idx = lowerQuery.indexOf(it)
                    if (idx >= 0) idx else null 
                }.minOrNull() ?: Int.MAX_VALUE
                
                if (firstTheorem < firstExercise) ContentType.THEOREM else ContentType.EXERCISE
            }
            else -> ContentType.UNKNOWN
        }
    }
}
