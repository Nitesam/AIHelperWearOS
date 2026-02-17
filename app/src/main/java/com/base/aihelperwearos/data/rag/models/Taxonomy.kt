package com.base.aihelperwearos.data.rag.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Sottotipo(
    @SerialName("nome")
    val nome: String,
    
    @SerialName("keywords")
    val keywords: List<String>
) {
    /**
     * Builds searchable terms from the subtype name and keywords.
     *
     * @return `Set<String>` of lowercase search terms.
     */
    fun getSearchableTerms(): Set<String> {
        return buildSet {
            addAll(
                keywords.map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
            )
            add(nome.lowercase())
            addAll(tokenizeLabel(nome))
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
data class Categoria(
    @SerialName("nome")
    val nome: String,
    
    @SerialName("keywords")
    val keywords: List<String>,
    
    @SerialName("sottotipi")
    val sottotipi: List<Sottotipo>
) {
    /**
     * Builds searchable terms from the category and its subtypes.
     *
     * @return `Set<String>` of lowercase search terms.
     */
    fun getSearchableTerms(): Set<String> {
        return buildSet {
            addAll(
                keywords.map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
            )
            add(nome.lowercase())
            addAll(tokenizeLabel(nome))
            sottotipi.forEach { addAll(it.getSearchableTerms()) }
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
data class Taxonomy(
    @SerialName("version")
    val version: String,
    
    @SerialName("last_updated")
    val lastUpdated: String,
    
    @SerialName("categorie")
    val categorie: List<Categoria>
) {
    /**
     * Finds the best matching category for a query string.
     *
     * @param query user query text.
     * @return matching `Categoria?`, or `null` when no match is found.
     */
    fun findBestCategory(query: String): Categoria? {
        val queryTerms = query.lowercase()
            .split(Regex("[\\s,;.!?()\\[\\]{}]+"))
            .filter { it.length > 2 }
            .toSet()
        
        if (queryTerms.isEmpty()) return null
        
        var bestCategory: Categoria? = null
        var bestScore = 0
        
        for (categoria in categorie) {
            val categoryTerms = categoria.getSearchableTerms()
            val matchCount = queryTerms.count { term ->
                categoryTerms.any { it.contains(term) || term.contains(it) }
            }
            
            if (matchCount > bestScore) {
                bestScore = matchCount
                bestCategory = categoria
            }
        }
        
        return bestCategory
    }
    
    /**
     * Finds the best matching subtype within a category.
     *
     * @param query user query text.
     * @param categoria category to search within.
     * @return matching `Sottotipo?`, or `null` when no match is found.
     */
    fun findBestSubtype(query: String, categoria: Categoria): Sottotipo? {
        val queryTerms = query.lowercase()
            .split(Regex("[\\s,;.!?()\\[\\]{}]+"))
            .filter { it.length > 2 }
            .toSet()
        
        if (queryTerms.isEmpty()) return null
        
        var bestSubtype: Sottotipo? = null
        var bestScore = 0
        
        for (sottotipo in categoria.sottotipi) {
            val subtypeTerms = sottotipo.getSearchableTerms()
            val matchCount = queryTerms.count { term ->
                subtypeTerms.any { it.contains(term) || term.contains(it) }
            }
            
            if (matchCount > bestScore) {
                bestScore = matchCount
                bestSubtype = sottotipo
            }
        }
        
        return bestSubtype
    }
}
