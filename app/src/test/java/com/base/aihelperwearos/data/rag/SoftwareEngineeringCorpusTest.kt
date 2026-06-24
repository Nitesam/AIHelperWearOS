package com.base.aihelperwearos.data.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class SoftwareEngineeringCorpusTest {

    @Test
    fun `software engineering corpus parses and covers professor material`() {
        val corpusFile = listOf(
            File("src/main/res/raw/ingegneria_software.json"),
            File("app/src/main/res/raw/ingegneria_software.json")
        ).first { it.exists() }

        val result = ExerciseParser.parseExercises(
            ByteArrayInputStream(corpusFile.readBytes())
        )

        assertTrue(result.isSuccess)
        val database = result.getOrThrow()
        val entries = database.exercises

        assertEquals(database.totalExercises, entries.size)
        assertTrue(entries.all { ExerciseParser.validateExercise(it) })
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue("Expected rich professor corpus", entries.size >= 90)

        val categories = entries.groupBy { it.categoria }
        assertTrue(categories.containsKey("Ingegneria Software - Appunti e quiz"))
        assertTrue(categories.containsKey("Ingegneria Software - Esercizi Stream"))
        assertTrue(categories.containsKey("Ingegneria Software - Design Pattern"))
        assertTrue(categories.containsKey("Ingegneria Software - Esempi Java Pattern"))
        assertTrue(categories.containsKey("Ingegneria Software - Testing esempi"))
        assertTrue((categories["Ingegneria Software - Esercizi Stream"]?.size ?: 0) >= 40)

        val patternSubtypes = entries
            .filter { it.categoria == "Ingegneria Software - Design Pattern" }
            .map { it.sottotipo }
            .toSet()
        val expectedPatterns = setOf(
            "Iterator",
            "Factory Method",
            "Abstract Factory",
            "Prototype",
            "Adapter",
            "Facade",
            "Template Method",
            "Strategy",
            "State",
            "Composite",
            "Visitor",
            "Decorator",
            "Observer",
            "Chain of Responsibility",
            "Command",
            "Mediator",
            "Singleton",
            "Null Object"
        )
        assertTrue(patternSubtypes.containsAll(expectedPatterns))

        val searchable = entries.flatMap { it.getSearchableTerms() }.toSet()
        assertTrue(searchable.contains("groupingby"))
        assertTrue(searchable.contains("mockito"))
        assertTrue(searchable.contains("mcdc"))
    }
}
