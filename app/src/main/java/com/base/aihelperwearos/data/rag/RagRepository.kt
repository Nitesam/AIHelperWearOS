package com.base.aihelperwearos.data.rag

import android.content.Context
import android.util.Log
import com.base.aihelperwearos.data.rag.models.ClassificationResult
import com.base.aihelperwearos.data.rag.models.ContentType
import com.base.aihelperwearos.data.rag.models.Exercise
import com.base.aihelperwearos.data.rag.models.ExerciseDatabase
import com.base.aihelperwearos.data.rag.models.RagResult
import com.base.aihelperwearos.data.rag.models.Taxonomy
import com.base.aihelperwearos.data.rag.models.Theorem
import com.base.aihelperwearos.data.rag.models.TheoremDatabase
import com.base.aihelperwearos.data.rag.models.TheoryKeywords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class TheoremResult {
    data class Found(val theorem: Theorem) : TheoremResult() {
        val formattedContent: String get() = theorem.formatForDisplay()
    }
    data class NotFound(val reason: String) : TheoremResult()
}

class RagRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "RagRepository"
        private const val MAX_CACHE_SIZE = 10
        private const val DEFAULT_RESULT_LIMIT = 2
    }
    
    private var exerciseDatabase: ExerciseDatabase? = null
    private var theoremDatabase: TheoremDatabase? = null
    private var taxonomy: Taxonomy? = null
    
    private var exerciseKeywordIndex: Map<String, MutableList<String>> = emptyMap()
    private var theoremKeywordIndex: Map<String, MutableList<String>> = emptyMap()
    
    private val queryCache = object : LinkedHashMap<String, RagResult>(MAX_CACHE_SIZE, 0.75f, true) {
        /**
         * Evicts the eldest cache entry when the maximum size is exceeded.
         *
         * @param eldest current eldest entry in the cache.
         * @return `Boolean` indicating whether the eldest entry should be removed.
         */
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RagResult>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }
    
    private val initMutex = Mutex()
    private val cacheMutex = Mutex()
    
    @Volatile
    private var isInitialized = false
    
    /**
     * Initializes the RAG repository by loading exercises, taxonomy, and theorems.
     *
     * @return `Unit` after initialization completes.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (isInitialized) {
                Log.d(TAG, "Already initialized, skipping")
                return@withContext
            }
            
            try {
                Log.d(TAG, "Starting RAG initialization...")
                val startTime = System.currentTimeMillis()
                
                val taxonomyResult = ExerciseParser.loadTaxonomyFromResources(context)
                if (taxonomyResult.isSuccess) {
                    taxonomy = taxonomyResult.getOrNull()
                    Log.d(TAG, "Taxonomy loaded: ${taxonomy?.categorie?.size ?: 0} categories")
                } else {
                    Log.w(TAG, "Failed to load taxonomy, RAG classification will be limited")
                }
                
                val exercisesResult = ExerciseParser.loadExercisesFromResources(context)
                if (exercisesResult.isSuccess) {
                    exerciseDatabase = exercisesResult.getOrNull()
                    Log.d(TAG, "Exercises loaded: ${exerciseDatabase?.exercises?.size ?: 0} exercises")
                    buildExerciseKeywordIndex()
                } else {
                    Log.w(TAG, "Failed to load exercises")
                }
                
                val theoremsResult = ExerciseParser.loadTheoremsFromResources(context)
                if (theoremsResult.isSuccess) {
                    theoremDatabase = theoremsResult.getOrNull()
                    Log.d(TAG, "Theorems loaded: ${theoremDatabase?.theorems?.size ?: 0} theorems")
                    buildTheoremKeywordIndex()
                } else {
                    Log.w(TAG, "Failed to load theorems")
                }
                
                if (exerciseDatabase == null && theoremDatabase == null) {
                    throw Exception("Failed to load both exercises and theorems")
                }
                
                isInitialized = true
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "RAG initialization complete in ${elapsed}ms")
                
            } catch (e: Exception) {
                Log.e(TAG, "RAG initialization failed", e)
                throw e
            }
        }
    }
    
    /**
     * Builds the exercise keyword index for fast lookup.
     *
     * @return `Unit` after index creation.
     */
    private fun buildExerciseKeywordIndex() {
        val index = mutableMapOf<String, MutableList<String>>()
        
        exerciseDatabase?.exercises?.forEach { exercise ->
            val terms = exercise.getSearchableTerms()
            terms.forEach { term ->
                index.getOrPut(term) { mutableListOf() }.add(exercise.id)
            }
        }
        
        exerciseKeywordIndex = index
        Log.d(TAG, "Exercise keyword index built: ${exerciseKeywordIndex.size} unique terms")
    }
    
    /**
     * Builds the theorem keyword index for fast lookup.
     *
     * @return `Unit` after index creation.
     */
    private fun buildTheoremKeywordIndex() {
        val index = mutableMapOf<String, MutableList<String>>()
        
        theoremDatabase?.theorems?.forEach { theorem ->
            val terms = theorem.getSearchableTerms()
            terms.forEach { term ->
                index.getOrPut(term) { mutableListOf() }.add(theorem.id)
            }
        }
        
        theoremKeywordIndex = index
        Log.d(TAG, "Theorem keyword index built: ${theoremKeywordIndex.size} unique terms")
    }
    
    
    /**
     * Classifies a query as theorem or exercise intent.
     *
     * @param query user query text.
     * @return `ContentType` classification.
     */
    fun classifyQueryType(query: String): ContentType {
        return TheoryKeywords.classifyQuery(query)
    }
    
    
    /**
     * Finds a theorem matching the query text.
     *
     * @param query user query text.
     * @return `TheoremResult` with match information.
     */
    suspend fun findTheorem(query: String): TheoremResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            try { initialize() } catch (e: Exception) {
                return@withContext TheoremResult.NotFound("Sistema non inizializzato")
            }
        }
        
        val database = theoremDatabase ?: return@withContext TheoremResult.NotFound(
            "Database teoremi non disponibile"
        )
        
        try {
            val queryTerms = extractQueryTerms(query)
            val theoremScores = mutableMapOf<String, Int>()
            
            queryTerms.forEach { term ->
                theoremKeywordIndex[term]?.forEach { theoremId ->
                    theoremScores[theoremId] = (theoremScores[theoremId] ?: 0) + 3
                }
                
                theoremKeywordIndex.keys.filter { 
                    it.contains(term) || term.contains(it) 
                }.forEach { key ->
                    theoremKeywordIndex[key]?.forEach { theoremId ->
                        theoremScores[theoremId] = (theoremScores[theoremId] ?: 0) + 1
                    }
                }
            }
            
            if (theoremScores.isEmpty()) {
                return@withContext TheoremResult.NotFound(
                    "Nessun teorema trovato per: ${query.take(50)}"
                )
            }
            
            val bestId = theoremScores.maxByOrNull { it.value }?.key
            val bestTheorem = database.theorems.find { it.id == bestId }
            
            if (bestTheorem != null) {
                Log.d(TAG, "Found theorem: ${bestTheorem.nome} (score: ${theoremScores[bestId]})")
                TheoremResult.Found(bestTheorem)
            } else {
                TheoremResult.NotFound("Teorema non trovato nel database")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding theorem", e)
            TheoremResult.NotFound("Errore: ${e.message}")
        }
    }
    
    
    /**
     * Finds relevant exercises for a query.
     *
     * @param query user query text.
     * @param limit maximum number of exercises to return.
     * @return `RagResult` with matches or fallback info.
     */
    suspend fun findRelevantExercises(
        query: String,
        limit: Int = DEFAULT_RESULT_LIMIT
    ): RagResult = withContext(Dispatchers.IO) {
        
        cacheMutex.withLock {
            queryCache[query]?.let { cached ->
                Log.d(TAG, "Cache hit for query: ${query.take(50)}...")
                return@withContext cached
            }
        }
        
        if (!isInitialized) {
            Log.w(TAG, "RAG not initialized, attempting lazy init")
            try {
                initialize()
            } catch (e: Exception) {
                return@withContext RagResult.Error(
                    message = "Sistema RAG non inizializzato",
                    exception = e
                )
            }
        }
        
        val database = exerciseDatabase ?: return@withContext RagResult.Error(
            message = "Database esercizi non disponibile"
        )
        
        try {
            val classification = classifyQuery(query)
            
            val candidates = findCandidatesByKeywords(query)
            
            val ranked = rankCandidates(candidates, query, classification)
            
            val result = if (ranked.isEmpty()) {
                Log.d(TAG, "No matching exercises found for: ${query.take(50)}...")
                RagResult.NoMatch(
                    reason = "Nessun esercizio simile trovato. Procedo con risoluzione standard."
                )
            } else {
                val topExercises = ranked.take(limit)
                Log.d(TAG, "Found ${topExercises.size} relevant exercises")
                RagResult.Success(
                    exercises = topExercises,
                    matchedCategory = classification.category,
                    matchedSubtype = classification.subtype,
                    confidence = classification.confidence
                )
            }
            
            cacheMutex.withLock {
                queryCache[query] = result
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding relevant exercises", e)
            RagResult.Error(
                message = "Errore durante la ricerca: ${e.message}",
                exception = e
            )
        }
    }
    
    /**
     * Classifies a query into taxonomy category and subtype.
     *
     * @param query user query text.
     * @return `ClassificationResult` with matched metadata.
     */
    private fun classifyQuery(query: String): ClassificationResult {
        val tax = taxonomy ?: return ClassificationResult(
            category = null,
            subtype = null,
            confidence = 0f,
            matchedKeywords = emptyList()
        )
        
        val bestCategory = tax.findBestCategory(query)
        val bestSubtype = bestCategory?.let { tax.findBestSubtype(query, it) }
        
        val queryTerms = extractQueryTerms(query)
        val matchedKeywords = mutableListOf<String>()
        
        if (bestCategory != null) {
            matchedKeywords.addAll(
                bestCategory.keywords.filter { kw ->
                    queryTerms.any { it.contains(kw.lowercase()) || kw.lowercase().contains(it) }
                }
            )
        }
        
        val confidence = when {
            matchedKeywords.size >= 3 -> 0.9f
            matchedKeywords.size == 2 -> 0.7f
            matchedKeywords.size == 1 -> 0.5f
            bestCategory != null -> 0.3f
            else -> 0f
        }
        
        return ClassificationResult(
            category = bestCategory?.nome,
            subtype = bestSubtype?.nome,
            confidence = confidence,
            matchedKeywords = matchedKeywords
        )
    }
    
    /**
     * Finds candidate exercises using the keyword index.
     *
     * @param query user query text.
     * @return `List<Exercise>` of candidate matches.
     */
    private fun findCandidatesByKeywords(query: String): List<Exercise> {
        val queryTerms = extractQueryTerms(query)
        val exerciseScores = mutableMapOf<String, Int>()
        
        queryTerms.forEach { term ->
            exerciseKeywordIndex[term]?.forEach { exerciseId ->
                exerciseScores[exerciseId] = (exerciseScores[exerciseId] ?: 0) + 2
            }
            
            exerciseKeywordIndex.keys.filter { it.contains(term) || term.contains(it) }.forEach { key ->
                exerciseKeywordIndex[key]?.forEach { exerciseId ->
                    exerciseScores[exerciseId] = (exerciseScores[exerciseId] ?: 0) + 1
                }
            }
        }
        
        val exerciseMap = exerciseDatabase?.exercises?.associateBy { it.id } ?: emptyMap()
        
        return exerciseScores.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .mapNotNull { exerciseMap[it.key] }
    }
    
    /**
     * Ranks candidate exercises by relevance to the query and classification.
     *
     * @param candidates candidate exercises to rank.
     * @param query user query text.
     * @param classification taxonomy classification result.
     * @return `List<Exercise>` sorted by relevance.
     */
    private fun rankCandidates(
        candidates: List<Exercise>,
        query: String,
        classification: ClassificationResult
    ): List<Exercise> {
        if (candidates.isEmpty()) return emptyList()
        
        return candidates.sortedByDescending { exercise ->
            var score = 0
            
            if (classification.category != null && 
                exercise.categoria.equals(classification.category, ignoreCase = true)) {
                score += 10
            }
            
            if (classification.subtype != null && 
                exercise.sottotipo.equals(classification.subtype, ignoreCase = true)) {
                score += 5
            }
            
            val exerciseTerms = exercise.getSearchableTerms()
            val queryTerms = extractQueryTerms(query)
            score += queryTerms.count { term ->
                exerciseTerms.any { it.contains(term) || term.contains(it) }
            }
            
            score
        }
    }
    
    /**
     * Extracts searchable terms from a query string.
     *
     * @param query user query text.
     * @return `Set<String>` of normalized search terms.
     */
    private fun extractQueryTerms(query: String): Set<String> {
        return query.lowercase()
            .replace(Regex("[\\$\\\\{}^_]"), " ")
            .split(Regex("[\\s,;.!?()\\[\\]]+"))
            .filter { it.length > 2 }
            .toSet()
    }
    
    /**
     * Clears the in-memory query cache.
     *
     * @return `Unit` after cache eviction.
     */
    fun clearCache() {
        Log.d(TAG, "Clearing RAG cache")
        queryCache.clear()
    }
    
    /**
     * Reports whether the repository has loaded data.
     *
     * @return `Boolean` indicating readiness.
     */
    fun isReady(): Boolean = isInitialized && (exerciseDatabase != null || theoremDatabase != null)
    
    /**
     * Returns statistics about loaded data and cache.
     *
     * @return `Map<String, Any>` with repository metrics.
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "exerciseCount" to (exerciseDatabase?.exercises?.size ?: 0),
            "theoremCount" to (theoremDatabase?.theorems?.size ?: 0),
            "categoryCount" to (taxonomy?.categorie?.size ?: 0),
            "exerciseKeywordIndexSize" to exerciseKeywordIndex.size,
            "theoremKeywordIndexSize" to theoremKeywordIndex.size,
            "cacheSize" to queryCache.size
        )
    }
}
