package com.base.aihelperwearos.data.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class PhysicsExerciseCorpusTest {

    @Test
    fun `physics corpus parses and contains expected categories`() {
        val corpusFile = listOf(
            File("src/main/res/raw/esercizi_fisica.json"),
            File("app/src/main/res/raw/esercizi_fisica.json")
        ).first { it.exists() }

        val result = ExerciseParser.parseExercises(
            ByteArrayInputStream(corpusFile.readBytes())
        )

        assertTrue(result.isSuccess)
        val exercises = result.getOrThrow().exercises
        assertEquals(32, exercises.size)
        assertTrue(exercises.all { ExerciseParser.validateExercise(it) })
        assertTrue(exercises.any { it.categoria == "Moto parabolico" })
        assertTrue(exercises.any { it.categoria == "Energia" })
        assertTrue(exercises.any { it.categoria == "Urti elastici" })
        assertTrue(exercises.any { it.categoria == "Oscillazioni" })
        assertTrue(exercises.any { it.categoria == "Gravitazione" })
        assertTrue(exercises.any { it.categoria == "Termodinamica" })
        assertTrue(exercises.any { it.categoria == "Calorimetria" })
        assertTrue(exercises.any { it.categoria == "Entropia" })
    }
}
