package com.base.aihelperwearos.data.rag

import com.base.aihelperwearos.data.rag.models.Exercise
import com.base.aihelperwearos.data.rag.models.RagResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

class MathContextRetrieverTest {

    private lateinit var mockRagRepository: RagRepository
    private lateinit var retriever: MathContextRetriever

    private val sampleExercise = Exercise(
        id = "EDO-001",
        categoria = "Equazioni Differenziali",
        sottotipo = "Lineari I Ordine",
        keywords = listOf("lineare", "primo ordine"),
        testo = "Risolvere l'equazione: $$\\frac{dy}{dx} = x$$",
        svolgimento = "1. Integro: $$y = \\frac{x^2}{2} + C$$"
    )

    /**
     * Initializes mocks and the retriever before each test.
     *
     * @return `Unit` after setup completes.
     */
    @Before
    fun setup() {
        mockRagRepository = mock(RagRepository::class.java)
        retriever = MathContextRetriever(mockRagRepository)
    }

    /**
     * Verifies blank queries return null context.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `retrieveContext returns null for blank query`() = runBlocking {
        val result = retriever.retrieveContext("")
        
        assertNull(result)
    }

    /**
     * Verifies whitespace queries return null context.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `retrieveContext returns null for whitespace query`() = runBlocking {
        val result = retriever.retrieveContext("   ")
        
        assertNull(result)
    }

    /**
     * Verifies context is null when the repository is not ready.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `retrieveContext returns null when repository not ready`() = runBlocking {
        whenever(mockRagRepository.isReady()).thenReturn(false)
        
        val result = retriever.retrieveContext("una query valida")
        
        assertNull(result)
    }

    /**
     * Verifies successful retrieval returns formatted context.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `retrieveContext returns formatted context on success`() = runBlocking {
        whenever(mockRagRepository.isReady()).thenReturn(true)
        whenever(mockRagRepository.findRelevantExercises("equazione differenziale", 2))
            .thenReturn(RagResult.Success(
                exercises = listOf(sampleExercise),
                matchedCategory = "Equazioni Differenziali",
                matchedSubtype = "Lineari I Ordine",
                confidence = 0.8f
            ))
        
        val result = retriever.retrieveContext("equazione differenziale")
        
        assertNotNull(result)
        assertTrue(result!!.contains("ESEMPI"))
        assertTrue(result.contains("EDO-001") || result.contains("Equazioni Differenziali"))
    }

    /**
     * Verifies NoMatch results yield null context.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `retrieveContext returns null on NoMatch`() = runBlocking {
        whenever(mockRagRepository.isReady()).thenReturn(true)
        whenever(mockRagRepository.findRelevantExercises("query senza match", 2))
            .thenReturn(RagResult.NoMatch("Nessun esercizio trovato"))
        
        val result = retriever.retrieveContext("query senza match")
        
        assertNull(result)
    }

    /**
     * Verifies Error results yield null context.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `retrieveContext returns null on Error`() = runBlocking {
        whenever(mockRagRepository.isReady()).thenReturn(true)
        whenever(mockRagRepository.findRelevantExercises("query con errore", 2))
            .thenReturn(RagResult.Error("Errore di test", Exception("Test")))
        
        val result = retriever.retrieveContext("query con errore")
        
        assertNull(result)
    }

    /**
     * Verifies metadata is populated on success.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `retrieveContextWithMetadata returns success metadata correctly`() = runBlocking {
        whenever(mockRagRepository.isReady()).thenReturn(true)
        whenever(mockRagRepository.findRelevantExercises("equazione differenziale", 2))
            .thenReturn(RagResult.Success(
                exercises = listOf(sampleExercise),
                matchedCategory = "Equazioni Differenziali",
                matchedSubtype = "Lineari I Ordine",
                confidence = 0.85f
            ))
        
        val result = retriever.retrieveContextWithMetadata("equazione differenziale")
        
        assertTrue(result.success)
        assertEquals("Equazioni Differenziali", result.category)
        assertEquals("Lineari I Ordine", result.subtype)
        assertEquals(0.85f, result.confidence, 0.01f)
        assertEquals(1, result.exerciseCount)
        assertNotNull(result.context)
    }

    /**
     * Verifies metadata indicates failure on NoMatch.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `retrieveContextWithMetadata returns failure metadata on NoMatch`() = runBlocking {
        whenever(mockRagRepository.isReady()).thenReturn(true)
        whenever(mockRagRepository.findRelevantExercises("no match", 2))
            .thenReturn(RagResult.NoMatch("Nessun esercizio"))
        
        val result = retriever.retrieveContextWithMetadata("no match")
        
        assertFalse(result.success)
        assertNull(result.context)
        assertEquals(0, result.exerciseCount)
        assertEquals(0f, result.confidence, 0.01f)
        assertNotNull(result.fallbackReason)
    }

    /**
     * Verifies availability reflects repository readiness.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `isAvailable returns repository ready state`() {
        whenever(mockRagRepository.isReady()).thenReturn(true)
        assertTrue(retriever.isAvailable())
        
        whenever(mockRagRepository.isReady()).thenReturn(false)
        assertFalse(retriever.isAvailable())
    }

    /**
     * Verifies prompt formatting includes all exercises.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `RagResult Success formatForPrompt includes all exercises`() {
        val exercises = listOf(
            sampleExercise,
            sampleExercise.copy(id = "EDO-002", sottotipo = "Lineari II Ordine")
        )
        
        val result = RagResult.Success(
            exercises = exercises,
            matchedCategory = "Equazioni Differenziali",
            matchedSubtype = null,
            confidence = 0.7f
        )
        
        val formatted = result.formatForPrompt()
        
        assertTrue(formatted.contains("📚"))
        assertTrue(formatted.contains("ESEMPI"))
        assertTrue(formatted.contains("Lineari I Ordine") || formatted.contains("EDO-001"))
    }

    /**
     * Verifies prompt formatting returns empty for empty exercise list.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `RagResult Success formatForPrompt returns empty for empty list`() {
        val result = RagResult.Success(
            exercises = emptyList(),
            matchedCategory = null,
            matchedSubtype = null,
            confidence = 0f
        )
        
        val formatted = result.formatForPrompt()
        
        assertEquals("", formatted)
    }
}
