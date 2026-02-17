package com.base.aihelperwearos.data.rag

import android.content.Context
import android.util.Log
import com.base.aihelperwearos.R
import com.base.aihelperwearos.data.rag.models.Categoria
import com.base.aihelperwearos.data.rag.models.Exercise
import com.base.aihelperwearos.data.rag.models.ExerciseDatabase
import com.base.aihelperwearos.data.rag.models.Sottotipo
import com.base.aihelperwearos.data.rag.models.Taxonomy
import com.base.aihelperwearos.data.rag.models.Theorem
import com.base.aihelperwearos.data.rag.models.TheoremDatabase
import kotlinx.serialization.json.Json
import java.io.InputStream

object ExerciseParser {
    
    private const val TAG = "ExerciseParser"
    private const val MAX_EXERCISES = 100
    private const val MAX_THEOREMS = 50
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    /**
     * Parses exercises from a JSON input stream with a size cap.
     *
     * @param inputStream input stream containing exercise JSON.
     * @return `Result<ExerciseDatabase>` with parsed exercises or failure.
     */
    fun parseExercises(inputStream: InputStream): Result<ExerciseDatabase> {
        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            val database = json.decodeFromString<ExerciseDatabase>(content)
            
            val cappedDatabase = if (database.exercises.size > MAX_EXERCISES) {
                Log.w(TAG, "Exercise count ${database.exercises.size} exceeds max $MAX_EXERCISES, truncating")
                database.copy(exercises = database.exercises.take(MAX_EXERCISES))
            } else {
                database
            }
            
            Log.d(TAG, "Parsed ${cappedDatabase.exercises.size} exercises (version: ${cappedDatabase.version})")
            Result.success(cappedDatabase)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse exercises JSON", e)
            Result.failure(e)
        }
    }
    
    /**
     * Parses taxonomy from a JSON input stream.
     *
     * @param inputStream input stream containing taxonomy JSON.
     * @return `Result<Taxonomy>` with parsed taxonomy or failure.
     */
    fun parseTaxonomy(inputStream: InputStream): Result<Taxonomy> {
        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            val taxonomy = json.decodeFromString<Taxonomy>(content)
            
            Log.d(TAG, "Parsed taxonomy with ${taxonomy.categorie.size} categories (version: ${taxonomy.version})")
            Result.success(taxonomy)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse taxonomy JSON", e)
            Result.failure(e)
        }
    }
    
    /**
     * Loads exercises from raw resources.
     *
     * @param context context used to access raw resources.
     * @return `Result<ExerciseDatabase>` with parsed exercises or failure.
     */
    fun loadExercisesFromResources(context: Context): Result<ExerciseDatabase> {
        return try {
            context.resources.openRawResource(R.raw.esercizi_analisi2).use { stream ->
                parseExercises(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open exercises resource", e)
            Result.failure(e)
        }
    }
    
    /**
     * Builds a taxonomy from real categories/subtypes present in exercises.
     *
     * Source of truth is `esercizi_analisi2.json`.
     *
     * @param context context used to access raw resources.
     * @return `Result<Taxonomy>` with generated taxonomy or failure.
     */
    fun loadSynchronizedTaxonomyFromResources(context: Context): Result<Taxonomy> {
        val exercisesDb = loadExercisesFromResources(context).getOrNull()

        if (exercisesDb == null) {
            return Result.failure(IllegalStateException("Exercises could not be loaded"))
        }

        val taxonomyFromExercises = synchronizeTaxonomyWithExercises(
            baseTaxonomy = null,
            exercisesDb = exercisesDb
        )
        return Result.success(taxonomyFromExercises)
    }
    
    
    /**
     * Parses theorems from a JSON input stream with a size cap.
     *
     * @param inputStream input stream containing theorem JSON.
     * @return `Result<TheoremDatabase>` with parsed theorems or failure.
     */
    fun parseTheorems(inputStream: InputStream): Result<TheoremDatabase> {
        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            val database = json.decodeFromString<TheoremDatabase>(content)
            
            val cappedDatabase = if (database.theorems.size > MAX_THEOREMS) {
                Log.w(TAG, "Theorem count ${database.theorems.size} exceeds max $MAX_THEOREMS, truncating")
                database.copy(theorems = database.theorems.take(MAX_THEOREMS))
            } else {
                database
            }
            
            Log.d(TAG, "Parsed ${cappedDatabase.theorems.size} theorems (version: ${cappedDatabase.version})")
            Result.success(cappedDatabase)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse theorems JSON", e)
            Result.failure(e)
        }
    }
    
    /**
     * Loads theorems from raw resources.
     *
     * @param context context used to access raw resources.
     * @return `Result<TheoremDatabase>` with parsed theorems or failure.
     */
    fun loadTheoremsFromResources(context: Context): Result<TheoremDatabase> {
        return try {
            context.resources.openRawResource(R.raw.teoremi_analisi2).use { stream ->
                parseTheorems(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open theorems resource", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validates that a theorem has required fields populated.
     *
     * @param theorem theorem to validate.
     * @return `Boolean` indicating validity.
     */
    fun validateTheorem(theorem: Theorem): Boolean {
        return theorem.id.isNotBlank() &&
                theorem.nome.isNotBlank() &&
                theorem.enunciato.isNotBlank()
    }
    
    
    /**
     * Validates that an exercise has required fields populated.
     *
     * @param exercise exercise to validate.
     * @return `Boolean` indicating validity.
     */
    fun validateExercise(exercise: Exercise): Boolean {
        return exercise.id.isNotBlank() &&
                exercise.categoria.isNotBlank() &&
                exercise.testo.isNotBlank() &&
                exercise.svolgimento.isNotBlank()
    }
    
    /**
     * Parses exercises into a lazy sequence for streaming processing.
     *
     * @param inputStream input stream containing exercise JSON.
     * @return `Sequence<Exercise>` of valid exercises.
     */
    fun parseExercisesLazy(inputStream: InputStream): Sequence<Exercise> = sequence {
        val result = parseExercises(inputStream)
        if (result.isSuccess) {
            val database = result.getOrNull()
            database?.exercises?.forEach { exercise ->
                if (validateExercise(exercise)) {
                    yield(exercise)
                }
            }
        }
    }

    /**
     * Merges raw taxonomy with categories/subtypes actually present in exercises.
     */
    private fun synchronizeTaxonomyWithExercises(
        baseTaxonomy: Taxonomy?,
        exercisesDb: ExerciseDatabase
    ): Taxonomy {
        val baseByCategory = baseTaxonomy?.categorie
            ?.associateBy { it.nome.lowercase().trim() }
            ?.toMutableMap()
            ?: mutableMapOf()

        val categoriesFromExercises = exercisesDb.exercises.groupBy { it.categoria }
        val mergedCategories = mutableListOf<Categoria>()
        var missingSubtypeCount = 0
        var missingCategoryCount = 0

        categoriesFromExercises.forEach { (categoryName, exercisesInCategory) ->
            val baseCategory = baseByCategory.remove(categoryName.lowercase().trim())
            if (baseCategory == null) missingCategoryCount++

            val exerciseCategoryKeywords = exercisesInCategory
                .flatMap { it.keywords }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val mergedCategoryKeywords = mergeKeywords(
                baseCategory?.keywords ?: emptyList(),
                exerciseCategoryKeywords + tokenizeLabel(categoryName)
            )

            val baseSubtypeMap = baseCategory?.sottotipi
                ?.associateBy { it.nome.lowercase().trim() }
                ?.toMutableMap()
                ?: mutableMapOf()

            val subtypesFromExercises = exercisesInCategory.groupBy { it.sottotipo }
            val mergedSubtypes = mutableListOf<Sottotipo>()

            subtypesFromExercises.forEach { (subtypeName, exercisesInSubtype) ->
                val baseSubtype = baseSubtypeMap.remove(subtypeName.lowercase().trim())
                if (baseSubtype == null) missingSubtypeCount++

                val exerciseSubtypeKeywords = exercisesInSubtype
                    .flatMap { it.keywords }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                val mergedSubtypeKeywords = mergeKeywords(
                    baseSubtype?.keywords ?: emptyList(),
                    exerciseSubtypeKeywords + tokenizeLabel(subtypeName)
                )

                mergedSubtypes += Sottotipo(
                    nome = subtypeName,
                    keywords = mergedSubtypeKeywords
                )
            }

            if (baseSubtypeMap.isNotEmpty()) {
                mergedSubtypes += baseSubtypeMap.values
            }

            mergedCategories += Categoria(
                nome = categoryName,
                keywords = mergedCategoryKeywords,
                sottotipi = mergedSubtypes
            )
        }

        if (baseByCategory.isNotEmpty()) {
            mergedCategories += baseByCategory.values
        }

        val effectiveVersion = baseTaxonomy?.version ?: exercisesDb.version
        val effectiveLastUpdated = exercisesDb.lastUpdated

        Log.i(
            TAG,
            "Synchronized taxonomy ready: ${mergedCategories.size} categories, " +
                "$missingCategoryCount missing categories added, $missingSubtypeCount missing subtypes added"
        )

        return Taxonomy(
            version = effectiveVersion,
            lastUpdated = effectiveLastUpdated,
            categorie = mergedCategories
        )
    }

    /**
     * Merges keyword lists preserving order and removing blanks/duplicates.
     */
    private fun mergeKeywords(primary: List<String>, secondary: List<String>): List<String> {
        val merged = LinkedHashSet<String>()
        primary.forEach { keyword ->
            val cleaned = keyword.trim()
            if (cleaned.isNotBlank()) merged.add(cleaned)
        }
        secondary.forEach { keyword ->
            val cleaned = keyword.trim()
            if (cleaned.isNotBlank()) merged.add(cleaned)
        }
        return merged.toList()
    }

    /**
     * Splits a label into keyword tokens useful for matching.
     */
    private fun tokenizeLabel(label: String): List<String> {
        return label
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 }
            .distinct()
    }
}
