package com.base.aihelperwearos.data.rag

import com.base.aihelperwearos.data.rag.models.Exercise
import com.base.aihelperwearos.data.rag.models.ExerciseDatabase
import com.base.aihelperwearos.data.rag.models.Taxonomy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class ExerciseParserTest {

    private val validExerciseJson = """
        {
          "version": "1.0",
          "last_updated": "2026-01-13",
          "max_exercises": 100,
          "exercises": [
            {
              "id": "EDO-001",
              "categoria": "Equazioni Differenziali",
              "sottotipo": "Lineari I Ordine",
              "keywords": ["lineare", "primo ordine"],
              "testo": "Risolvere l'equazione: $$\\frac{dy}{dx} = x$$",
              "svolgimento": "Integro entrambi i lati..."
            },
            {
              "id": "INT-001",
              "categoria": "Integrali",
              "sottotipo": "Integrali Doppi",
              "keywords": ["doppio", "area"],
              "testo": "Calcolare l'integrale...",
              "svolgimento": "Uso coordinate polari..."
            }
          ]
        }
    """.trimIndent()

    private val validTaxonomyJson = """
        {
          "version": "1.0",
          "last_updated": "2026-01-13",
          "categorie": [
            {
              "nome": "Equazioni Differenziali",
              "keywords": ["EDO", "differenziale"],
              "sottotipi": [
                {
                  "nome": "Lineari I Ordine",
                  "keywords": ["primo ordine", "lineare"]
                }
              ]
            },
            {
              "nome": "Integrali",
              "keywords": ["integrale", "primitiva"],
              "sottotipi": [
                {
                  "nome": "Integrali Doppi",
                  "keywords": ["doppio", "area"]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    /**
     * Verifies parsing succeeds for valid exercise JSON.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `parseExercises returns success for valid JSON`() {
        val inputStream = ByteArrayInputStream(validExerciseJson.toByteArray())
        
        val result = ExerciseParser.parseExercises(inputStream)
        
        assertTrue(result.isSuccess)
        val database = result.getOrNull()
        assertNotNull(database)
        assertEquals("1.0", database?.version)
        assertEquals(2, database?.exercises?.size)
    }

    /**
     * Verifies exercise fields are parsed correctly.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `parseExercises extracts exercise fields correctly`() {
        val inputStream = ByteArrayInputStream(validExerciseJson.toByteArray())
        
        val result = ExerciseParser.parseExercises(inputStream)
        val exercise = result.getOrNull()?.exercises?.first()
        
        assertNotNull(exercise)
        assertEquals("EDO-001", exercise?.id)
        assertEquals("Equazioni Differenziali", exercise?.categoria)
        assertEquals("Lineari I Ordine", exercise?.sottotipo)
        assertEquals(2, exercise?.keywords?.size)
        assertTrue(exercise?.testo?.contains("frac{dy}{dx}") == true)
    }

    /**
     * Verifies parsing fails for invalid JSON input.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `parseExercises returns failure for invalid JSON`() {
        val invalidJson = "{ this is not valid json }"
        val inputStream = ByteArrayInputStream(invalidJson.toByteArray())
        
        val result = ExerciseParser.parseExercises(inputStream)
        
        assertTrue(result.isFailure)
    }

    /**
     * Verifies parsing fails for empty input.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `parseExercises returns failure for empty input`() {
        val inputStream = ByteArrayInputStream("".toByteArray())
        
        val result = ExerciseParser.parseExercises(inputStream)
        
        assertTrue(result.isFailure)
    }

    /**
     * Verifies exercise parsing caps the list to the maximum size.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `parseExercises caps exercises at 100`() {
        val manyExercises = (1..150).map { i ->
            """
            {
              "id": "TEST-$i",
              "categoria": "Test",
              "sottotipo": "Test",
              "keywords": ["test"],
              "testo": "Esercizio $i",
              "svolgimento": "Soluzione $i"
            }
            """.trimIndent()
        }.joinToString(",")
        
        val json = """
            {
              "version": "1.0",
              "last_updated": "2026-01-13",
              "max_exercises": 100,
              "exercises": [$manyExercises]
            }
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(json.toByteArray())
        val result = ExerciseParser.parseExercises(inputStream)
        
        assertTrue(result.isSuccess)
        assertEquals(100, result.getOrNull()?.exercises?.size)
    }

    /**
     * Verifies parsing succeeds for valid taxonomy JSON.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `parseTaxonomy returns success for valid JSON`() {
        val inputStream = ByteArrayInputStream(validTaxonomyJson.toByteArray())
        
        val result = ExerciseParser.parseTaxonomy(inputStream)
        
        assertTrue(result.isSuccess)
        val taxonomy = result.getOrNull()
        assertNotNull(taxonomy)
        assertEquals("1.0", taxonomy?.version)
        assertEquals(2, taxonomy?.categorie?.size)
    }

    /**
     * Verifies taxonomy categories are parsed correctly.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `parseTaxonomy extracts categories correctly`() {
        val inputStream = ByteArrayInputStream(validTaxonomyJson.toByteArray())
        
        val result = ExerciseParser.parseTaxonomy(inputStream)
        val category = result.getOrNull()?.categorie?.first()
        
        assertNotNull(category)
        assertEquals("Equazioni Differenziali", category?.nome)
        assertEquals(2, category?.keywords?.size)
        assertEquals(1, category?.sottotipi?.size)
        assertEquals("Lineari I Ordine", category?.sottotipi?.first()?.nome)
    }

    /**
     * Verifies exercise validation succeeds for valid data.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `validateExercise returns true for valid exercise`() {
        val exercise = Exercise(
            id = "TEST-001",
            categoria = "Test",
            sottotipo = "SubTest",
            keywords = listOf("test"),
            testo = "Un esercizio di test",
            svolgimento = "La soluzione"
        )
        
        assertTrue(ExerciseParser.validateExercise(exercise))
    }

    /**
     * Verifies exercise validation fails for empty id.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `validateExercise returns false for empty id`() {
        val exercise = Exercise(
            id = "",
            categoria = "Test",
            sottotipo = "SubTest",
            keywords = listOf("test"),
            testo = "Un esercizio",
            svolgimento = "La soluzione"
        )
        
        assertFalse(ExerciseParser.validateExercise(exercise))
    }

    /**
     * Verifies exercise validation fails for empty text field.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `validateExercise returns false for empty testo`() {
        val exercise = Exercise(
            id = "TEST-001",
            categoria = "Test",
            sottotipo = "SubTest",
            keywords = listOf("test"),
            testo = "",
            svolgimento = "La soluzione"
        )
        
        assertFalse(ExerciseParser.validateExercise(exercise))
    }

    /**
     * Verifies exercise validation fails for empty solution field.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `validateExercise returns false for empty svolgimento`() {
        val exercise = Exercise(
            id = "TEST-001",
            categoria = "Test",
            sottotipo = "SubTest",
            keywords = listOf("test"),
            testo = "Un esercizio",
            svolgimento = "   "
        )
        
        assertFalse(ExerciseParser.validateExercise(exercise))
    }

    /**
     * Verifies searchable terms include expected fields.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Exercise getSearchableTerms includes all relevant terms`() {
        val exercise = Exercise(
            id = "EDO-001",
            categoria = "Equazioni Differenziali",
            sottotipo = "Lineari II Ordine",
            keywords = listOf("secondo ordine", "omogenea"),
            testo = "Test",
            svolgimento = "Test"
        )
        
        val terms = exercise.getSearchableTerms()
        
        assertTrue(terms.contains("secondo ordine"))
        assertTrue(terms.contains("omogenea"))
        assertTrue(terms.contains("equazioni differenziali"))
        assertTrue(terms.contains("lineari ii ordine"))
        assertTrue(terms.contains("equazioni"))
        assertTrue(terms.contains("differenziali"))
    }

    /**
     * Verifies prompt formatting includes required sections.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Exercise formatForPrompt contains required sections`() {
        val exercise = Exercise(
            id = "TEST-001",
            categoria = "Categoria Test",
            sottotipo = "Sottotipo Test",
            keywords = listOf("test"),
            testo = "Il testo dell'esercizio",
            svolgimento = "Lo svolgimento"
        )
        
        val formatted = exercise.formatForPrompt()
        
        assertTrue(formatted.contains("Categoria Test"))
        assertTrue(formatted.contains("Sottotipo Test"))
        assertTrue(formatted.contains("Il testo dell'esercizio"))
        assertTrue(formatted.contains("Lo svolgimento"))
        assertTrue(formatted.contains("📝"))
        assertTrue(formatted.contains("✅"))
    }

    /**
     * Verifies taxonomy category matching finds differential equations.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Taxonomy findBestCategory matches correctly`() {
        val inputStream = ByteArrayInputStream(validTaxonomyJson.toByteArray())
        val taxonomy = ExerciseParser.parseTaxonomy(inputStream).getOrNull()!!
        
        val result = taxonomy.findBestCategory("Risolvere l'equazione differenziale del secondo ordine")
        
        assertNotNull(result)
        assertEquals("Equazioni Differenziali", result?.nome)
    }

    /**
     * Verifies taxonomy category matching finds integrals.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Taxonomy findBestCategory matches integrals`() {
        val inputStream = ByteArrayInputStream(validTaxonomyJson.toByteArray())
        val taxonomy = ExerciseParser.parseTaxonomy(inputStream).getOrNull()!!
        
        val result = taxonomy.findBestCategory("Calcolare l'integrale doppio sull'area")
        
        assertNotNull(result)
        assertEquals("Integrali", result?.nome)
    }

    /**
     * Verifies taxonomy matching can return null for unmatched queries.
     *
     * @return `Unit` after assertions run.
     */
    @Test
    fun `Taxonomy findBestCategory returns null for unmatched query`() {
        val inputStream = ByteArrayInputStream(validTaxonomyJson.toByteArray())
        val taxonomy = ExerciseParser.parseTaxonomy(inputStream).getOrNull()!!
        
        val result = taxonomy.findBestCategory("Come stai oggi?")
        
    }
}
