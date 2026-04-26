package com.base.aihelperwearos.data.metodi

import android.content.Context
import android.util.Log
import com.base.aihelperwearos.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class MetodiRepository(private val context: Context) {
    companion object {
        private const val TAG = "MetodiRepository"
        private const val THEORY_LIMIT = 4
        private const val CODE_LIMIT = 3
        private const val THEORY_CONTEXT_MAX_CHARS = 5200
        private const val CODE_CONTEXT_MAX_CHARS = 5600
        private val STOPWORDS = setOf(
            "alla", "alle", "allo", "anche", "avere", "come", "con", "cui", "dal", "dalla",
            "delle", "degli", "dell", "del", "dei", "che", "gli", "per", "piu", "puo",
            "sono", "sul", "sulla", "tra", "una", "uno", "nel", "nella", "nelle", "non",
            "cosa", "spiega", "spiegami", "risolvi", "calcola", "codice", "python",
            "the", "and", "for", "with", "from", "this", "that", "are", "not"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val initMutex = Mutex()

    @Volatile
    private var isInitialized = false
    private var theoryDatabase: MetodiTheoryDatabase? = null
    private var codeDatabase: MetodiCodeDatabase? = null

    suspend fun retrieveTheoryContext(query: String): MetodiContextResult = withContext(Dispatchers.IO) {
        ensureInitialized()
        val database = theoryDatabase ?: return@withContext MetodiContextResult(
            context = null,
            matchedIds = emptyList(),
            matchedLabels = emptyList(),
            fallbackReason = "database teoria non disponibile"
        )

        val queryTerms = extractTerms(query)
        val ranked = database.chunks
            .map { chunk -> chunk to scoreTheoryChunk(chunk, queryTerms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(THEORY_LIMIT)

        if (ranked.isEmpty()) {
            return@withContext MetodiContextResult(
                context = null,
                matchedIds = emptyList(),
                matchedLabels = emptyList(),
                fallbackReason = "nessun chunk teoria rilevante"
            )
        }

        val context = ranked.joinToString("\n\n") { chunk ->
            buildString {
                appendLine("[${chunk.id}] ${chunk.title} - pagina ${chunk.page}")
                appendLine(chunk.text)
            }
        }.takeWithMarker(THEORY_CONTEXT_MAX_CHARS)

        MetodiContextResult(
            context = context,
            matchedIds = ranked.map { it.id },
            matchedLabels = ranked.map { "p.${it.page} ${it.title}" }
        )
    }

    suspend fun retrieveCodeContext(query: String): MetodiContextResult = withContext(Dispatchers.IO) {
        ensureInitialized()
        val database = codeDatabase ?: return@withContext MetodiContextResult(
            context = null,
            matchedIds = emptyList(),
            matchedLabels = emptyList(),
            fallbackReason = "database codice non disponibile"
        )

        val queryTerms = extractTerms(query)
        val ranked = database.examples
            .map { example -> example to scoreCodeExample(example, queryTerms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(CODE_LIMIT)

        if (ranked.isEmpty()) {
            return@withContext MetodiContextResult(
                context = null,
                matchedIds = emptyList(),
                matchedLabels = emptyList(),
                fallbackReason = "nessun esempio codice rilevante"
            )
        }

        val context = ranked.mapIndexed { index, example ->
            buildString {
                val priority = if (index == 0) "ESEMPIO PRIORITARIO" else "ESEMPIO SECONDARIO"
                appendLine("[$priority]")
                appendLine("[${example.id}] ${example.category} / ${example.subtype}")
                appendLine("Origine: ${example.sourcePath}")
                appendLine("Titolo: ${example.title}")
                appendLine("```python")
                appendLine(example.code)
                appendLine("```")
            }
        }.joinToString("\n\n").takeWithMarker(CODE_CONTEXT_MAX_CHARS)

        MetodiContextResult(
            context = context,
            matchedIds = ranked.map { it.id },
            matchedLabels = ranked.map { "${it.category} / ${it.subtype}" }
        )
    }

    private suspend fun ensureInitialized() {
        if (isInitialized) return

        initMutex.withLock {
            if (isInitialized) return

            theoryDatabase = loadTheoryDatabase()
            codeDatabase = loadCodeDatabase()
            isInitialized = true

            Log.d(
                TAG,
                "Initialized: theory=${theoryDatabase?.chunks?.size ?: 0}, code=${codeDatabase?.examples?.size ?: 0}"
            )
        }
    }

    private fun loadTheoryDatabase(): MetodiTheoryDatabase? {
        return runCatching {
            context.resources.openRawResource(R.raw.metodi_teoria).use { stream ->
                json.decodeFromString<MetodiTheoryDatabase>(stream.bufferedReader().readText().removeUtf8Bom())
            }
        }.onFailure {
            Log.e(TAG, "Failed to load metodi_teoria", it)
        }.getOrNull()
    }

    private fun loadCodeDatabase(): MetodiCodeDatabase? {
        return runCatching {
            context.resources.openRawResource(R.raw.metodi_codice).use { stream ->
                json.decodeFromString<MetodiCodeDatabase>(stream.bufferedReader().readText().removeUtf8Bom())
            }
        }.onFailure {
            Log.e(TAG, "Failed to load metodi_codice", it)
        }.getOrNull()
    }

    private fun String.removeUtf8Bom(): String {
        return removePrefix("\uFEFF")
    }

    private fun scoreTheoryChunk(chunk: MetodiTheoryChunk, queryTerms: Set<String>): Int {
        if (queryTerms.isEmpty()) return 0
        val keywordSet = chunk.keywords.map { it.lowercase() }.toSet()
        val text = "${chunk.title} ${chunk.text}".lowercase()
        return queryTerms.fold(0) { total, term ->
            total + when {
                term in keywordSet -> 5
                keywordSet.any { it.contains(term) || term.contains(it) } -> 3
                text.contains(term) -> 1
                else -> 0
            }
        }
    }

    private fun scoreCodeExample(example: MetodiCodeExample, queryTerms: Set<String>): Int {
        if (queryTerms.isEmpty()) return 0
        val keywordSet = example.keywords.map { it.lowercase() }.toSet()
        val metadata = "${example.category} ${example.subtype} ${example.title} ${example.sourcePath}".lowercase()
        val code = example.code.lowercase()
        return queryTerms.fold(0) { total, term ->
            total + when {
                term in keywordSet -> 6
                keywordSet.any { it.contains(term) || term.contains(it) } -> 4
                metadata.contains(term) -> 3
                code.contains(term) -> 1
                else -> 0
            }
        }
    }

    private fun extractTerms(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}_]+"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 }
            .filter { it !in STOPWORDS }
            .toSet()
    }

    private fun String.takeWithMarker(maxChars: Int): String {
        return if (length <= maxChars) {
            this
        } else {
            take(maxChars).trimEnd() + "\n[contesto troncato]"
        }
    }
}
