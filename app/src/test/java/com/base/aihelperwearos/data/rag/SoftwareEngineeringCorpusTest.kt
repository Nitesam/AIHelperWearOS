package com.base.aihelperwearos.data.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class SoftwareEngineeringCorpusTest {

    @Test
    fun `software engineering corpus parses and is ready for professor material`() {
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
    }
}
