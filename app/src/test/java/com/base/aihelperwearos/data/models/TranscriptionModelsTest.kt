package com.base.aihelperwearos.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionModelsTest {

    @Test
    fun `default transcription model is selectable`() {
        assertTrue(TranscriptionModels.options.any { it.id == TranscriptionModels.DEFAULT_ID })
    }

    @Test
    fun `unknown transcription model falls back to default`() {
        assertEquals(
            TranscriptionModels.DEFAULT_ID,
            TranscriptionModels.normalizeId("missing-model")
        )
    }
}
