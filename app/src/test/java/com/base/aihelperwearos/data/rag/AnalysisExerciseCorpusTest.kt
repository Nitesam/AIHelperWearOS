package com.base.aihelperwearos.data.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class AnalysisExerciseCorpusTest {

    @Test
    fun `analysis corpus parses and contains generic analysis categories`() {
        val corpusFile = listOf(
            File("src/main/res/raw/esercizi_analisi.json"),
            File("app/src/main/res/raw/esercizi_analisi.json")
        ).first { it.exists() }

        val result = ExerciseParser.parseExercises(
            ByteArrayInputStream(corpusFile.readBytes())
        )

        assertTrue(result.isSuccess)
        val database = result.getOrThrow()
        val exercises = database.exercises

        assertEquals(database.totalExercises, exercises.size)
        assertTrue(exercises.all { ExerciseParser.validateExercise(it) })
        assertEquals(exercises.size, exercises.map { it.id }.distinct().size)
        assertTrue(exercises.any { it.categoria.startsWith("Analisi 1") })
        assertTrue(exercises.any { it.categoria.startsWith("Analisi 2") })
    }
}
