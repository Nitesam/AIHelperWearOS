package com.base.aihelperwearos.data.rag.models

import org.junit.Assert.*
import org.junit.Test

class RagModelsTest {

    /**
     * Verifies exercise searchable terms are lowercased.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Exercise getSearchableTerms returns lowercase terms`() {
        val exercise = Exercise(
            id = "TEST-001",
            categoria = "Equazioni Differenziali",
            sottotipo = "Lineari II Ordine",
            keywords = listOf("SECONDO ORDINE", "Omogenea"),
            testo = "test",
            svolgimento = "test"
        )
        
        val terms = exercise.getSearchableTerms()
        
        terms.forEach { term ->
            assertEquals(term.lowercase(), term)
        }
    }

    /**
     * Verifies compound words are split into searchable terms.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Exercise getSearchableTerms splits compound words`() {
        val exercise = Exercise(
            id = "TEST-001",
            categoria = "Equazioni-Differenziali",
            sottotipo = "Test Sottotipo",
            keywords = listOf("keyword"),
            testo = "test",
            svolgimento = "test"
        )
        
        val terms = exercise.getSearchableTerms()
        
        assertTrue(terms.contains("equazioni"))
        assertTrue(terms.contains("differenziali"))
        assertTrue(terms.contains("test"))
        assertTrue(terms.contains("sottotipo"))
    }

    /**
     * Verifies subtype searchable terms include name and keywords.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Sottotipo getSearchableTerms includes nome and keywords`() {
        val sottotipo = Sottotipo(
            nome = "Lineari II Ordine",
            keywords = listOf("secondo ordine", "caratteristica")
        )
        
        val terms = sottotipo.getSearchableTerms()
        
        assertTrue(terms.contains("lineari ii ordine"))
        assertTrue(terms.contains("secondo ordine"))
        assertTrue(terms.contains("caratteristica"))
        assertTrue(terms.contains("lineari"))
        assertTrue(terms.contains("ii"))
        assertTrue(terms.contains("ordine"))
    }

    /**
     * Verifies category terms include subtype terms.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Categoria getSearchableTerms includes sottotipi terms`() {
        val categoria = Categoria(
            nome = "Equazioni Differenziali",
            keywords = listOf("EDO", "ODE"),
            sottotipi = listOf(
                Sottotipo(nome = "Lineari", keywords = listOf("lineare"))
            )
        )
        
        val terms = categoria.getSearchableTerms()
        
        assertTrue(terms.contains("equazioni differenziali"))
        assertTrue(terms.contains("edo"))
        assertTrue(terms.contains("ode"))
        assertTrue(terms.contains("lineari"))
        assertTrue(terms.contains("lineare"))
    }

    /**
     * Verifies taxonomy returns null for empty query.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Taxonomy findBestCategory returns null for empty query`() {
        val taxonomy = Taxonomy(
            version = "1.0",
            lastUpdated = "2026-01-13",
            categorie = listOf(
                Categoria(
                    nome = "Test",
                    keywords = listOf("test"),
                    sottotipi = emptyList()
                )
            )
        )
        
        val result = taxonomy.findBestCategory("")
        
        assertNull(result)
    }

    /**
     * Verifies taxonomy returns null for short-word queries.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Taxonomy findBestCategory returns null for short words only`() {
        val taxonomy = Taxonomy(
            version = "1.0",
            lastUpdated = "2026-01-13",
            categorie = listOf(
                Categoria(
                    nome = "Test Categoria",
                    keywords = listOf("testword"),
                    sottotipi = emptyList()
                )
            )
        )
        
        val result = taxonomy.findBestCategory("a b c")
        
        assertNull(result)
    }

    /**
     * Verifies category matching uses keyword hits.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Taxonomy findBestCategory matches by keyword`() {
        val taxonomy = Taxonomy(
            version = "1.0",
            lastUpdated = "2026-01-13",
            categorie = listOf(
                Categoria(
                    nome = "Equazioni Differenziali",
                    keywords = listOf("differenziale", "EDO"),
                    sottotipi = emptyList()
                ),
                Categoria(
                    nome = "Integrali",
                    keywords = listOf("integrale", "primitiva"),
                    sottotipi = emptyList()
                )
            )
        )
        
        val result = taxonomy.findBestCategory("Risolvere l'equazione differenziale")
        
        assertNotNull(result)
        assertEquals("Equazioni Differenziali", result?.nome)
    }

    /**
     * Verifies category selection favors most matches.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Taxonomy findBestCategory chooses category with most matches`() {
        val taxonomy = Taxonomy(
            version = "1.0",
            lastUpdated = "2026-01-13",
            categorie = listOf(
                Categoria(
                    nome = "Generica",
                    keywords = listOf("calcolare"),
                    sottotipi = emptyList()
                ),
                Categoria(
                    nome = "Integrali",
                    keywords = listOf("integrale", "calcolare", "doppio"),
                    sottotipi = emptyList()
                )
            )
        )
        
        val result = taxonomy.findBestCategory("Calcolare l'integrale doppio")
        
        assertNotNull(result)
        assertEquals("Integrali", result?.nome)
    }

    /**
     * Verifies subtype matching finds the correct subtype.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Taxonomy findBestSubtype matches correctly`() {
        val categoria = Categoria(
            nome = "Integrali",
            keywords = listOf("integrale"),
            sottotipi = listOf(
                Sottotipo(nome = "Integrali Doppi", keywords = listOf("doppio", "area")),
                Sottotipo(nome = "Integrali Tripli", keywords = listOf("triplo", "volume"))
            )
        )
        
        val taxonomy = Taxonomy(
            version = "1.0",
            lastUpdated = "2026-01-13",
            categorie = listOf(categoria)
        )
        
        val result = taxonomy.findBestSubtype("Calcolare l'integrale doppio sull'area", categoria)
        
        assertNotNull(result)
        assertEquals("Integrali Doppi", result?.nome)
    }

    /**
     * Verifies NoMatch result retains the provided reason.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `RagResult NoMatch contains reason`() {
        val result = RagResult.NoMatch("Motivo del fallback")
        
        assertEquals("Motivo del fallback", result.reason)
    }

    /**
     * Verifies Error result retains message and exception.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `RagResult Error contains message and exception`() {
        val exception = RuntimeException("Test error")
        val result = RagResult.Error("Messaggio errore", exception)
        
        assertEquals("Messaggio errore", result.message)
        assertEquals(exception, result.exception)
    }

    /**
     * Verifies Success formatting is empty for empty exercises.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `RagResult Success with empty exercises formats correctly`() {
        val result = RagResult.Success(
            exercises = emptyList(),
            matchedCategory = null,
            matchedSubtype = null,
            confidence = 0f
        )
        
        assertEquals("", result.formatForPrompt())
    }

    /**
     * Verifies classification result stores all fields.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `ClassificationResult stores all fields`() {
        val result = ClassificationResult(
            category = "Test Category",
            subtype = "Test Subtype",
            confidence = 0.75f,
            matchedKeywords = listOf("keyword1", "keyword2")
        )
        
        assertEquals("Test Category", result.category)
        assertEquals("Test Subtype", result.subtype)
        assertEquals(0.75f, result.confidence, 0.001f)
        assertEquals(2, result.matchedKeywords.size)
    }


    /**
     * Verifies display formatting includes name and statement.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Theorem formatForDisplay includes nome and enunciato`() {
        val theorem = Theorem(
            id = "GAUSS-001",
            nome = "Teorema di Gauss-Green",
            categoria = "Integrali di Superficie",
            tipo = "teorema",
            keywords = listOf("gauss", "green", "divergenza"),
            enunciato = "\\iiint_V \\text{div}\\vec{F}\\,dV = \\iint_{\\partial V} \\vec{F}\\cdot\\vec{n}\\,dS",
            dimostrazione = null,
            note = null
        )
        
        val formatted = theorem.formatForDisplay()
        
        assertTrue(formatted.contains("TEOREMA"))
        assertTrue(formatted.contains("Teorema di Gauss-Green"))
        assertTrue(formatted.contains("\\iiint_V"))
    }

    /**
     * Verifies display formatting includes the proof section.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Theorem formatForDisplay includes dimostrazione when present`() {
        val theorem = Theorem(
            id = "TEST-001",
            nome = "Test Theorem",
            categoria = "Test",
            tipo = "teorema",
            keywords = listOf("test"),
            enunciato = "Enunciato del teorema",
            dimostrazione = "Dimostrazione completa",
            note = null
        )
        
        val formatted = theorem.formatForDisplay()
        
        assertTrue(formatted.contains("Dimostrazione:"))
        assertTrue(formatted.contains("Dimostrazione completa"))
    }

    /**
     * Verifies display formatting includes notes when provided.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Theorem formatForDisplay includes note when present`() {
        val theorem = Theorem(
            id = "TEST-001",
            nome = "Test Theorem",
            categoria = "Test",
            tipo = "definizione",
            keywords = listOf("test"),
            enunciato = "Enunciato",
            dimostrazione = null,
            note = "Nota importante"
        )
        
        val formatted = theorem.formatForDisplay()
        
        assertTrue(formatted.contains("DEFINIZIONE"))
        assertTrue(formatted.contains("Note:"))
        assertTrue(formatted.contains("Nota importante"))
    }

    /**
     * Verifies theorem searchable terms include expected fields.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Theorem getSearchableTerms returns all relevant terms`() {
        val theorem = Theorem(
            id = "STOKES-001",
            nome = "Teorema di Stokes",
            categoria = "Campi Vettoriali",
            tipo = "teorema",
            keywords = listOf("stokes", "rotore", "circuitazione"),
            enunciato = "test",
            dimostrazione = null,
            note = null
        )
        
        val terms = theorem.getSearchableTerms()
        
        assertTrue(terms.contains("stokes"))
        assertTrue(terms.contains("rotore"))
        assertTrue(terms.contains("circuitazione"))
        assertTrue(terms.contains("teorema di stokes"))
        assertTrue(terms.contains("campi vettoriali"))
        assertTrue(terms.contains("teorema"))
    }

    /**
     * Verifies content type enum includes all expected values.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `ContentType enum has correct values`() {
        assertEquals(3, ContentType.values().size)
        assertTrue(ContentType.values().contains(ContentType.EXERCISE))
        assertTrue(ContentType.values().contains(ContentType.THEOREM))
        assertTrue(ContentType.values().contains(ContentType.UNKNOWN))
    }

    /**
     * Verifies theory keyword classification for theorem queries.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `TheoryKeywords classifies theorem queries correctly`() {
        assertEquals(ContentType.THEOREM, TheoryKeywords.classifyQuery("Enuncia il teorema di Gauss"))
        assertEquals(ContentType.THEOREM, TheoryKeywords.classifyQuery("Definizione di convergenza uniforme"))
        assertEquals(ContentType.THEOREM, TheoryKeywords.classifyQuery("Cos'è il teorema di Stokes?"))
        assertEquals(ContentType.THEOREM, TheoryKeywords.classifyQuery("Dimmi cosa dice il lemma di Schwarz"))
    }

    /**
     * Verifies theory keyword classification for exercise queries.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `TheoryKeywords classifies exercise queries correctly`() {
        assertEquals(ContentType.EXERCISE, TheoryKeywords.classifyQuery("Calcola l'integrale doppio"))
        assertEquals(ContentType.EXERCISE, TheoryKeywords.classifyQuery("Risolvi l'equazione differenziale"))
        assertEquals(ContentType.EXERCISE, TheoryKeywords.classifyQuery("Trova il gradiente di f(x,y)"))
        assertEquals(ContentType.EXERCISE, TheoryKeywords.classifyQuery("Determina il valore massimo"))
    }

    /**
     * Verifies ambiguous queries return UNKNOWN classification.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `TheoryKeywords returns UNKNOWN for ambiguous queries`() {
        val result = TheoryKeywords.classifyQuery("Gauss Green superficie")
        assertEquals(ContentType.UNKNOWN, result)
    }

    /**
     * Verifies theorem database stores theorem list entries.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `TheoremDatabase contains theorems list`() {
        val database = TheoremDatabase(
            version = "1.0",
            lastUpdated = "2026-01-13",
            theorems = listOf(
                Theorem(
                    id = "T-001",
                    nome = "Test",
                    categoria = "Test",
                    tipo = "teorema",
                    keywords = listOf("test"),
                    enunciato = "test"
                )
            )
        )
        
        assertEquals(1, database.theorems.size)
        assertEquals("T-001", database.theorems[0].id)
    }
}
